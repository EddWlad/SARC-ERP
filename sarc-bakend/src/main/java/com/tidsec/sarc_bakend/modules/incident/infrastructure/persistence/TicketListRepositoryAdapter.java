package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentLabels;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketListQuery;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketListRepository;

/**
 * Listado de tickets con SQL nativo: filtra por los campos de FR-061, ordena por columna y pagina, y solo devuelve los
 * ids de la pagina. Los filtros por condicion miran los hitos del ultimo episodio. Todos los valores van como
 * parametros; el unico texto que se concatena al SQL sale de enumeraciones del dominio, nunca del cliente.
 */
@Component
class TicketListRepositoryAdapter implements TicketListRepository {

	private static final String LATEST = "(SELECT max(x.ordinal) FROM inc_episode x WHERE x.ticket_id = t.id)";

	private final JdbcTemplate jdbc;

	TicketListRepositoryAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(readOnly = true)
	public IdPage find(TicketListQuery query) {
		List<Object> params = new ArrayList<>();
		String where = where(query, params);
		Long total = jdbc.queryForObject("SELECT count(*) FROM inc_ticket t" + where, Long.class, params.toArray());
		List<Object> pageParams = new ArrayList<>(params);
		pageParams.add(query.size());
		pageParams.add((long) query.page() * query.size());
		List<UUID> ids = jdbc.query("SELECT t.id FROM inc_ticket t" + where + orderBy(query.sort()) + " LIMIT ? OFFSET ?",
				(rs, row) -> rs.getObject(1, UUID.class), pageParams.toArray());
		return new IdPage(ids, total == null ? 0 : total);
	}

	private static String where(TicketListQuery query, List<Object> params) {
		List<String> clauses = new ArrayList<>();
		if (query.onlyIds() != null) {
			if (query.onlyIds().isEmpty()) {
				clauses.add("1 = 0");
			}
			else {
				// Un solo parametro de texto con los UUID: no hay tope de parametros por consulta. Solo lleva UUID.
				clauses.add("t.id = ANY (CAST(? AS uuid[]))");
				params.add("{" + query.onlyIds().stream().map(java.util.UUID::toString)
						.collect(java.util.stream.Collectors.joining(",")) + "}");
			}
		}
		if (query.state() != null) {
			clauses.add("t.state = ?");
			params.add(query.state().name());
		}
		if (query.elementClass() != null) {
			clauses.add("t.element_class = ?");
			params.add(query.elementClass().name());
		}
		if (query.incidentType() != null) {
			clauses.add("t.incident_type = ?");
			params.add(query.incidentType().name());
		}
		if (query.priority() != null) {
			clauses.add("t.priority = ?");
			params.add(query.priority().name());
		}
		if (query.network() != null) {
			if (TicketListQuery.NO_NETWORK.equals(query.network())) {
				clauses.add("t.network_at_t0 IS NULL");
			}
			else {
				clauses.add("t.network_at_t0 = ?");
				params.add(query.network());
			}
		}
		if (query.equipmentId() != null) {
			clauses.add("(t.equipment_id = ? OR t.reference_equipment_id = ?)");
			params.add(query.equipmentId());
			params.add(query.equipmentId());
		}
		if (query.responsibleId() != null) {
			clauses.add("t.current_responsible_id = ?");
			params.add(query.responsibleId());
		}
		if (query.condition() != null) {
			clauses.add(condition(query.condition()));
		}
		if (query.search() != null && !query.search().isBlank()) {
			String like = "%" + escapeLike(query.search().strip().toLowerCase(Locale.ROOT)) + "%";
			clauses.add("(lower(t.ticket_code) LIKE ? ESCAPE '\\' OR lower(t.description) LIKE ? ESCAPE '\\'"
					+ " OR lower(t.current_responsible_name) LIKE ? ESCAPE '\\'"
					+ " OR lower(coalesce(t.equipment_label_at_t0, '')) LIKE ? ESCAPE '\\')");
			for (int i = 0; i < 4; i++) {
				params.add(like);
			}
		}
		return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
	}

	/** FR-062: las condiciones se derivan de los hitos del episodio vigente, sin agregar estados. */
	private static String condition(com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketCondition condition) {
		return switch (condition) {
			case RECOVERED_OBSERVING -> "t.state = 'OPEN' AND " + milestone("T3", "RECORDED");
			case IN_ATTENTION -> "t.state = 'OPEN' AND " + milestone("T2", "RECORDED") + " AND " + milestone("T3", "PENDING");
			case PENDING_VALIDATION -> "t.state = 'OPEN' AND " + milestone("T1", "PENDING");
		};
	}

	private static String milestone(String kind, String status) {
		return "EXISTS (SELECT 1 FROM inc_episode e JOIN inc_milestone m ON m.episode_id = e.id"
				+ " WHERE e.ticket_id = t.id AND e.ordinal = " + LATEST + " AND m.kind = '" + kind
				+ "' AND m.status = '" + status + "')";
	}

	private static String orderBy(TicketListQuery.Sort sort) {
		String direction = sort.descending() ? " DESC" : " ASC";
		String expression = switch (sort.field()) {
			case CODE -> "t.ticket_number";
			case STATE -> "t.state";
			case TYPE -> typeLabelExpression();
			case NETWORK -> "coalesce(t.network_at_t0, 'ZZZ')";
			case PRIORITY -> "CASE t.priority WHEN 'CRITICAL' THEN 3 WHEN 'HIGH' THEN 2 ELSE 1 END";
			case RESPONSIBLE -> "lower(t.current_responsible_name)";
			case T0 -> "(SELECT m.occurred_at FROM inc_episode e JOIN inc_milestone m ON m.episode_id = e.id"
					+ " AND m.kind = 'T0' WHERE e.ticket_id = t.id ORDER BY e.ordinal DESC LIMIT 1)";
			case LAST_ACTIVITY -> "t.last_activity_at";
		};
		return " ORDER BY " + expression + direction + ", t.ticket_number DESC";
	}

	/** El tipo se ordena por su etiqueta en español, no por su codigo (contracts/incidents-api.md). */
	private static String typeLabelExpression() {
		StringBuilder sql = new StringBuilder("CASE t.incident_type");
		for (IncidentType type : IncidentType.values()) {
			sql.append(" WHEN '").append(type.name()).append("' THEN '")
					.append(IncidentLabels.type(type).replace("'", "''")).append('\'');
		}
		return sql.append(" END").toString();
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
