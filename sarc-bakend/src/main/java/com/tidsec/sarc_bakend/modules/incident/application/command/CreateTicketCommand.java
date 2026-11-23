package com.tidsec.sarc_bakend.modules.incident.application.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;

/**
 * Crear un ticket (FR-010): solo el elemento, el tipo y la descripcion son obligatorios. {@code priority} nula usa la
 * propuesta; {@code t0} nulo es el momento de creacion, y uno anterior exige {@code t0Reason}. {@code connectorIds}
 * vacio es el equipo completo. {@code duplicateOverrideReason} permite crear aunque haya otro ticket abierto del mismo
 * elemento (FR-016).
 */
public record CreateTicketCommand(UUID clientRequestId, ElementClass elementClass, UUID equipmentId,
		List<UUID> connectorIds, ChatbotScope chatbotScope, SiteElementInput siteElement, IncidentType incidentType,
		String description, ReportOrigin reportOrigin, String reportOriginDetail, Priority priority,
		String priorityReason, Instant t0, String t0Reason, String duplicateOverrideReason) {

	/** Los datos de un elemento del sitio (FR-001a). */
	public record SiteElementInput(SiteElementKind kind, String elementName, UUID referenceEquipmentId,
			String manualSiteName, String manualLocation, String manualMapsUrl) {
	}
}
