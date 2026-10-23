package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.inventory.application.command.RegisterExternalIdentityCommand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.externalidentity.ExternalIdentity;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ExternalIdentityRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Identidades de los equipos en Wallbox, Enerlink y OCPP (RF-002.10). Una misma clave externa no
 * puede apuntar a dos equipos: si ya pertenece a otro, se rechaza en vez de fusionar identidades.
 */
@Service
@Transactional
public class ExternalIdentityService {

	private final ExternalIdentityRepository identities;
	private final EquipmentRepository equipment;
	private final AuditTrail audit;
	private final CurrentActor currentActor;
	private final Clock clock;

	ExternalIdentityService(ExternalIdentityRepository identities, EquipmentRepository equipment, AuditTrail audit,
			CurrentActor currentActor, Clock clock) {
		this.identities = identities;
		this.equipment = equipment;
		this.audit = audit;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ExternalIdentity> listForEquipment(UUID equipmentId) {
		loadEquipment(equipmentId);
		return identities.findByEquipmentId(equipmentId);
	}

	/** Equipo al que corresponde un identificador externo; base de la conciliacion de importaciones. */
	@Transactional(readOnly = true)
	public ExternalIdentity resolve(ExternalPlatform platform, String scope, String externalId) {
		return identities.findByKey(platform, ExternalIdentity.normalizeScope(scope),
				ExternalIdentity.normalizeExternalId(externalId))
				.orElseThrow(() -> new NotFoundException("EXTERNAL_IDENTITY_NOT_FOUND",
						"Ningun equipo tiene ese identificador externo"));
	}

	@PreAuthorize(InventoryPermissions.EDITORS)
	public ExternalIdentity register(RegisterExternalIdentityCommand command) {
		Equipment owner = loadEquipment(command.equipmentId());
		if (owner.isDeleted()) {
			throw new BusinessRuleException("EQUIPMENT_DELETED", "No se agregan identidades a un equipo eliminado");
		}
		ExternalIdentity identity = ExternalIdentity.register(owner.getId(), command.platform(), command.scope(),
				command.externalId(), command.alias());
		identities.findByKey(identity.getPlatform(), identity.getScope(), identity.getExternalId())
				.ifPresent(existing -> {
					throw new ConflictException("EXTERNAL_IDENTITY_IN_USE",
							"Ese identificador externo ya pertenece a un equipo",
							Map.of("equipmentId", existing.getEquipmentId()));
				});
		identities.save(identity);
		audit.record("EXTERNAL_IDENTITY_REGISTERED", ExternalIdentity.AUDIT_ENTITY_TYPE, identity.getId(),
				Map.of("equipmentId", owner.getId().toString(), "platform", identity.getPlatform().name(),
						"externalId", identity.getExternalId()));
		return identity;
	}

	/** Un analista confirma que el identificador corresponde de verdad al equipo. */
	@PreAuthorize(InventoryPermissions.EDITORS)
	public ExternalIdentity verify(UUID id, long version) {
		ExternalIdentity identity = load(id);
		identity.requireVersion(version);
		identity.verify(currentActor.id().orElse(null), Instant.now(clock));
		identities.save(identity);
		audit.record("EXTERNAL_IDENTITY_VERIFIED", ExternalIdentity.AUDIT_ENTITY_TYPE, id, Map.of());
		return identity;
	}

	/** Solo se quitan identidades sin verificar; las verificadas ya respaldan datos importados. */
	@PreAuthorize(InventoryPermissions.EDITORS)
	public void remove(UUID id) {
		ExternalIdentity identity = load(id);
		if (identity.isVerified()) {
			throw new BusinessRuleException("EXTERNAL_IDENTITY_VERIFIED",
					"Una identidad verificada no se elimina porque respalda datos importados");
		}
		identities.delete(identity);
		audit.record("EXTERNAL_IDENTITY_REMOVED", ExternalIdentity.AUDIT_ENTITY_TYPE, id,
				Map.of("equipmentId", identity.getEquipmentId().toString(), "externalId", identity.getExternalId()));
	}

	private ExternalIdentity load(UUID id) {
		return identities.findById(id)
				.orElseThrow(() -> new NotFoundException("EXTERNAL_IDENTITY_NOT_FOUND", "La identidad no existe"));
	}

	private Equipment loadEquipment(UUID id) {
		return equipment.findById(id)
				.orElseThrow(() -> new NotFoundException("EQUIPMENT_NOT_FOUND", "El equipo no existe"));
	}
}
