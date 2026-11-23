package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketNumbering;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketNumberingRepository;

/**
 * Contador de una sola fila ({@code inc_ticket_numbering}). El numero se toma con {@code UPDATE … RETURNING}: la
 * fila queda bloqueada hasta que termina la transaccion que crea el ticket, asi que dos creaciones simultaneas nunca
 * reciben el mismo numero y, si la creacion falla, el numero vuelve (no hay hueco).
 */
@Component
class TicketNumberingRepositoryAdapter implements TicketNumberingRepository {

	private final JdbcTemplate jdbc;

	TicketNumberingRepositoryAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(readOnly = true)
	public TicketNumbering current() {
		return jdbc.query("SELECT start_number, next_number, environment FROM inc_ticket_numbering WHERE id = 1",
				(rs, row) -> new TicketNumbering((Integer) rs.getObject("start_number"),
						(Integer) rs.getObject("next_number"), rs.getString("environment")))
				.stream().findFirst().orElse(new TicketNumbering(null, null, null));
	}

	@Override
	@Transactional(readOnly = true)
	public boolean ticketsExist() {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM inc_ticket)", Boolean.class));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Integer> nextNumber() {
		List<Integer> assigned = jdbc.query("""
				UPDATE inc_ticket_numbering
				   SET next_number = next_number + 1, version = version + 1
				 WHERE id = 1 AND start_number IS NOT NULL
				RETURNING next_number - 1
				""", (rs, row) -> rs.getInt(1));
		return assigned.stream().findFirst();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean configure(int startNumber, String environment, UUID configuredBy, Instant configuredAt) {
		// 1) Bloquea la fila: una creacion en curso termina primero. 2) Cuenta los tickets en una sentencia nueva,
		// para ver los que esa creacion confirmo. 3) Solo entonces escribe.
		jdbc.query("SELECT id FROM inc_ticket_numbering WHERE id = 1 FOR UPDATE", (rs, row) -> rs.getInt(1));
		if (ticketsExist()) {
			return false;
		}
		jdbc.update("""
				UPDATE inc_ticket_numbering
				   SET start_number = ?, next_number = ?, environment = ?, configured_at = ?, configured_by = ?,
				       version = version + 1
				 WHERE id = 1
				""", startNumber, startNumber, environment, Timestamp.from(configuredAt), configuredBy);
		return true;
	}
}
