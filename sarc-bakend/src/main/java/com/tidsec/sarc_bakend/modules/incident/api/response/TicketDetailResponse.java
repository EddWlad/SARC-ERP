package com.tidsec.sarc_bakend.modules.incident.api.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView;
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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Detalle de un ticket (contracts/incidents-api.md, `TicketDetail`): el resumen mas el elemento con su nombre actual y
 * su etiqueta en T0, la descripcion, el origen, los episodios con sus hitos, las relaciones y la cronologia.
 */
public record TicketDetailResponse(TicketSummaryResponse summary, String description, ReportOrigin reportOrigin,
		@Schema(nullable = true) String reportOriginDetail, @Schema(nullable = true) TicketEquipment equipment,
		List<TicketConnectorResponse> connectors, @Schema(nullable = true) ChatbotScope chatbotScope,
		@Schema(nullable = true) TicketSiteElement siteElement, @Schema(nullable = true) String location,
		@Schema(nullable = true) String mapsUrl, @Schema(nullable = true) TicketReferenceVersion referenceVersion,
		List<TicketEpisode> episodes, List<TicketRelationResponse> relations, List<TicketEventResponse> timeline,
		List<TicketVisitResponse> visits, List<TicketEvidenceResponse> evidence,
		List<TicketCommunicationResponse> communications, List<String> allowedActions) {

	/** Una visita tecnica registrada a mano, con su etapa actual. */
	public record TicketVisitResponse(UUID id, int episodeOrdinal, @Schema(nullable = true) String providerName,
			VisitStage stage, @Schema(nullable = true) String cancelDecidedBy,
			@Schema(nullable = true) String cancelEvidenceNote, @Schema(nullable = true) String reescalationCondition,
			Instant createdAt, Instant updatedAt) {
	}

	/** Un aviso publicado a mano por el analista: no demuestra nada mas que lo que el analista confirmo. */
	public record TicketCommunicationResponse(UUID id, CommunicationChannel channel,
			@Schema(nullable = true) String channelDetail, CommunicationKind kind, Instant sentAt, boolean retroactive,
			@Schema(nullable = true) String justification, @Schema(nullable = true) UUID recordedBy,
			@Schema(nullable = true) String recordedByName, Instant recordedAt, boolean annulled,
			@Schema(nullable = true) String annulReason) {
	}

	/** Una evidencia (imagen o PDF) con su huella SHA-256. El archivo se descarga por su propio endpoint. */
	public record TicketEvidenceResponse(UUID id, UUID eventId, @Schema(nullable = true) UUID communicationId,
			EvidencePurpose purpose, EvidenceFormat format,
			long sizeBytes, String sha256, String originalName, @Schema(nullable = true) UUID uploadedBy,
			@Schema(nullable = true) String uploadedByName, Instant uploadedAt, boolean annulled) {
	}

	public record TicketEquipment(UUID id, String labelAtT0, String currentName, boolean active) {
	}

	public record TicketConnectorResponse(UUID id, int positionAtT0, @Schema(nullable = true) Integer currentPosition,
			boolean active) {
	}

	public record TicketSiteElement(SiteElementKind kind, String kindLabel, @Schema(nullable = true) String elementName,
			@Schema(nullable = true) UUID referenceEquipmentId, String siteName) {
	}

	public record TicketReferenceVersion(String code, String label) {
	}

	public record TicketEpisode(int ordinal, TicketState state, @Schema(nullable = true) String reopenReason,
			List<TicketMilestone> milestones, List<TicketDuration> durations, List<TicketReference> references,
			@Schema(nullable = true) Long elapsedOpenMinutes, @Schema(nullable = true) TicketClosure closure) {
	}

	/** Como se cerro el episodio: resultado, causa, confirmaciones y justificaciones. */
	public record TicketClosure(ClosureResult result, @Schema(nullable = true) UUID duplicateOfTicketId,
			@Schema(nullable = true) String duplicateOfCode, CauseKind causeKind, @Schema(nullable = true) String causeText,
			@Schema(nullable = true) String recommendations, Map<String, String> closureChecks,
			@Schema(nullable = true) String finalEvidenceWaiver,
			@Schema(nullable = true) String closureCommunicationWaiver,
			@Schema(nullable = true) String deviationJustification) {
	}

	/** Duracion de un par de hitos: nula si falta alguno, nunca 0. */
	public record TicketDuration(String pair, @Schema(nullable = true) Integer minutes, List<MilestoneKind> missing) {
	}

	/** Una referencia operativa con su resultado: WITHIN, OUTSIDE, NOT_EVALUABLE o NO_REFERENCE. No es un SLA. */
	public record TicketReference(String measure, int limitMinutes, boolean extendable, String result) {
	}

	public record TicketMilestone(MilestoneKind kind, MilestoneStatus status, @Schema(nullable = true) Instant occurredAt,
			@Schema(nullable = true) String reason, boolean retroactive, boolean orderWarningAck,
			@Schema(nullable = true) UUID recordedBy, Instant recordedAt) {
	}

	public record TicketRelationResponse(UUID ticketId, @Schema(nullable = true) String code, RelationKind kind,
			@Schema(nullable = true) String reason) {
	}

	public record TicketEventResponse(UUID id, EventType type, Instant occurredAt, Instant recordedAt,
			@Schema(nullable = true) UUID recordedBy, @Schema(nullable = true) String recordedByName,
			boolean retroactive, @Schema(nullable = true) String text, @Schema(nullable = true) TargetKind targetKind,
			@Schema(nullable = true) String targetRoleOrProvider, Map<String, Object> details, boolean annulled,
			@Schema(nullable = true) Instant annulledAt, @Schema(nullable = true) String annulReason) {
	}

	public static TicketDetailResponse from(TicketDetailView view) {
		return new TicketDetailResponse(TicketSummaryResponse.from(view.summary()), view.description(),
				view.reportOrigin(), view.reportOriginDetail(),
				view.equipment() == null ? null : new TicketEquipment(view.equipment().id(), view.equipment().labelAtT0(),
						view.equipment().currentName(), view.equipment().active()),
				view.connectors().stream().map(c -> new TicketConnectorResponse(c.id(), c.positionAtT0(), c.currentPosition(),
						c.active())).toList(),
				view.chatbotScope(),
				view.siteElement() == null ? null : new TicketSiteElement(view.siteElement().kind(),
						view.siteElement().kindLabel(), view.siteElement().elementName(),
						view.siteElement().referenceEquipmentId(), view.siteElement().siteName()),
				view.location(), view.mapsUrl(),
				view.referenceVersion() == null ? null : new TicketReferenceVersion(view.referenceVersion().code(),
						view.referenceVersion().label()),
				view.episodes().stream().map(e -> new TicketEpisode(e.ordinal(), e.state(), e.reopenReason(),
						e.milestones().stream().map(m -> new TicketMilestone(m.kind(), m.status(), m.occurredAt(), m.reason(),
								m.retroactive(), m.orderWarningAck(), m.recordedBy(), m.recordedAt())).toList(),
						e.durations().stream().map(d -> new TicketDuration(d.pair(), d.minutes(), d.missing())).toList(),
						e.references().stream().map(r -> new TicketReference(r.measure(), r.limitMinutes(), r.extendable(),
								r.result())).toList(),
						e.elapsedOpenMinutes(),
							e.closure() == null ? null : new TicketClosure(e.closure().result(), e.closure().duplicateOfTicketId(),
									e.closure().duplicateOfCode(), e.closure().causeKind(), e.closure().causeText(),
									e.closure().recommendations(), e.closure().closureChecks(),
									e.closure().finalEvidenceWaiver(), e.closure().closureCommunicationWaiver(),
									e.closure().deviationJustification())))
						.toList(),
				view.relations().stream().map(r -> new TicketRelationResponse(r.ticketId(), r.code(), r.kind(), r.reason())).toList(),
				view.timeline().stream().map(e -> new TicketEventResponse(e.id(), e.type(), e.occurredAt(), e.recordedAt(),
						e.recordedBy(), e.recordedByName(), e.retroactive(), e.text(), e.targetKind(),
						e.targetRoleOrProvider(), e.details(), e.annulled(), e.annulledAt(), e.annulReason())).toList(),
				view.visits().stream().map(v -> new TicketVisitResponse(v.id(), v.episodeOrdinal(), v.providerName(),
						v.stage(), v.cancelDecidedBy(), v.cancelEvidenceNote(), v.reescalationCondition(), v.createdAt(),
						v.updatedAt())).toList(),
				view.evidence().stream().map(e -> new TicketEvidenceResponse(e.id(), e.eventId(), e.communicationId(), e.purpose(), e.format(),
						e.sizeBytes(), e.sha256(), e.originalName(), e.uploadedBy(), e.uploadedByName(), e.uploadedAt(),
						e.annulled())).toList(),
				view.communications().stream().map(c -> new TicketCommunicationResponse(c.id(), c.channel(), c.channelDetail(),
						c.kind(), c.sentAt(), c.retroactive(), c.justification(), c.recordedBy(), c.recordedByName(),
						c.recordedAt(), c.annulled(), c.annulReason())).toList(),
				view.allowedActions());
	}
}
