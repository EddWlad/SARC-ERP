package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Conector afectado por un ticket de equipo, por identidad (D06) y con la posicion que mostraba al crear el ticket.
 * Sin filas, la afectacion es del equipo completo (FR-002).
 */
@Entity
@Table(name = "inc_ticket_connector")
@IdClass(TicketConnectorId.class)
public class TicketConnector {

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ticket_id", nullable = false, updatable = false)
	private Ticket ticket;

	@Id
	@Column(name = "connector_id", updatable = false)
	private UUID connectorId;

	@Column(nullable = false, updatable = false)
	private short positionAtT0;

	protected TicketConnector() {
	}

	TicketConnector(Ticket ticket, ConnectorRef ref) {
		this.ticket = ticket;
		this.connectorId = ref.connectorId();
		this.positionAtT0 = (short) ref.positionAtT0();
	}

	public UUID getConnectorId() {
		return connectorId;
	}

	public int getPositionAtT0() {
		return positionAtT0;
	}
}
