package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneRevision;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.MilestonesAsKnownAt;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.MilestonesAsKnownAt.Value;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.TicketStateAt;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.TicketStateAt.Span;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.MilestoneRevisionRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAssetDirectory;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Consulta comun de los informes de incidencias (FR-072 a FR-077, FR-086). Con un unico corte lee los tickets creados
 * hasta ese instante y reconstruye sus hitos tal como se conocian en el con {@link MilestonesAsKnownAt}: asi el informe,
 * el listado con periodo y la reconstruccion de un informe ya emitido usan las mismas reglas de dominio y no hay calculos
 * en SQL que las dupliquen. Debe llamarse dentro de una transaccion de solo lectura; los lotes de episodios y revisiones
 * se piden con una consulta cada uno, sin N+1.
 */
@Service
@PreAuthorize(IncidentPermissions.READERS)
public class IncidentReportQuery {

	/** Tope del rango de un informe, como los demas modulos (FR-072). */
	public static final int MAX_PERIOD_DAYS = 366;

	/**
	 * Lo que se pide: sin periodo es «Todo el historial» (FR-072). {@code from} y {@code to} son dias inclusivos; el
	 * servidor los lleva a {@code [S, E)} con el fin exclusivo (FR-074). El estado y la red son filtros opcionales sobre
	 * el estado al final del periodo y la red conservada en T0.
	 */
	public record Selection(LocalDate from, LocalDate to, TicketState state, String network) {

		public Selection {
			if ((from == null) != (to == null)) {
				throw new BusinessRuleException("PERIOD_INVALID", "El período necesita el primer y el último día");
			}
			if (from != null) {
				if (to.isBefore(from)) {
					throw new BusinessRuleException("PERIOD_INVALID", "El último día no puede ser anterior al primero");
				}
				if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
					throw new BusinessRuleException("PERIOD_TOO_LONG",
							"El período admite hasta " + MAX_PERIOD_DAYS + " días");
				}
			}
		}

