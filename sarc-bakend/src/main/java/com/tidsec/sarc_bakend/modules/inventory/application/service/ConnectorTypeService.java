package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.inventory.application.command.UpdateConnectorTypeCommand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ConnectorTypeRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;

/** Catalogo ampliable de tipos de conector (RF-002.4). */
@Service
@Transactional
public class ConnectorTypeService {

	private final ConnectorTypeRepository connectorTypes;
	private final AuditTrail audit;

	ConnectorTypeService(ConnectorTypeRepository connectorTypes, AuditTrail audit) {
		this.connectorTypes = connectorTypes;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<ConnectorType> list() {
		return connectorTypes.findAll();
	}

	@PreAuthorize(InventoryPermissions.EDITORS)
	public ConnectorType create(String code, String name) {
		ConnectorType created = ConnectorType.create(code, name);
		if (connectorTypes.existsByCode(created.getCode())) {
			throw new ConflictException("CONNECTOR_TYPE_CODE_EXISTS", "Ya existe un tipo de conector con ese codigo");
		}
		connectorTypes.save(created);
		audit.record("CONNECTOR_TYPE_CREATED", ConnectorType.AUDIT_ENTITY_TYPE, created.getId(),
				Map.of("code", created.getCode()));
		return created;
	}

	@PreAuthorize(InventoryPermissions.EDITORS)
	public ConnectorType update(UpdateConnectorTypeCommand command) {
		ConnectorType target = connectorTypes.findById(command.id())
				.orElseThrow(() -> new NotFoundException("CONNECTOR_TYPE_NOT_FOUND", "El tipo de conector no existe"));
		target.requireVersion(command.version());
		if (command.name() != null) {
			target.rename(command.name());
		}
		if (command.status() != null) {
			target.changeStatus(command.status());
		}
		connectorTypes.save(target);
		audit.record("CONNECTOR_TYPE_UPDATED", ConnectorType.AUDIT_ENTITY_TYPE, target.getId(),
				Map.of("name", target.getName(), "status", target.getAdminStatus().name()));
		return target;
	}
}
