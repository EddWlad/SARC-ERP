package com.tidsec.sarc_bakend.modules.inventory.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Puerto de persistencia del inventario de equipos. */
public interface EquipmentRepository {

	Equipment save(Equipment equipment);

	Optional<Equipment> findById(UUID id);

	List<Equipment> findAllById(Collection<UUID> ids);

	/** Series ya normalizadas; incluye inactivos y eliminados. */
	List<Equipment> findBySerials(Collection<String> serials);

	/** Codigos asignados tal como estan guardados; incluye cualquier red, tipo y estado. */
	List<Equipment> findByAssignedCodes(Collection<String> assignedCodes);

	/** Todos los puntos de carga de la red KIA, tambien inactivos y eliminados. */
	List<Equipment> findKiaChargingPoints();

	/**
	 * Equipos que ya usan alguno de estos identificadores (serie, codigo asignado o UID/PUK),
	 * incluidos inactivos y eliminados, porque la unicidad es global.
	 */
	List<Equipment> findHoldersOf(String serial, String assignedCode, String uidPuk);

	PageResult<Equipment> search(EquipmentSearchCriteria criteria, PageQuery pageQuery);

	/** Todos los que cumplen el filtro, ordenados por nombre; para reportes. */
	List<Equipment> findAll(EquipmentSearchCriteria criteria);

	/** Cuantos cumplen el filtro, sin cargarlos (p. ej. para rechazar un reporte desbordado antes de leerlo). */
	long count(EquipmentSearchCriteria criteria);

	List<EquipmentCount> countByDimensions(EquipmentSearchCriteria criteria);

	/** Conectores activos de los equipos filtrados, agrupados por codigo de tipo. */
	Map<String, Long> countActiveConnectorsByType(EquipmentSearchCriteria criteria);

	/** Conectores, tambien inactivos, de los equipos filtrados, por nombre de equipo y posicion. */
	List<ConnectorRow> findConnectors(EquipmentSearchCriteria criteria, UUID equipmentId);

	/** Incluye conectores de equipos eliminados: el historico les pertenece. */
	List<ConnectorRow> findConnectorsById(Collection<UUID> connectorIds);
}
