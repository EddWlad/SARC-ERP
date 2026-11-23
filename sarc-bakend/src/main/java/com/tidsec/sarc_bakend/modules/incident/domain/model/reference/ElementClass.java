package com.tidsec.sarc_bakend.modules.incident.domain.model.reference;

/**
 * Clase del elemento afectado por un ticket (FR-001). Un ticket se refiere a un solo elemento: un equipo, el
 * servicio de chatbot o un elemento del sitio.
 */
public enum ElementClass {

	/** Equipo del inventario, completo o con conectores concretos. */
	EQUIPMENT,
	/** Servicio de chatbot: no exige equipo, conector, ubicacion ni enlace de Maps (FR-005). */
	CHATBOT_SERVICE,
	/** Barrera vehicular u otro elemento del sitio que no es un cargador (FR-001a). */
	SITE_ELEMENT
}
