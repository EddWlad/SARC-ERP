package com.tidsec.sarc_bakend.modules.intermittency.application.command;

import java.time.Instant;
import java.util.UUID;

/** Pone fin a una ventana abierta. {@code version} es la que leyo quien la cierra (FR-018). */
public record CloseWindowCommand(UUID windowId, long version, Instant endAt) {
}
