package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.io.Serializable;
import java.util.UUID;

/** Clave de un hito: una fila por episodio y tipo ({@code episode} es el id del episodio). */
public record MilestoneId(UUID episode, MilestoneKind kind) implements Serializable {
}
