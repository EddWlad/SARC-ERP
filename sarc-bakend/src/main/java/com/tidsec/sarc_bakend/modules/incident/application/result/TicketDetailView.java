package com.tidsec.sarc_bakend.modules.incident.application.result;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CauseKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureResult;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CommunicationChannel;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CommunicationKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EvidenceFormat;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EvidencePurpose;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.VisitStage;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.RelationKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TargetKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

/**
 * Detalle de un ticket (contracts/incidents-api.md). Esta version basica trae lo de la creacion: el elemento con su
 * nombre actual y su etiqueta en T0, la descripcion, el origen, los episodios con sus hitos, las relaciones y la
 * cronologia. Las historias siguientes agregan duraciones, referencias, visitas, comunicaciones y evidencias.
 */
public record TicketDetailView(TicketSummaryView summary, String description, ReportOrigin reportOrigin,
		String reportOriginDetail, EquipmentView equipment, List<ConnectorView> connectors, ChatbotScope chatbotScope,
		SiteElementView siteElement, String location, String mapsUrl, ReferenceVersionView referenceVersion,
		List<EpisodeView> episodes, List<RelationView> relations, List<EventView> timeline, List<VisitView> visits,
		List<EvidenceView> evidence, List<CommunicationView> communications, List<String> allowedActions) {

	/** Una visita tecnica con su etapa actual y, si se cancelo o aplazo, quien lo decidio y por que. */
	public record VisitView(UUID id, int episodeOrdinal, String providerName, VisitStage stage, String cancelDecidedBy,
			String cancelEvidenceNote, String reescalationCondition, Instant createdAt, Instant updatedAt) {
	}

	/** Un aviso publicado a mano: canal, tipo y hora real del envio; si se anulo, su motivo. */
	public record CommunicationView(UUID id, CommunicationChannel channel, String channelDetail, CommunicationKind kind,
			Instant sentAt, boolean retroactive, String justification, UUID recordedBy, String recordedByName,
			Instant recordedAt, boolean annulled, String annulReason) {
	}

	/** Una evidencia: nunca trae la referencia de almacenamiento; el archivo se pide por su endpoint. */
	public record EvidenceView(UUID id, UUID eventId, UUID communicationId, EvidencePurpose purpose, EvidenceFormat format, long sizeBytes,
			String sha256, String originalName, UUID uploadedBy, String uploadedByName, Instant uploadedAt,
			boolean annulled) {
	}

	/** El equipo afectado: su etiqueta en T0, su nombre actual y si sigue activo (FR-007). */
	public record EquipmentView(UUID id, String labelAtT0, String currentName, boolean active) {
	}

	/** Un conector afectado: su posicion en T0 y la actual si cambio (D06). */
	public record ConnectorView(UUID id, int positionAtT0, Integer currentPosition, boolean active) {
	}

	public record SiteElementView(SiteElementKind kind, String kindLabel, String elementName,
			UUID referenceEquipmentId, String siteName) {
	}

	public record ReferenceVersionView(String code, String label) {
	}

	/**
	 * Un episodio con sus hitos, sus cinco duraciones (nulas si falta un hito, nunca 0), el juicio frente a las referencias
	 * de su version y, si esta abierto, el tiempo transcurrido desde T0 aparte (FR-045, FR-046).
	 */
	public record EpisodeView(int ordinal, TicketState state, String reopenReason, List<MilestoneView> milestones,
			List<DurationView> durations, List<ReferenceView> references, Long elapsedOpenMinutes,
			ClosureView closure) {
	}

	/**
	 * Como se cerro un episodio (FR-047): el resultado, la causa, las confirmaciones y las justificaciones. Un duplicado
	 * trae el codigo del ticket original.
	 */
	public record ClosureView(ClosureResult result, UUID duplicateOfTicketId, String duplicateOfCode,
			CauseKind causeKind, String causeText, String recommendations, Map<String, String> closureChecks,
			String finalEvidenceWaiver, String closureCommunicationWaiver, String deviationJustification) {
	}

	public record DurationView(String pair, Integer minutes, List<MilestoneKind> missing) {
	}

	/** Una referencia operativa, no un SLA contractual. */
	public record ReferenceView(String measure, int limitMinutes, boolean extendable, String result) {
	}

	public record MilestoneView(MilestoneKind kind, MilestoneStatus status, Instant occurredAt, String reason,
			boolean retroactive, boolean orderWarningAck, UUID recordedBy, Instant recordedAt) {
	}

	public record RelationView(UUID ticketId, String code, RelationKind kind, String reason) {
	}

	public record EventView(UUID id, EventType type, Instant occurredAt, Instant recordedAt, UUID recordedBy,
			String recordedByName, boolean retroactive, String text, TargetKind targetKind,
			String targetRoleOrProvider, Map<String, Object> details, boolean annulled, Instant annulledAt,
			String annulReason) {
	}
}
