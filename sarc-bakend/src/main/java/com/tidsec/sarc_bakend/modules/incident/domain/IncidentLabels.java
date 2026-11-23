package com.tidsec.sarc_bakend.modules.incident.domain;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

/**
 * Etiquetas en espanol de los mensajes y los informes de incidencias (feature 010). Es la unica fuente: el frontend
 * repite las mismas en {@code incident-labels.ts}. Cada metodo cubre todo el enum, y las pruebas fallan si se agrega
 * un valor sin etiqueta.
 */
public final class IncidentLabels {

	/** Grupo de reparto para los tickets sin red: chatbot y elementos del sitio (P-10, P-14). */
	public static final String NO_NETWORK = "Sin red";
	public static final String CHATBOT_SERVICE = "Servicio de chatbot";

	public static final String OPEN = "Abierto";
	public static final String CLOSED = "Cerrado";

	/** Marcas de hito (FR-042, FR-080). */
	public static final String MARK_RECORDED = "R";
	public static final String MARK_RETROACTIVE = "R*";
	public static final String MARK_NOT_APPLICABLE = "N/A";
	public static final String MARK_NO_DATA = "S/D";
	public static final String MARK_PENDING = "—";

	private IncidentLabels() {
	}

	public static String state(TicketState state) {
		return switch (state) {
			case OPEN -> OPEN;
			case CLOSED -> CLOSED;
		};
	}

	public static String type(IncidentType type) {
		return switch (type) {
			case WAIT_FOR_CONNECTION -> "Espera de conexión";
			case DISCONNECTION -> "Desconexión";
			case CONNECTOR_FAILURE -> "Falla de conector";
			case EMERGENCY_STOP -> "Paro de emergencia";
			case CONNECTOR_STUCK -> "Conector retenido en el vehículo";
			case CANNOT_STOP_CHARGING -> "No permite detener la carga";
			case KIA_PERSISTENT_OUTAGE -> "Punto KIA con caída persistente";
			case INTERMITTENCY -> "Intermitencia";
			case INSTALLATION_OR_CONNECTIVITY -> "Novedad de instalación o conectividad";
			case CHATBOT_SERVICE_FAILURE -> "Falla del servicio de chatbot";
			case SITE_ELEMENT_FAILURE -> "Falla de elemento del sitio";
			case UNCATALOGED -> "Incidencia no catalogada";
		};
	}

	public static String elementClass(ElementClass elementClass) {
		return switch (elementClass) {
			case EQUIPMENT -> "Equipo";
			case CHATBOT_SERVICE -> CHATBOT_SERVICE;
			case SITE_ELEMENT -> "Elemento del sitio";
		};
	}

	public static String siteElementKind(SiteElementKind kind) {
		return switch (kind) {
			case VEHICLE_BARRIER -> "Barrera vehicular";
			case OTHER -> "Otro elemento del sitio";
		};
	}

	public static String priority(Priority priority) {
		return switch (priority) {
			case MEDIUM -> "Media";
			case HIGH -> "Alta";
			case CRITICAL -> "Crítica";
		};
	}

	public static String origin(ReportOrigin origin) {
		return switch (origin) {
			case PLATFORM -> "Plataforma de monitoreo";
			case CHATBOT -> "Chatbot";
			case SITE_STAFF -> "Personal en sitio o seguridad";
			case CUSTOMER -> "Cliente";
			case PROVIDER -> "Técnico o proveedor";
			case OTHER -> "Otro";
		};
	}

	/** Marca de un hito: R, R* (retroactivo), N/A, S/D o — (pendiente). */
	public static String milestoneMark(MilestoneStatus status, boolean retroactive) {
		return switch (status) {
			case RECORDED -> retroactive ? MARK_RETROACTIVE : MARK_RECORDED;
			case NOT_APPLICABLE -> MARK_NOT_APPLICABLE;
			case NO_DATA -> MARK_NO_DATA;
			case PENDING -> MARK_PENDING;
		};
	}

	/** Etiqueta de la red conservada en T0; {@code null} es «Sin red». */
	public static String network(String network) {
		return network == null ? NO_NETWORK : network;
	}
}
