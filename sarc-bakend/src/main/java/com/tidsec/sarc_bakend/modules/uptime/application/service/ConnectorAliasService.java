package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.ConnectorAlias;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.UptimeImportRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorDirectory;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Alias aprobados por una persona. Cambiarlos solo afecta cargas posteriores, nunca mediciones. */
@Service
@Transactional
@PreAuthorize(UptimePermissions.EDITORS)
public class ConnectorAliasService {

	private static final String ENTITY = "UPTIME_CONNECTOR_ALIAS";

	private final UptimeImportRepository imports;
	private final ConnectorDirectory connectors;
	private final AuditTrail audit;

	ConnectorAliasService(UptimeImportRepository imports, ConnectorDirectory connectors, AuditTrail audit) {
		this.imports = imports;
		this.connectors = connectors;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public PageResult<ConnectorAlias> search(UUID equipmentId, PageQuery page) {
		return imports.searchAliases(equipmentId, page);
	}

	/** Una etiqueta ya conocida se reasigna y se reactiva: hay una sola por equipo y fuente. */
	public ConnectorAlias remember(MeasurementSource source, UUID equipmentId, UUID connectorId, String label) {
		return imports.findAlias(source, equipmentId, ConnectorAlias.keyOf(label))
				.map(existing -> change(existing, connectorId, true))
				.orElseGet(() -> {
					ConnectorAlias alias = imports.saveAlias(ConnectorAlias.approve(source, equipmentId, connectorId, label));
					audit.record("UPTIME_ALIAS_CREATED", ENTITY, alias.getId(), Map.of("equipmentId", equipmentId.toString(),
							"connectorId", connectorId.toString(), "label", label));
					return alias;
				});
	}

	public ConnectorAlias update(UUID id, long version, UUID connectorId, boolean active) {
		ConnectorAlias alias = imports.findAlias(id)
				.orElseThrow(() -> new NotFoundException("UPTIME_ALIAS_NOT_FOUND", "Alias no encontrado."));
		alias.requireVersion(version);
		MeasurableConnector connector = connectors.findByIds(List.of(connectorId)).get(connectorId);
		if (connector == null || !connector.equipmentId().equals(alias.getEquipmentId())) {
			throw new BusinessRuleException("UPTIME_ALIAS_CONNECTOR_INVALID", "El conector no pertenece a ese equipo.");
		}
		return change(alias, connectorId, active);
	}

	private ConnectorAlias change(ConnectorAlias alias, UUID connectorId, boolean active) {
		Map<String, String> details = Map.of("previousConnectorId", alias.getConnectorId().toString(), "previousActive",
				String.valueOf(alias.isActive()), "connectorId", connectorId.toString(), "active",
				String.valueOf(active));
		alias.assign(connectorId, active);
		ConnectorAlias saved = imports.saveAlias(alias);
		audit.record("UPTIME_ALIAS_CHANGED", ENTITY, alias.getId(), details);
		return saved;
	}
}
