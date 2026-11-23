package com.tidsec.sarc_bakend.modules.incident.domain.model.reference;

/**
 * Catalogo de tipos de incidencia (FR-020), mantenido con cambios controlados del sistema (P-11). Cada tipo declara
 * para que clases de elemento afectado sirve (FR-022). La misma matriz esta en el {@code CHECK} de V021, y
 * {@code IncidentTypeTest} verifica que coincidan.
 */
public enum IncidentType {

	WAIT_FOR_CONNECTION,
	DISCONNECTION,
	CONNECTOR_FAILURE,
	EMERGENCY_STOP,
	CONNECTOR_STUCK,
	CANNOT_STOP_CHARGING,
	KIA_PERSISTENT_OUTAGE,
	INTERMITTENCY,
	INSTALLATION_OR_CONNECTIVITY,
	CHATBOT_SERVICE_FAILURE,
	SITE_ELEMENT_FAILURE,
	/** Categoria temporal, sin referencias de tiempo; se reclasifica despues con justificacion (FR-021). */
	UNCATALOGED;

	/**
	 * Si el tipo sirve para esa clase de elemento (FR-022): el chatbot admite su falla y la no catalogada, un
	 * elemento del sitio admite la falla de elemento del sitio y la no catalogada, y equipos y conectores admiten los
	 * demas tipos y la no catalogada. Ningun tipo de equipo se ofrece al chatbot ni a un elemento del sitio.
	 */
	public boolean allowedFor(ElementClass elementClass) {
		return switch (elementClass) {
			case EQUIPMENT -> this != CHATBOT_SERVICE_FAILURE && this != SITE_ELEMENT_FAILURE;
			case CHATBOT_SERVICE -> this == CHATBOT_SERVICE_FAILURE || this == UNCATALOGED;
			case SITE_ELEMENT -> this == SITE_ELEMENT_FAILURE || this == UNCATALOGED;
		};
	}
}
