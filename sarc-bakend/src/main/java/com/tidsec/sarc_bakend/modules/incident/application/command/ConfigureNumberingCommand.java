package com.tidsec.sarc_bakend.modules.incident.application.command;

/** Configurar el numero inicial de tickets: el numero, el ambiente y la confirmacion del codigo resultante. */
public record ConfigureNumberingCommand(int startNumber, String environment, String confirmation) {
}
