package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ConnectorRow;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorDirectory;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorQuery;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;

/** Implementacion del puerto publico {@link ConnectorDirectory} con los datos del inventario. */
@Service
@Transactional(readOnly = true)
class ConnectorDirectoryService implements ConnectorDirectory {

	private final EquipmentRepository equipment;

	ConnectorDirectoryService(EquipmentRepository equipment) {
		this.equipment = equipment;
	}

	@Override
	public List<MeasurableConnector> search(ConnectorQuery query) {
		EquipmentBrand brand = null;
		if (query.brand() != null) {
			brand = Arrays.stream(EquipmentBrand.values()).filter(known -> known.name().equals(query.brand()))
					.findFirst().orElse(null);
			if (brand == null) {
				return List.of();
			}
		}
		EquipmentSearchCriteria criteria = new EquipmentSearchCriteria(query.text(), query.network(), query.type(),
				brand, null, null);
		return equipment.findConnectors(criteria, query.equipmentId()).stream()
				.map(ConnectorDirectoryService::connector).toList();
	}

	@Override
	public Map<UUID, MeasurableConnector> findByIds(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return equipment.findConnectorsById(Set.copyOf(ids)).stream().map(ConnectorDirectoryService::connector)
				.collect(Collectors.toMap(MeasurableConnector::connectorId, Function.identity()));
	}

	private static MeasurableConnector connector(ConnectorRow row) {
		return new MeasurableConnector(row.connectorId(), row.ordinal(), row.typeCode(), row.typeName(), row.active(),
				row.equipmentId(), row.equipmentName(), row.assignedCode(), row.network(), row.type(),
				row.brand().name(), row.adminStatus());
	}
}
