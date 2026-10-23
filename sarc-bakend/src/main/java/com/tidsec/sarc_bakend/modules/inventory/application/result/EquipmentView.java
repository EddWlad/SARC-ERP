package com.tidsec.sarc_bakend.modules.inventory.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ScheduleException;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ScheduleMode;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.WeeklyInterval;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/**
 * Equipo listo para presentar, armado dentro de la transaccion. Si quien consulta no puede ver
 * datos restringidos, {@code uidPuk} y {@code ocppUrl} van nulos: el backend no los envia
 * (TRD 08: proyeccion por rol, no solo ocultacion visual).
 */
public record EquipmentView(
		UUID id,
		ChargingNetwork network,
		InfrastructureType type,
		EquipmentBrand brand,
		String name,
		String serial,
		String assignedCode,
		String uidPuk,
		String location,
		String mapsUrl,
		String ocppUrl,
		String imageUrl,
		AdministrativeStatus adminStatus,
		UsageStatus usageStatus,
		UUID replacesEquipmentId,
		List<ConnectorView> connectors,
		ScheduleMode scheduleMode,
		List<WeeklyInterval> weeklyIntervals,
		List<ScheduleException> scheduleExceptions,
		boolean restrictedDataVisible,
		Instant createdAt,
		Instant updatedAt,
		long version) {

	public static EquipmentView from(Equipment equipment, String imageUrl, boolean restrictedDataVisible) {
		return new EquipmentView(equipment.getId(), equipment.getNetwork(), equipment.getType(),
				equipment.getBrand(), equipment.getName(), equipment.getSerial(), equipment.getAssignedCode(),
				restrictedDataVisible ? equipment.getUidPuk() : null, equipment.getLocation(), equipment.getMapsUrl(),
				restrictedDataVisible ? equipment.getOcppUrl() : null, imageUrl, equipment.getAdminStatus(),
				equipment.getUsageStatus(), equipment.getReplacesEquipmentId(),
				equipment.activeConnectors().stream().map(ConnectorView::from).toList(), equipment.getScheduleMode(),
				equipment.getWeeklyIntervals(), equipment.getScheduleExceptions(), restrictedDataVisible,
				equipment.getCreatedAt(), equipment.getUpdatedAt(), equipment.getVersion());
	}
}
