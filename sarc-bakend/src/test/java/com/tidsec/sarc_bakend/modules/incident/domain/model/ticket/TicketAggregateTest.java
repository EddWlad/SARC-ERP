package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class TicketAggregateTest {

	private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");
	private static final UUID ACTOR = UUID.randomUUID();
	private static final UUID VERSION = UUID.randomUUID();

	private static NewTicket request(AffectedElement element, IncidentType type, Instant t0, String t0Reason) {
		return new NewTicket(9001, UUID.randomUUID(), element, type, ReportOrigin.PLATFORM, null, "Sin comunicacion",
				Priority.HIGH, ACTOR, "Analista de prueba", "EQ:1", null, t0, t0Reason, VERSION, ACTOR, NOW);
	}

	private static AffectedElement equipment(ConnectorRef... connectors) {
		return AffectedElement.equipment(UUID.randomUUID(), "Equipo de prueba", "KIA", "CHARGING_STATION",
				List.of(connectors));
	}

	@Test
	void opensWithOneEpisodeAndFiveMilestonesT0RecordedAndT1ToT4Pending() {
		Ticket ticket = Ticket.open(request(equipment(), IncidentType.DISCONNECTION, NOW, null));

		assertThat(ticket.getState()).isEqualTo(TicketState.OPEN);
		assertThat(ticket.isOpen()).isTrue();
		assertThat(ticket.getTicketNumber()).isEqualTo(9001);
		assertThat(ticket.getEpisodes()).hasSize(1);
		Episode episode = ticket.latestEpisode();
		assertThat(episode.getOrdinal()).isEqualTo(1);
		assertThat(episode.isOpen()).isTrue();
		assertThat(episode.getReferenceVersionId()).isEqualTo(VERSION);
		assertThat(episode.getMilestones()).extracting(Milestone::getKind).containsExactly(MilestoneKind.T0,
				MilestoneKind.T1, MilestoneKind.T2, MilestoneKind.T3, MilestoneKind.T4);

		Milestone t0 = episode.milestone(MilestoneKind.T0);
		assertThat(t0.getStatus()).isEqualTo(MilestoneStatus.RECORDED);
		assertThat(t0.getOccurredAt()).isEqualTo(NOW);
		assertThat(t0.isRetroactive()).isFalse();
		for (MilestoneKind kind : List.of(MilestoneKind.T1, MilestoneKind.T2, MilestoneKind.T3, MilestoneKind.T4)) {
			Milestone pending = episode.milestone(kind);
			assertThat(pending.getStatus()).as(kind.name()).isEqualTo(MilestoneStatus.PENDING);
			assertThat(pending.getOccurredAt()).as("%s no inventa una hora".formatted(kind)).isNull();
		}
	}

	@Test
	void keepsTheElementAsItWasAtT0() {
		UUID equipmentId = UUID.randomUUID();
		AffectedElement element = AffectedElement.equipment(equipmentId, "KIA Norte", "KIA", "CHARGING_STATION",
				List.of(new ConnectorRef(UUID.randomUUID(), 1), new ConnectorRef(UUID.randomUUID(), 2)));
		Ticket ticket = Ticket.open(request(element, IncidentType.CONNECTOR_FAILURE, NOW, null));

		assertThat(ticket.getElementClass()).isEqualTo(ElementClass.EQUIPMENT);
		assertThat(ticket.getEquipmentId()).isEqualTo(equipmentId);
		assertThat(ticket.getEquipmentLabelAtT0()).isEqualTo("KIA Norte");
		assertThat(ticket.getNetworkAtT0()).isEqualTo("KIA");
		assertThat(ticket.getInfrastructureTypeAtT0()).isEqualTo("CHARGING_STATION");
		assertThat(ticket.getConnectors()).extracting(TicketConnector::getPositionAtT0).containsExactlyInAnyOrder(1, 2);
	}

	@Test
	void aTicketWithoutConnectorsAffectsTheWholeEquipment() {
		Ticket ticket = Ticket.open(request(equipment(), IncidentType.DISCONNECTION, NOW, null));
		assertThat(ticket.getConnectors()).isEmpty();
	}

	@Test
	void aChatbotTicketHasNoEquipmentSiteOrNetwork() {
		Ticket ticket = Ticket.open(request(AffectedElement.chatbot(ChatbotScope.PARTIAL),
				IncidentType.CHATBOT_SERVICE_FAILURE, NOW, null));
		assertThat(ticket.getElementClass()).isEqualTo(ElementClass.CHATBOT_SERVICE);
		assertThat(ticket.getChatbotScope()).isEqualTo(ChatbotScope.PARTIAL);
		assertThat(ticket.getEquipmentId()).isNull();
		assertThat(ticket.getNetworkAtT0()).isNull();
		assertThat(ticket.getSiteElementKind()).isNull();
		assertThat(ticket.getConnectors()).isEmpty();
	}

	@Test
	void aSiteElementKeepsItsKindNameAndLocationWithoutNetwork() {
		UUID reference = UUID.randomUUID();
		Ticket withReference = Ticket.open(request(AffectedElement.siteWithReference(SiteElementKind.VEHICLE_BARRIER,
				null, reference, "CC Prueba Norte", "Via Prueba", "https://maps.example.test/x"),
				IncidentType.SITE_ELEMENT_FAILURE, NOW, null));
		assertThat(withReference.getReferenceEquipmentId()).isEqualTo(reference);
		assertThat(withReference.getManualSiteName()).isNull();
		assertThat(withReference.getSiteLabelAtT0()).isEqualTo("CC Prueba Norte");
		assertThat(withReference.getNetworkAtT0()).isNull();
		assertThat(withReference.getEquipmentId()).isNull();

		Ticket manual = Ticket.open(request(AffectedElement.siteWithManualLocation(SiteElementKind.OTHER, "Porton",
				"Sitio sin equipos", "Via principal", null), IncidentType.SITE_ELEMENT_FAILURE, NOW, null));
		assertThat(manual.getReferenceEquipmentId()).isNull();
		assertThat(manual.getManualSiteName()).isEqualTo("Sitio sin equipos");
		assertThat(manual.getElementName()).isEqualTo("Porton");
		assertThat(manual.getMapsUrlAtT0()).isNull();
	}

	@Test
	void aT0WithinTheToleranceIsNotRetroactiveAndKeepsNoReason() {
		Ticket ticket = Ticket.open(request(equipment(), IncidentType.DISCONNECTION, NOW.minusSeconds(45), "ignorado"));
		Milestone t0 = ticket.latestEpisode().milestone(MilestoneKind.T0);
		assertThat(t0.isRetroactive()).isFalse();
		assertThat(t0.getReason()).isNull();
		assertThat(t0.getOccurredAt()).isEqualTo(NOW.minusSeconds(45));
	}

	@Test
	void aRetroactiveT0NeedsAReasonAndIsMarked() {
		Instant earlier = NOW.minus(Duration.ofHours(2));
		Ticket ticket = Ticket.open(request(equipment(), IncidentType.DISCONNECTION, earlier, "Avisaron por llamada"));
		Milestone t0 = ticket.latestEpisode().milestone(MilestoneKind.T0);
		assertThat(t0.isRetroactive()).isTrue();
		assertThat(t0.getReason()).isEqualTo("Avisaron por llamada");
		assertThat(t0.getOccurredAt()).isEqualTo(earlier);

		for (String reason : new String[] { null, "", "   " }) {
			assertThatThrownBy(() -> Ticket.open(request(equipment(), IncidentType.DISCONNECTION, earlier, reason)))
					.isInstanceOfSatisfying(BusinessRuleException.class,
							error -> assertThat(error.code()).isEqualTo(IncidentErrors.T0_INVALID));
		}
	}

	@Test
	void aFutureT0IsRejected() {
		assertThatThrownBy(() -> Ticket.open(request(equipment(), IncidentType.DISCONNECTION, NOW.plusSeconds(5), null)))
				.isInstanceOfSatisfying(BusinessRuleException.class,
						error -> assertThat(error.code()).isEqualTo(IncidentErrors.T0_INVALID));
	}

	@Test
	void aMilestoneRevisionHasNoSettersAndOnlyGetters() {
		assertThat(Arrays.stream(MilestoneRevision.class.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
				.map(m -> m.getName())).allSatisfy(name -> assertThat(name).startsWith("get"));
		MilestoneRevision revision = new MilestoneRevision(UUID.randomUUID(), MilestoneKind.T1, MilestoneStatus.PENDING,
				null, null, MilestoneStatus.RECORDED, NOW, null, null, ACTOR, NOW);
		assertThat(revision.getNewStatus()).isEqualTo(MilestoneStatus.RECORDED);
		assertThat(revision.getPreviousStatus()).isEqualTo(MilestoneStatus.PENDING);
	}

	@Test
	void anEscalationNeedsADestinationAndOnlyTechnicalCountsForTheReference() {
		assertThatThrownBy(() -> TicketEvent.record(UUID.randomUUID(), null, EventType.ESCALATED, NOW, NOW, ACTOR, false,
				"Escalado", null, null, null, true)).isInstanceOf(IllegalArgumentException.class);
		assertThat(TargetKind.TECHNICAL.countsForEscalationReference()).isTrue();
		for (TargetKind other : new TargetKind[] { TargetKind.SUPERVISION, TargetKind.PLATFORM_SUPPORT,
				TargetKind.OTHER }) {
			assertThat(other.countsForEscalationReference()).as(other.name()).isFalse();
		}
	}

	@Test
	void anEventIsOnlyAnnulledNeverChangedAndTheFirstReasonStays() {
		TicketEvent event = TicketEvent.record(UUID.randomUUID(), null, EventType.OBSERVATION, NOW, NOW, ACTOR, false,
				"Observacion", null, null, Map.of(), false);
		assertThat(event.isAnnulled()).isFalse();
		assertThatThrownBy(() -> event.annul(ACTOR, NOW, " ")).isInstanceOf(IllegalArgumentException.class);

		event.annul(ACTOR, NOW, "Registro duplicado");
		event.annul(UUID.randomUUID(), NOW.plusSeconds(60), "Otro motivo");
		assertThat(event.isAnnulled()).isTrue();
		assertThat(event.getAnnulReason()).isEqualTo("Registro duplicado");
		assertThat(event.getAnnulledBy()).isEqualTo(ACTOR);
		assertThat(event.getText()).isEqualTo("Observacion");
	}

	@Test
	void aRelationStoresTheUuidPairInPostgresOrderAndRejectsSelfRelation() {
		// Dos uuid donde UUID.compareTo (con signo) y el texto hexadecimal (sin signo, como PostgreSQL) discrepan.
		UUID positive = UUID.fromString("10000000-0000-0000-0000-000000000000");
		UUID negative = UUID.fromString("f0000000-0000-0000-0000-000000000000");
		assertThat(negative.compareTo(positive)).as("Java compara con signo").isLessThan(0);

		TicketRelation relation = TicketRelation.between(negative, positive, RelationKind.COMMON_FAILURE, null, ACTOR, NOW);
		assertThat(relation.getTicketId()).as("el texto 1... va antes que f...").isEqualTo(positive);
		assertThat(relation.getRelatedTicketId()).isEqualTo(negative);
		assertThat(TicketRelation.between(positive, negative, RelationKind.COMMON_FAILURE, null, ACTOR, NOW).getTicketId())
				.isEqualTo(positive);
		assertThat(relation.otherThan(positive)).isEqualTo(negative);
		assertThat(relation.otherThan(negative)).isEqualTo(positive);
		assertThatThrownBy(() -> TicketRelation.between(positive, positive, RelationKind.COMMON_FAILURE, null, ACTOR, NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void closureResultsAndCausesCarryTheirRules() {
		assertThat(ClosureResult.DUPLICATE.excludedFromIndicators()).isTrue();
		assertThat(ClosureResult.REGISTERED_IN_ERROR.excludedFromIndicators()).isTrue();
		assertThat(ClosureResult.RECOVERED.excludedFromIndicators()).isFalse();
		assertThat(ClosureResult.NO_FAULT_CONFIRMED.excludedFromIndicators()).isFalse();
		assertThat(CauseKind.UNDETERMINED.needsDescription()).isFalse();
		assertThat(CauseKind.CONFIRMED.needsDescription()).isTrue();
		assertThat(CauseKind.PROBABLE.needsEvidence()).isTrue();
		assertThat(CauseKind.CONFIRMED.needsEvidence()).isFalse();
	}

	@Test
	void onlyTheFourManualEventTypesAreRegisteredByTheAnalyst() {
		assertThat(Arrays.stream(EventType.values()).filter(EventType::isManual)).containsExactlyInAnyOrder(
				EventType.VALIDATION, EventType.CORRECTIVE_ACTION, EventType.ESCALATED, EventType.OBSERVATION);
		assertThat(EventType.values()).hasSize(18);
	}

	@Test
	void notifiableEventsAreTheOnesTheFutureBellNeeds() {
		assertThat(EventType.TICKET_CREATED.isNotifiable(null)).isTrue();
		assertThat(EventType.TICKET_CLOSED.isNotifiable(null)).isTrue();
		assertThat(EventType.TICKET_REOPENED.isNotifiable(null)).isTrue();
		assertThat(EventType.ESCALATED.isNotifiable(null)).isTrue();
		assertThat(EventType.PRIORITY_CHANGED.isNotifiable(Priority.CRITICAL)).isTrue();
		assertThat(EventType.PRIORITY_CHANGED.isNotifiable(Priority.HIGH)).isFalse();
		assertThat(EventType.OBSERVATION.isNotifiable(null)).isFalse();
		assertThat(EventType.MILESTONE_RECORDED.isNotifiable(null)).isFalse();
	}
}
