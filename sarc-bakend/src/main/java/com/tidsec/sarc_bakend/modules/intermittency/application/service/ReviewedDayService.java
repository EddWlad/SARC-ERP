package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.ReviewedDayView;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.ReviewedDayView.Marked;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.ReviewedDayView.UnconfirmedBatch;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage.ReviewedDay;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage.ReviewedDayEvent;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.IntermittencyImportRepository;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.ReviewedDayRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Marca de dias revisados para la red KIA (FR-044): al cerrar su turno, un editor marca un dia ya terminado como
 * revisado; esa marca acredita la cobertura de la disponibilidad estimada. Retirarla exige justificacion. Cada
 * accion deja su evento y su auditoria.
 */
@Service
@PreAuthorize(IntermittencyPermissions.EDITORS)
public class ReviewedDayService {

	/** Igual que el dashboard: un rango admite hasta 366 dias. */
	static final int MAX_DAYS = 366;

	/** Sin rango, los ultimos 31 dias, hoy incluido: lo que un analista revisa al cerrar su turno. */
	private static final int DEFAULT_DAYS = 31;

	private final ReviewedDayRepository reviewedDays;

	private final IntermittencyImportRepository imports;

	private final AuditTrail audit;

	private final CurrentActor actor;

	private final Clock clock;

	private final OperatingZone zone;

	ReviewedDayService(ReviewedDayRepository reviewedDays, IntermittencyImportRepository imports, AuditTrail audit,
			CurrentActor actor, Clock clock, OperatingZone zone) {
		this.reviewedDays = reviewedDays;
		this.imports = imports;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
		this.zone = zone;
	}

	/**
	 * Un registro por cada dia de {@code [from, to)}, con o sin marca. Sin rango: los ultimos 31 dias, hoy incluido.
	 */
	@Transactional(readOnly = true)
	public List<ReviewedDayView> list(LocalDate from, LocalDate to) {
		LocalDate today = zone.dateOf(Instant.now(clock));
		LocalDate first = from == null ? today.minusDays(DEFAULT_DAYS - 1L) : from;
		LocalDate end = to == null ? today.plusDays(1) : to;
		if (!end.isAfter(first)) {
			throw new BusinessRuleException("PERIOD_INVALID", "El rango debe tener al menos un dia: el fin es exclusivo");
		}
		if (ChronoUnit.DAYS.between(first, end) > MAX_DAYS) {
			throw new BusinessRuleException("PERIOD_TOO_LONG", "El rango admite hasta " + MAX_DAYS + " dias");
		}
		Map<LocalDate, ReviewedDay> marked = new HashMap<>();
		reviewedDays.findInRange(first, end).forEach(day -> marked.put(day.getDay(), day));
		Map<LocalDate, Integer> pending = pendingLinkRows(first, end.minusDays(1));
		Map<LocalDate, Integer> unconfirmed = imports.countUnconfirmedBatchesByDay(first, end.minusDays(1));
		List<ReviewedDayView> days = new ArrayList<>();
		for (LocalDate day = first; day.isBefore(end); day = day.plusDays(1)) {
			days.add(view(day, marked.get(day), today, pending.getOrDefault(day, 0), unconfirmed.getOrDefault(day, 0)));
		}
		return days;
	}

	/**
	 * Marca el dia como revisado para toda la red. Un dia que no termino se rechaza (FR-044). Marcar un dia ya
	 * marcado no cambia nada. Responde ademas los lotes sin confirmar que tocan el dia, porque todavia no
	 * afectan la cobertura (FR-045).
	 */
	@Transactional
	public Marked mark(LocalDate day) {
		Instant now = Instant.now(clock);
		LocalDate today = zone.dateOf(now);
		UUID actorId = actor.id().orElseThrow();
		reviewedDays.lockDay(day);
		List<UnconfirmedBatch> warnings = imports.findUnconfirmedBatchesTouching(day).stream()
				.map(batch -> new UnconfirmedBatch(batch.getId(), batch.getFileName())).toList();

		Optional<ReviewedDay> existing = reviewedDays.find(day);
		ReviewedDay saved;
		boolean changed;
		if (existing.isEmpty()) {
			saved = reviewedDays.save(ReviewedDay.mark(day, today, actorId, now));
			changed = true;
		}
		else {
			ReviewedDay current = existing.get();
			boolean wasActive = current.isActive();
			current.remark(today, actorId, now);
			changed = !wasActive;
			saved = changed ? reviewedDays.save(current) : current;
		}
		if (changed) {
			reviewedDays.saveEvent(ReviewedDayEvent.marked(day, actorId, now, warnings.size()));
			audit.record("INTERMITTENCY_DAY_MARKED", ReviewedDay.AUDIT_ENTITY_TYPE, entityId(day),
					Map.of("day", day.toString(), "unconfirmedBatches", String.valueOf(warnings.size())));
		}
		return new Marked(view(day, saved, today, pendingLinkRows(day, day).getOrDefault(day, 0), warnings.size()),
				warnings);
	}

	/** Retira la marca con una justificacion obligatoria; el dia vuelve a quedar sin datos. */
	@Transactional
	public ReviewedDayView withdraw(LocalDate day, long version, String justification) {
		Instant now = Instant.now(clock);
		LocalDate today = zone.dateOf(now);
		UUID actorId = actor.id().orElseThrow();
		reviewedDays.lockDay(day);
		ReviewedDay current = reviewedDays.find(day).orElseThrow(
				() -> new NotFoundException("REVIEWED_DAY_NOT_FOUND", "El dia no esta marcado como revisado"));
		current.requireVersion(version);
		current.withdraw(justification);
		ReviewedDay saved = reviewedDays.save(current);
		reviewedDays.saveEvent(ReviewedDayEvent.withdrawn(day, justification, actorId, now));
		audit.record("INTERMITTENCY_DAY_WITHDRAWN", ReviewedDay.AUDIT_ENTITY_TYPE, entityId(day),
				Map.of("day", day.toString()));
		return view(day, saved, today, pendingLinkRows(day, day).getOrDefault(day, 0),
				imports.countUnconfirmedBatchesByDay(day, day).getOrDefault(day, 0));
	}

	/**
	 * Filas de lotes confirmados que dejan sin cubrir cada dia de {@code [from, to]} para toda la red: las
	 * por vincular y las en conflicto sin punto (la misma regla que usa la disponibilidad).
	 */
	private Map<LocalDate, Integer> pendingLinkRows(LocalDate from, LocalDate to) {
		return new CoverageBlocks(imports.findBlockingDays(from, to), from, to).networkRowsByDay();
	}

	private static ReviewedDayView view(LocalDate day, ReviewedDay marked, LocalDate today, int pendingLinkRows,
			int unconfirmedBatches) {
		boolean finished = day.isBefore(today);
		if (marked == null) {
			return new ReviewedDayView(day, false, null, null, null, finished, pendingLinkRows, unconfirmedBatches);
		}
		return new ReviewedDayView(day, marked.isActive(), marked.getMarkedBy(), marked.getMarkedAt(),
				marked.getVersion(), finished, pendingLinkRows, unconfirmedBatches);
	}

	/** La auditoria identifica cada entidad con un UUID: el dia no tiene uno, se deriva de la fecha. */
	private static UUID entityId(LocalDate day) {
		return UUID.nameUUIDFromBytes(("itm-day:" + day).getBytes(StandardCharsets.UTF_8));
	}
}
