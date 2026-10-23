package com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

/**
 * Conector fisico de un equipo, identificado por su posicion (1 a 3). Su id es estable porque
 * uptime e incidencias lo referencian; por eso nunca se borra: si el equipo deja de tenerlo,
 * queda inactivo.
 */
@Entity
@Table(name = "asset_connector")
public class Connector extends BaseAuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "equipment_id", nullable = false, updatable = false)
	private Equipment equipment;

	@Column(nullable = false, updatable = false)
	private short ordinal;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "connector_type_id", nullable = false)
	private ConnectorType connectorType;

	@Column(nullable = false)
	private boolean active;

	protected Connector() {
	}

	Connector(Equipment equipment, int ordinal, ConnectorType connectorType) {
		super(UUID.randomUUID());
		this.equipment = equipment;
		this.ordinal = (short) ordinal;
		this.connectorType = connectorType;
		this.active = true;
	}

	void assign(ConnectorType type) {
		this.connectorType = type;
		this.active = true;
	}

	void retire() {
		this.active = false;
	}

	public int getOrdinal() {
		return ordinal;
	}

	public ConnectorType getConnectorType() {
		return connectorType;
	}

	public boolean isActive() {
		return active;
	}
}
