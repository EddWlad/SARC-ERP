package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.application.command.CreateTicketCommand;
import com.tidsec.sarc_bakend.modules.incident.application.command.CreateTicketCommand.SiteElementInput;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /tickets}. Solo el elemento, el tipo y la descripcion son obligatorios (FR-010); el origen
 * se propone como «Plataforma de monitoreo», la prioridad como la del tipo y T0 como el momento de creacion.
 * {@code connectorIds} vacio es el equipo completo.
 */
public record CreateTicketRequest(
		@NotNull UUID clientRequestId,
		@NotNull ElementClass elementClass,
		@Schema(nullable = true) UUID equipmentId,
		@Schema(nullable = true) @Size(max = 3) List<UUID> connectorIds,
		@Schema(nullable = true) ChatbotScope chatbotScope,
		@Schema(nullable = true) @Valid SiteElementRequest siteElement,
		@NotNull IncidentType incidentType,
		@NotBlank @Size(max = 500) String description,
		@Schema(nullable = true) ReportOrigin reportOrigin,
		@Schema(nullable = true) @Size(max = 200) String reportOriginDetail,
		@Schema(nullable = true) Priority priority,
		@Schema(nullable = true) @Size(max = 300) String priorityReason,
		@Schema(nullable = true) Instant t0,
		@Schema(nullable = true) @Size(max = 300) String t0Reason,
		@Schema(nullable = true) @Size(max = 300) String duplicateOverrideReason) {

	/** Un elemento del sitio: su clase, su nombre y una de dos ubicaciones (FR-001a). */
	public record SiteElementRequest(
			@NotNull SiteElementKind kind,
			@Schema(nullable = true) @Size(max = 120) String elementName,
			@Schema(nullable = true) UUID referenceEquipmentId,
			@Schema(nullable = true) @Size(max = 150) String manualSiteName,
			@Schema(nullable = true) @Size(max = 300) String manualLocation,
			@Schema(nullable = true) @Size(max = 500) String manualMapsUrl) {
	}

	public CreateTicketCommand toCommand() {
		SiteElementInput site = siteElement == null ? null
				: new SiteElementInput(siteElement.kind(), siteElement.elementName(), siteElement.referenceEquipmentId(),
						siteElement.manualSiteName(), siteElement.manualLocation(), siteElement.manualMapsUrl());
		return new CreateTicketCommand(clientRequestId, elementClass, equipmentId, connectorIds, chatbotScope, site,
				incidentType, description, reportOrigin == null ? ReportOrigin.proposed() : reportOrigin,
				reportOriginDetail, priority, priorityReason, t0, t0Reason, duplicateOverrideReason);
	}
}
