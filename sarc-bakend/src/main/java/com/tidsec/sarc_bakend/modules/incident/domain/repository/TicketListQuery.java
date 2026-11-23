package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.util.Collection;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketCondition;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

/**
 * Filtros, orden y pagina del listado de tickets (FR-061). {@code network} es la red conservada en T0, o
 * {@link #NO_NETWORK} para los tickets sin red (chatbot y elementos del sitio). El elemento afectado no se ordena.
 */
public record TicketListQuery(TicketState state, ElementClass elementClass, IncidentType incidentType,
		Priority priority, String network, UUID equipmentId, UUID responsibleId, TicketCondition condition,
		String search, Sort sort, int page, int size, Collection<UUID> onlyIds) {

	/** Sin restriccion de identidad: el listado de siempre. */
	public TicketListQuery(TicketState state, ElementClass elementClass, IncidentType incidentType, Priority priority,
			String network, UUID equipmentId, UUID responsibleId, TicketCondition condition, String search, Sort sort,
			int page, int size) {
		this(state, elementClass, incidentType, priority, network, equipmentId, responsibleId, condition, search, sort, page,
				size, null);
	}

	/**
	 * El mismo listado limitado a esos tickets y sin filtro de estado actual: con un periodo, el estado que se filtra es
	 * el del final del periodo y ya lo aplico la consulta del informe (FR-073, FR-074). La condicion se deriva del estado
	 * actual de los hitos y no tiene sentido en un periodo: se descarta para no mezclar dos instantes.
	 */
	public TicketListQuery withPeriod(Collection<UUID> ids) {
		return new TicketListQuery(null, elementClass, incidentType, priority, network, equipmentId, responsibleId,
				null, search, sort, page, size, ids);
	}

	/** Valor de {@code network} para «Sin red». */
	public static final String NO_NETWORK = "NONE";

	/** Campos por los que se puede ordenar. */
	public enum SortField {
		CODE, STATE, TYPE, NETWORK, PRIORITY, RESPONSIBLE, T0, LAST_ACTIVITY
	}

	public record Sort(SortField field, boolean descending) {

		/** Por defecto: la ultima actualizacion, la mas reciente primero. */
		public static Sort byDefault() {
			return new Sort(SortField.LAST_ACTIVITY, true);
		}
	}
}
