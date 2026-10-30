package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

/** Por que una sesion Enerlink sigue sin equipo; se recalcula al importar y al vincular pendientes. */
public enum LinkIssue {

	/** Falta ACTIVO, CARGADOR o ambos: nunca se atribuye por un solo componente. */
	IDENTITY_INCOMPLETE("Identidad Enerlink incompleta"),
	/** Ningun equipo tiene ese par. */
	IDENTITY_NOT_REGISTERED("Identidad Enerlink no registrada"),
	/** El par existe, pero nadie lo verifico. */
	IDENTITY_NOT_VERIFIED("Identidad Enerlink no verificada"),
	/** El par verificado corresponde a dos o mas equipos: requiere conciliacion. */
	IDENTITY_AMBIGUOUS("Identidad Enerlink ambigua");

	private final String label;

	LinkIssue(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
