package com.tidsec.sarc_bakend.modules.uptime.domain.model.importing;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Etiqueta externa aprobada por una persona para un conector de ese equipo y fuente. Traduce al
 * identificador existente: no crea identidad de conector ni cambia mediciones guardadas.
 */
@Entity
@Table(name = "upt_connector_alias")
public class ConnectorAlias extends BaseAuditableEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private MeasurementSource source;

	@Column(nullable = false, updatable = false)
	private UUID equipmentId;

	@Column(nullable = false)
	private UUID connectorId;

	@Column(nullable = false, updatable = false, length = 150)
	private String labelOriginal;

	@Column(nullable = false, updatable = false, length = 310)
	private String labelKey;

	@Column(nullable = false)
	private boolean active;

	protected ConnectorAlias() {
	}

	private ConnectorAlias(UUID id) {
		super(id);
	}

	public static ConnectorAlias approve(MeasurementSource source, UUID equipmentId, UUID connectorId, String label) {
		ConnectorAlias alias = new ConnectorAlias(UUID.randomUUID());
		alias.source = source;
		alias.equipmentId = equipmentId;
		alias.connectorId = connectorId;
		alias.labelOriginal = label;
		alias.labelKey = keyOf(label);
		alias.active = true;
		return alias;
	}

	/** Equivalencia de D05, sin el separador de {@link ExternalKey}: PostgreSQL no admite NUL. */
	public static String keyOf(String label) {
		return new ExternalKey(null, label).comparable().replace("\u0000", "");
	}

	public void assign(UUID connector, boolean enabled) {
		connectorId = connector;
		active = enabled;
	}

	public MeasurementSource getSource() { return source; }
	public UUID getEquipmentId() { return equipmentId; }
	public UUID getConnectorId() { return connectorId; }
	public String getLabelOriginal() { return labelOriginal; }
	public String getLabelKey() { return labelKey; }
	public boolean isActive() { return active; }
}
