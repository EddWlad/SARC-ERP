package com.tidsec.sarc_bakend.shared.catalog;

/**
 * Resultado de buscar una identidad externa en el inventario. {@code equipment} solo viene cuando la
 * coincidencia es {@link Status#UNIQUE}: ante cualquier otra no se atribuye la sesion a ningun equipo.
 */
public record IdentityMatch(Status status, EquipmentReference equipment) {

	public enum Status {
		/** Verificada en un unico equipo. */
		UNIQUE,
		/** Verificada en dos o mas equipos: requiere conciliacion. */
		AMBIGUOUS,
		/** Registrada, pero nadie la verifico. */
		NOT_VERIFIED,
		/** Ningun equipo la tiene. */
		NOT_REGISTERED
	}
}
