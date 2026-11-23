package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/**
 * Destino de un escalamiento (FR-052). Solo {@link #TECHNICAL} (tecnicos propios o del proveedor) cuenta para la
 * referencia de escalamiento de la Espera de conexion.
 */
public enum TargetKind {

	TECHNICAL,
	SUPERVISION,
	PLATFORM_SUPPORT,
	OTHER;

	public boolean countsForEscalationReference() {
		return this == TECHNICAL;
	}
}
