package com.tidsec.sarc_bakend.modules.inventory.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

/**
 * Identificador del equipo en una plataforma externa (Wallbox, Enerlink, OCPP), con su alias.
 * Las importaciones lo usan para conciliar sus filas con el inventario; solo una identidad
 * verificada por un analista se considera confiable (RF-002.10). {@code scope} distingue
 * cuentas o sitios de una misma plataforma; vacio si no aplica.
 */
@Entity
@Table(name = "asset_external_identity")
public class ExternalIdentity extends BaseAuditableEntity {

	public static final String AUDIT_ENTITY_TYPE = "EXTERNAL_IDENTITY";

	@Column(nullable = false, updatable = false)
	private UUID equipmentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ExternalPlatform platform;

	@Column(nullable = false, updatable = false, length = 100)
	private String scope;

	@Column(nullable = false, updatable = false, length = 150)
	private String externalId;

	@Column(length = 150)
	private String alias;

	@Column(nullable = false)
	private boolean verified;

	private UUID verifiedBy;

	private Instant verifiedAt;

	protected ExternalIdentity() {
	}

	private ExternalIdentity(UUID equipmentId, ExternalPlatform platform, String scope, String externalId, String alias) {
		super(UUID.randomUUID());
		this.equipmentId = equipmentId;
		this.platform = platform;
		this.scope = scope;
		this.externalId = externalId;
		this.alias = alias;
	}

	public static ExternalIdentity register(UUID equipmentId, ExternalPlatform platform, String scope,
			String externalId, String alias) {
		if (platform == null) {
			throw new BusinessRuleException("INVALID_EXTERNAL_IDENTITY", "La plataforma es obligatoria");
		}
		return new ExternalIdentity(equipmentId, platform, normalizeScope(scope),
				requireText(externalId, 150, "El identificador externo es obligatorio (maximo 150)"),
				alias == null || alias.isBlank() ? null : requireText(alias, 150, "El alias admite 150 caracteres"));
	}

	/** El ambito vacio representa "toda la plataforma". */
	public static String normalizeScope(String scope) {
		String text = scope == null ? "" : scope.strip();
		if (text.length() > 100) {
			throw new BusinessRuleException("INVALID_EXTERNAL_IDENTITY", "El ambito admite 100 caracteres");
		}
		return text;
	}

	public static String normalizeExternalId(String externalId) {
		return externalId == null ? "" : externalId.strip();
	}

	public void verify(UUID verifiedBy, Instant at) {
		if (verified) {
			throw new BusinessRuleException("EXTERNAL_IDENTITY_ALREADY_VERIFIED", "La identidad ya esta verificada");
		}
		this.verified = true;
		this.verifiedBy = verifiedBy;
		this.verifiedAt = at;
	}

	private static String requireText(String value, int max, String message) {
		String text = value == null ? "" : value.strip();
		if (text.isEmpty() || text.length() > max) {
			throw new BusinessRuleException("INVALID_EXTERNAL_IDENTITY", message);
		}
		return text;
	}

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public ExternalPlatform getPlatform() {
		return platform;
	}

	public String getScope() {
		return scope;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getAlias() {
		return alias;
	}

	public boolean isVerified() {
		return verified;
	}

	public UUID getVerifiedBy() {
		return verifiedBy;
	}

	public Instant getVerifiedAt() {
		return verifiedAt;
	}
}
