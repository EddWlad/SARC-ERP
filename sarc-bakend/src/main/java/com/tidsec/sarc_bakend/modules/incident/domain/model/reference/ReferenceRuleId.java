package com.tidsec.sarc_bakend.modules.incident.domain.model.reference;

import java.io.Serializable;
import java.util.UUID;

/** Clave de una regla: una fila por version y tipo. */
public record ReferenceRuleId(UUID versionId, IncidentType incidentType) implements Serializable {
}
