package com.tidsec.sarc_bakend.modules.incident.application.result;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;

/** Un ticket abierto del mismo elemento, para avisar antes de crear otro (FR-016). */
public record OpenMatchView(UUID id, String code, String incidentTypeLabel, Priority priority, Instant createdAt) {
}
