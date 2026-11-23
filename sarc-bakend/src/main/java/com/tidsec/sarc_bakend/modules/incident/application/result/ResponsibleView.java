package com.tidsec.sarc_bakend.modules.incident.application.result;

import java.util.UUID;

/** El responsable actual de un ticket, con su nombre actual. */
public record ResponsibleView(UUID id, String name) {
}
