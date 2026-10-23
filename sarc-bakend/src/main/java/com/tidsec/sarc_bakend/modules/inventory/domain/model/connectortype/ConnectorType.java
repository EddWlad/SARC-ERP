package com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

/**
 * Tipo de conector del catalogo ampliable (RF-002.4). No se elimina: si ya se uso, desaparecer
 * dejaria referencias rotas en el historico; solo se inactiva para nuevos registros.
 */
@Entity
@Table(name = "asset_connector_type")
public class ConnectorType extends BaseAuditableEntity {

	public static final String AUDIT_ENTITY_TYPE = "CONNECTOR_TYPE";
	private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{2,30}");

	@Column(nullable = false, updatable = false, length = 30)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private AdministrativeStatus adminStatus;

	protected ConnectorType() {
	}

	private ConnectorType(String code, String name) {
		super(UUID.randomUUID());
		this.code = code;
		this.name = requireName(name);
		this.adminStatus = AdministrativeStatus.ACTIVE;
	}

	public static ConnectorType create(String code, String name) {
		return new ConnectorType(normalizeCode(code), name);
	}

	/** Mayusculas y guiones bajos: "ccs 2" se guarda como "CCS_2". */
	public static String normalizeCode(String raw) {
		String code = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
		if (!CODE.matcher(code).matches()) {
			throw new BusinessRuleException("INVALID_CONNECTOR_TYPE_CODE",
					"El codigo usa de 2 a 30 letras, numeros o guiones bajos");
		}
		return code;
	}

	public void rename(String newName) {
		this.name = requireName(newName);
	}

	public void changeStatus(AdministrativeStatus status) {
		if (status == AdministrativeStatus.DELETED) {
			throw new BusinessRuleException("CONNECTOR_TYPE_NOT_DELETABLE",
					"Los tipos de conector no se eliminan; se inactivan para nuevos registros");
		}
		this.adminStatus = status;
	}

	public boolean isActive() {
		return adminStatus == AdministrativeStatus.ACTIVE;
	}

	private static String requireName(String name) {
		String trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty() || trimmed.length() > 100) {
			throw new BusinessRuleException("INVALID_CONNECTOR_TYPE_NAME", "El nombre es obligatorio (maximo 100)");
		}
		return trimmed;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public AdministrativeStatus getAdminStatus() {
		return adminStatus;
	}
}
