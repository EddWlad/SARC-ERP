package com.tidsec.sarc_bakend.shared.catalog;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/**
 * Lo que el modulo de intermitencias puede saber de un equipo del inventario: su identidad estable, el codigo
 * asignado, el uso y el estado administrativo. {@code inScope} es {@code true} solo si es un punto de carga
 * de la red KIA; una electrolinera o un punto de otra red se devuelve con {@code false} para poder rechazarlo
 * con su motivo en lugar de tratarlo como desconocido. {@code usageStatus} es FREE, BLOCKED o DISCONNECTED, y
 * es nulo si el equipo no es un punto de carga. {@code createdAt} es cuando se dio de alta en el inventario: la
 * disponibilidad estimada no cuenta a un punto antes de existir.
 */
public record KiaPointReference(UUID id, String name, String assignedCode, String usageStatus,
		AdministrativeStatus adminStatus, boolean inScope, Instant createdAt) {

	/** Valor de {@code usageStatus} de un punto instalado sin servicio. */
	public static final String DISCONNECTED = "DISCONNECTED";

	public boolean disconnected() {
		return DISCONNECTED.equals(usageStatus);
	}
}