		public boolean allHistory() {
			return from == null;
		}
	}

	/** Un episodio con sus hitos conocidos al corte; la base de los instantes y de la huella. */
	public record EpisodeFacts(int ordinal, Instant t0, Instant t4) {
	}

	/** Una fila del informe de control, con el estado al final del periodo y lo necesario para su huella. */
	public record ControlRow(UUID ticketId, int number, String code, ElementClass elementClass, String elementLabel,
			String network, TicketState stateAtEnd, List<EpisodeFacts> episodes) {
	}

	/**
	 * El informe de control de un corte: las filas en orden de numero, el rango efectivo y los totales. {@code start} y
	 * {@code endExclusive} son nulos sin periodo; {@code stateInstant} es el instante en que se evaluo el estado.
	 */
	public record ControlData(Instant cutOff, Selection selection, Instant start, Instant endExclusive,
			Instant stateInstant, List<ControlRow> rows) {

		public long open() {
			return rows.stream().filter(row -> row.stateAtEnd() == TicketState.OPEN).count();
		}

		public long closed() {
			return rows.size() - open();
		}
	}

	private final TicketRepository tickets;
	private final MilestoneRevisionRepository revisions;
	private final IncidentAssetDirectory assets;
	private final OperatingZone zone;

	IncidentReportQuery(TicketRepository tickets, MilestoneRevisionRepository revisions, IncidentAssetDirectory assets,
			OperatingZone zone) {
		this.tickets = tickets;
		this.revisions = revisions;
		this.assets = assets;
		this.zone = zone;
	}

	/** Las filas del informe de control para la seleccion, con lo que se sabia en {@code cutOff}. */
	public ControlData control(Selection selection, Instant cutOff) {
		Instant start = selection.allHistory() ? null : zone.startOf(selection.from());
		Instant endExclusive = selection.allHistory() ? null : zone.startOf(selection.to().plusDays(1));
		Instant stateInstant = selection.allHistory() ? cutOff.plusNanos(1)
				: TicketStateAt.effectiveEnd(endExclusive, cutOff);

		List<Ticket> created = tickets.findCreatedUntil(cutOff);
		Map<UUID, List<MilestoneRevision>> byEpisode = revisionsOf(created);
		List<Ticket> included = new ArrayList<>();
		Map<UUID, List<EpisodeFacts>> facts = new java.util.HashMap<>();
		for (Ticket ticket : created) {
			List<EpisodeFacts> episodes = episodesKnownAt(ticket, byEpisode, cutOff);
			if (episodes.isEmpty()) {
				continue;
			}
			List<Span> spans = episodes.stream().map(episode -> new Span(episode.t0(), episode.t4())).toList();
			boolean inPeriod = selection.allHistory() || TicketStateAt.includedIn(spans, start, stateInstant);
			if (!inPeriod) {
				continue;
			}
			TicketState state = TicketStateAt.at(spans, stateInstant);
			if (selection.state() != null && selection.state() != state) {
				continue;
			}
			if (selection.network() != null && !selection.network().equals(ticket.getNetworkAtT0())) {
				continue;
			}
			included.add(ticket);
			facts.put(ticket.getId(), episodes);
		}
		Map<UUID, IncidentAsset> currentAssets = assetsOf(included);
		List<ControlRow> rows = included.stream().map(ticket -> {
			List<EpisodeFacts> episodes = facts.get(ticket.getId());
			List<Span> spans = episodes.stream().map(episode -> new Span(episode.t0(), episode.t4())).toList();
			return new ControlRow(ticket.getId(), ticket.getTicketNumber(), new TicketCode(ticket.getTicketNumber()).text(),
					ticket.getElementClass(), ElementLabelComposer.compose(ticket, currentAssets), ticket.getNetworkAtT0(),
					TicketStateAt.at(spans, stateInstant), episodes);
		}).toList();
		return new ControlData(cutOff, selection, start, endExclusive, stateInstant, rows);
	}

	/** Los episodios que ya existian en el corte, con T0 y T4 tal como se conocian entonces (FR-074a). */
	static List<EpisodeFacts> episodesKnownAt(Ticket ticket, Map<UUID, List<MilestoneRevision>> byEpisode,
			Instant cutOff) {
		List<EpisodeFacts> result = new ArrayList<>();
		for (Episode episode : ticket.getEpisodes()) {
			if (episode.getCreatedAt() != null && episode.getCreatedAt().isAfter(cutOff)) {
				continue;
			}
			Map<MilestoneKind, Value> known = MilestonesAsKnownAt.of(episode,
					byEpisode.getOrDefault(episode.getId(), List.of()), cutOff);
			Instant t0 = recordedTime(known.get(MilestoneKind.T0));
			if (t0 == null) {
				continue;
			}
			result.add(new EpisodeFacts(episode.getOrdinal(), t0, recordedTime(known.get(MilestoneKind.T4))));
		}
		return result;
	}

	private static Instant recordedTime(Value value) {
		return value != null && value.status() == MilestoneStatus.RECORDED ? value.occurredAt() : null;
	}

	Map<UUID, List<MilestoneRevision>> revisionsOf(Collection<Ticket> ofTickets) {
		Set<UUID> episodeIds = new HashSet<>();
		ofTickets.forEach(ticket -> ticket.getEpisodes().forEach(episode -> episodeIds.add(episode.getId())));
		return revisions.findByEpisodes(episodeIds).stream()
				.collect(Collectors.groupingBy(MilestoneRevision::getEpisodeId));
	}

	Map<UUID, IncidentAsset> assetsOf(Collection<Ticket> ofTickets) {
		Set<UUID> ids = new HashSet<>();
		for (Ticket ticket : ofTickets) {
			if (ticket.getEquipmentId() != null) {
				ids.add(ticket.getEquipmentId());
			}
			if (ticket.getReferenceEquipmentId() != null) {
				ids.add(ticket.getReferenceEquipmentId());
			}
		}
		return assets.findByIds(ids);
	}

	/**
	 * La huella de las filas (FR-086): SHA-256 de un CSV canonico con identificadores, estados e instantes, sin nombres
	 * visibles, que pueden cambiar despues (D12). Reproducirla con {@link #control} al mismo corte demuestra que una
	 * correccion posterior no cambio lo que el informe mostro.
	 */
	public static String rowsSha256(List<ControlRow> rows) {
		StringBuilder csv = new StringBuilder("ticketId,number,elementClass,network,stateAtEnd,episodes\n");
		for (ControlRow row : rows) {
			csv.append(row.ticketId()).append(',').append(row.number()).append(',').append(row.elementClass()).append(',')
					.append(row.network() == null ? "" : row.network()).append(',').append(row.stateAtEnd()).append(',')
					.append(row.episodes().stream()
							.map(episode -> episode.ordinal() + ":" + episode.t0() + ":"
									+ (episode.t4() == null ? "" : episode.t4().toString()))
							.collect(Collectors.joining(";")))
					.append('\n');
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(csv.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}
}
