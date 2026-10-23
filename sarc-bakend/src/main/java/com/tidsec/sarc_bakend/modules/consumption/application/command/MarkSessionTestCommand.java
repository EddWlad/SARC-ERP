package com.tidsec.sarc_bakend.modules.consumption.application.command;

import java.util.UUID;

/** Marca o desmarca una sesion ya importada como transaccion de prueba. */
public record MarkSessionTestCommand(UUID id, long version, boolean test) {
}
