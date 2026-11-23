package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.AffectedElement;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ConnectorRef;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneRevision;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.NewTicket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TargetKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.MilestoneRevisionRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

/** El agregado ticket contra PostgreSQL 18: guardar, volver a leer, y lo que la base genera (feature 010). */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class TicketPersistenceTest {

	private static final UUID VERSION = UUID.fromString("5b3f1c0e-0a10-4a01-9a10-000000000001");
	private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	TicketRepository tickets;

	@Autowired
	TicketEventRepository events;

	@Autowired
	MilestoneRevisionRepository revisions;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactions;

	private TransactionTemplate tx;

	@BeforeEach
	@AfterEach
	void clean() {
		Feature010Fixtures.clearIncidents(jdbc);
		tx = new TransactionTemplate(transactions);
	}

	private NewTicket request(int number, AffectedElement element, IncidentType type, String duplicateKey) {
		return new NewTicket(number, UUID.randomUUID(), element, type, ReportOrigin.PLATFORM, null, "Sin comunicacion",
				Priority.HIGH, UUID.randomUUID(), "Analista de prueba", duplicateKey, null, NOW, null, VERSION,
				UUID.randomUUID(), NOW);
	}

	private Ticket save(NewTicket in) {
		return tx.execute(status -> tickets.save(Ticket.open(in)));
	}

	@Test
	void savesAnEquipmentTicketWithItsEpisodeMilestonesAndConnectors() {
		UUID equipment = UUID.randomUUID();
		UUID connector1 = UUID.randomUUID();
		UUID connector2 = UUID.randomUUID();
		Ticket saved = save(request(86, AffectedElement.equipment(equipment, "KIA Norte", "KIA", "CHARGING_STATION",
				List.of(new ConnectorRef(connector1, 1), new ConnectorRef(connector2, 2))), IncidentType.CONNECTOR_FAILURE,
				"EQ:" + equipment));

		Ticket loaded = tx.execute(status -> {
			Ticket found = tickets.findById(saved.getId()).orElseThrow();
			found.getEpisodes().forEach(episode -> episode.getMilestones().size());
			found.getConnectors().size();
			return found;
		});

		assertThat(loaded.getTicketNumber()).isEqualTo(86);
		assertThat(loaded.getTicketCode()).as("la columna generada la rellena la base").isEqualTo("TICKET-086");
		assertThat(loaded.getEquipmentLabelAtT0()).isEqualTo("KIA Norte");
		assertThat(loaded.getEpisodes()).hasSize(1);
		assertThat(loaded.latestEpisode().getMilestones()).hasSize(5);
		assertThat(loaded.latestEpisode().milestone(MilestoneKind.T0).getStatus()).isEqualTo(MilestoneStatus.RECORDED);
		assertThat(loaded.latestEpisode().milestone(MilestoneKind.T4).getStatus()).isEqualTo(MilestoneStatus.PENDING);
		assertThat(loaded.getConnectors()).extracting(connector -> connector.getConnectorId())
				.containsExactlyInAnyOrder(connector1, connector2);
		assertThat(loaded.getVersion()).isNotNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM inc_milestone", Integer.class)).isEqualTo(5);
	}

	@Test
	void theGeneratedCodeNeverTruncatesFourDigitNumbers() {
		Ticket saved = save(request(1000, AffectedElement.chatbot(ChatbotScope.TOTAL),
				IncidentType.CHATBOT_SERVICE_FAILURE, "CHATBOT"));
		String code = tx.execute(status -> tickets.findById(saved.getId()).orElseThrow().getTicketCode());
		assertThat(code).isEqualTo("TICKET-1000");
	}

	@Test
	void savesChatbotAndSiteElementTicketsThatTheClassChecksAccept() {
		save(request(1, AffectedElement.chatbot(null), IncidentType.CHATBOT_SERVICE_FAILURE, "CHATBOT"));
		save(request(2, AffectedElement.siteWithReference(SiteElementKind.VEHICLE_BARRIER, null, UUID.randomUUID(),
				"CC Prueba Norte", "Via Prueba", null), IncidentType.SITE_ELEMENT_FAILURE, "SITE:VEHICLE_BARRIER:a:"));
		save(request(3, AffectedElement.siteWithManualLocation(SiteElementKind.OTHER, "Porton", "Sitio sin equipos",
				"Via principal", "https://maps.example.test/porton"), IncidentType.UNCATALOGED, "SITE:OTHER:b:porton"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM inc_ticket", Integer.class)).isEqualTo(3);
	}

	@Test
	void findsATicketByItsClientRequestAndOpenTicketsByDuplicateKey() {
		UUID equipment = UUID.randomUUID();
		NewTicket first = request(10, AffectedElement.equipment(equipment, "Equipo", "JAC", "CHARGING_POINT", List.of()),
				IncidentType.DISCONNECTION, "EQ:" + equipment);
		NewTicket second = request(11, AffectedElement.equipment(equipment, "Equipo", "JAC", "CHARGING_POINT", List.of()),
				IncidentType.DISCONNECTION, "EQ:" + equipment);
		save(first);
		save(second);
		save(request(12, AffectedElement.chatbot(null), IncidentType.UNCATALOGED, "CHATBOT"));

		Optional<Ticket> byRequest = tx.execute(status -> tickets.findByClientRequestId(first.clientRequestId()));
		Optional<Ticket> byUnknownRequest = tx.execute(status -> tickets.findByClientRequestId(UUID.randomUUID()));
		assertThat(byRequest).isPresent();
		assertThat(byUnknownRequest).isEmpty();
		List<Ticket> open = tx.execute(status -> tickets.findOpenByDuplicateKey("EQ:" + equipment));
		assertThat(open).extracting(Ticket::getTicketNumber).containsExactly(10, 11);
		List<Ticket> none = tx.execute(status -> tickets.findOpenByDuplicateKey("EQ:" + UUID.randomUUID()));
		assertThat(none).isEmpty();
	}

	@Test
	void eventsAreAppendedAndAnnulledNeverDeletedWithTheirJsonDetails() {
		Ticket ticket = save(request(20, AffectedElement.chatbot(null), IncidentType.CHATBOT_SERVICE_FAILURE, "CHATBOT"));
		UUID actor = UUID.randomUUID();
		TicketEvent escalation = TicketEvent.record(ticket.getId(), ticket.latestEpisode().getId(), EventType.ESCALATED,
				NOW, NOW, actor, false, "Se escalo a tecnicos", TargetKind.TECHNICAL, "Proveedor X",
				Map.of("note", "urgente"), true);
		events.save(escalation);
		events.save(TicketEvent.record(ticket.getId(), null, EventType.OBSERVATION, NOW.plusSeconds(60), NOW.plusSeconds(60),
				actor, false, "Observacion", null, null, null, false));

		tx.executeWithoutResult(status -> events.findById(escalation.getId()).orElseThrow()
				.annul(actor, NOW.plusSeconds(120), "Registro por error"));

		List<TicketEvent> timeline = events.findByTicket(ticket.getId());
		assertThat(timeline).hasSize(2).extracting(TicketEvent::getType).containsExactly(EventType.ESCALATED,
				EventType.OBSERVATION);
		TicketEvent annulled = timeline.getFirst();
		assertThat(annulled.isAnnulled()).isTrue();
		assertThat(annulled.getAnnulReason()).isEqualTo("Registro por error");
		assertThat(annulled.getTargetKind()).isEqualTo(TargetKind.TECHNICAL);
		assertThat(annulled.getDetails()).containsEntry("note", "urgente");
		assertThat(annulled.isNotifiable()).isTrue();
		assertThat(annulled.getText()).as("el texto original no cambia al anular").isEqualTo("Se escalo a tecnicos");
	}

	@Test
	void milestoneRevisionsAreAppendedInOrder() {
		Ticket ticket = save(request(30, AffectedElement.chatbot(null), IncidentType.UNCATALOGED, "CHATBOT"));
		UUID episode = ticket.latestEpisode().getId();
		UUID actor = UUID.randomUUID();
		revisions.save(new MilestoneRevision(episode, MilestoneKind.T1, MilestoneStatus.PENDING, null, null,
				MilestoneStatus.RECORDED, NOW.plusSeconds(120), null, null, actor, NOW.plusSeconds(180)));
		revisions.save(new MilestoneRevision(episode, MilestoneKind.T1, MilestoneStatus.RECORDED, NOW.plusSeconds(120),
				null, MilestoneStatus.RECORDED, NOW.plusSeconds(60), null, "Hora real segun la plataforma", actor,
				NOW.plusSeconds(300)));

		List<MilestoneRevision> found = revisions.findByEpisode(episode);
		assertThat(found).hasSize(2);
		assertThat(found.get(0).getPreviousStatus()).isEqualTo(MilestoneStatus.PENDING);
		assertThat(found.get(1).getPreviousOccurredAt()).isEqualTo(NOW.plusSeconds(120));
		assertThat(found.get(1).getNewOccurredAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(found.get(1).getJustification()).isEqualTo("Hora real segun la plataforma");
	}

	@Test
	void aTicketCannotBeDeletedWhileItHasEpisodesBecauseHistoryIsNeverRemoved() {
		Ticket ticket = save(request(40, AffectedElement.chatbot(null), IncidentType.UNCATALOGED, "CHATBOT"));
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> jdbc.update("DELETE FROM inc_ticket WHERE id = ?", ticket.getId()))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}
}
