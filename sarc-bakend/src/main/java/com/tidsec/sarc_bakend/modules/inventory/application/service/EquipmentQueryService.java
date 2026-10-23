package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.inventory.application.result.EquipmentView;
import com.tidsec.sarc_bakend.modules.inventory.application.result.InventorySummary;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentCount;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Consultas del inventario: disponibles para todos los roles autenticados. */
@Service
@Transactional(readOnly = true)
public class EquipmentQueryService {

	private final EquipmentRepository equipment;
	private final EquipmentViewFactory views;
	private final Clock clock;

	EquipmentQueryService(EquipmentRepository equipment, EquipmentViewFactory views, Clock clock) {
		this.equipment = equipment;
		this.views = views;
		this.clock = clock;
	}

	public PageResult<EquipmentView> search(EquipmentSearchCriteria criteria, PageQuery pageQuery) {
		return equipment.search(criteria, pageQuery).map(views::forCurrentUser);
	}

	public EquipmentView get(UUID id) {
		return equipment.findById(id).map(views::forCurrentUser)
				.orElseThrow(() -> new NotFoundException("EQUIPMENT_NOT_FOUND", "El equipo no existe"));
	}

	/**
	 * Indicadores para el mismo filtro del listado (sin estado: activos e inactivos). Los
	 * eliminados se informan aparte en {@code deletedEquipment}, nunca dentro de los totales.
	 */
	public InventorySummary summary(EquipmentSearchCriteria criteria) {
		List<EquipmentCount> counts = equipment.countByDimensions(criteria);
		long deleted = equipment.countByDimensions(withStatus(criteria, AdministrativeStatus.DELETED)).stream()
				.mapToLong(EquipmentCount::count).sum();
		List<EquipmentCount> points = counts.stream()
				.filter(count -> count.type() == InfrastructureType.CHARGING_POINT).toList();
		return new InventorySummary(
				counts.stream().mapToLong(EquipmentCount::count).sum(),
				deleted,
				totals(InfrastructureType.class, counts, EquipmentCount::type),
				totals(ChargingNetwork.class, counts, EquipmentCount::network),
				totals(EquipmentBrand.class, counts, EquipmentCount::brand),
				totals(AdministrativeStatus.class, counts, EquipmentCount::adminStatus),
				totals(UsageStatus.class, points, EquipmentCount::usageStatus),
				equipment.countActiveConnectorsByType(criteria),
				Instant.now(clock));
	}

	private static EquipmentSearchCriteria withStatus(EquipmentSearchCriteria criteria, AdministrativeStatus status) {
		return new EquipmentSearchCriteria(criteria.text(), criteria.network(), criteria.type(), criteria.brand(), status,
				criteria.usageStatus());
	}

	/** Suma por categoria con todas las categorias del enum presentes, aunque valgan cero. */
	private static <E extends Enum<E>> Map<E, Long> totals(Class<E> type, List<EquipmentCount> counts,
			Function<EquipmentCount, E> dimension) {
		Map<E, Long> totals = new EnumMap<>(type);
		for (E value : type.getEnumConstants()) {
			totals.put(value, 0L);
		}
		counts.stream().filter(count -> dimension.apply(count) != null)
				.forEach(count -> totals.merge(dimension.apply(count), count.count(), Long::sum));
		return totals;
	}
}
