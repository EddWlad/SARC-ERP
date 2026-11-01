package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionSummary;
import com.tidsec.sarc_bakend.modules.consumption.application.result.RankedEquipment;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReviewStatus;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.DailyEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyTotals;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EquipmentEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionAggregate;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionCriteria;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/**
 * Unica fuente de los indicadores de consumo: dashboard, PDF y Excel la usan con el mismo filtro,
 * por eso arrojan los mismos totales (CA-003-05). Solo las sesiones validadas son oficiales.
 */
@Component
class ConsumptionCalculator {

	static final int TOP_SIZE = 5;
	private static final Set<SessionState> OPERATIONAL_STATES = Set.of(SessionState.PENDING_LINK,
			SessionState.AWAITING_APPROVAL, SessionState.TEST, SessionState.ENERGY_REVIEW, SessionState.ENERGY_REJECTED);
	/** Un periodo mas largo no aporta a un tablero diario y encarece los agregados. */
	static final long MAX_PERIOD_DAYS = 366;

	private final EnergySessionRepository sessions;
	private final EquipmentDirectory directory;
	private final Clock clock;

	ConsumptionCalculator(EnergySessionRepository sessions, EquipmentDirectory directory, Clock clock) {
		this.sessions = sessions;
		this.directory = directory;
		this.clock = clock;
	}

	ConsumptionSummary summarize(SessionCriteria criteria, boolean includeOperations) {
		requirePeriod(criteria);
		List<SessionAggregate> rows = sessions.aggregate(criteria.withState(SessionState.VALIDATED));
		Map<SessionState, EnergyTotals> operations = includeOperations
				? sessions.totalsByState(criteria.withState(null), OPERATIONAL_STATES) : null;
		EnergyTotals totals = fold(rows);
		List<EquipmentEnergy> withEnergy = withEnergy(rows);
		return new ConsumptionSummary(criteria.from(), criteria.to(), totals, average(totals),
				rank(withEnergy, TOP_SIZE), totals.equipment() - withEnergy.size(),
				fillDays(criteria.from(), criteria.to(), rows),
				distribution(ChargingNetwork.class, rows, SessionAggregate::network),
				distribution(SessionSource.class, rows, SessionAggregate::source),
				distribution(InfrastructureType.class, rows, SessionAggregate::type),
				operational(operations, SessionState.PENDING_LINK),
				operational(operations, SessionState.AWAITING_APPROVAL),
				operational(operations, SessionState.TEST),
				operational(operations, SessionState.ENERGY_REVIEW),
				operational(operations, SessionState.ENERGY_REJECTED),
				fold(rows.stream().filter(row -> row.energyReview() == EnergyReviewStatus.APPROVED).toList()),
				Instant.now(clock));
	}

	private static EnergyTotals operational(Map<SessionState, EnergyTotals> operations, SessionState state) {
		return operations == null ? null : operations.getOrDefault(state, EnergyTotals.EMPTY);
	}

	/** Totales de un conjunto de grupos; un equipo presente en varios grupos cuenta una vez. */
	private static EnergyTotals fold(List<SessionAggregate> rows) {
		return EnergyTotals.of(rows.stream().mapToLong(SessionAggregate::sessions).sum(),
				rows.stream().map(SessionAggregate::energyKwh).filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null),
				rows.stream().mapToLong(SessionAggregate::readings).sum(),
				rows.stream().map(SessionAggregate::equipmentId).filter(Objects::nonNull).distinct().count());
	}

	/** Equipos con al menos una lectura de energia. */
	private static List<EquipmentEnergy> withEnergy(List<SessionAggregate> rows) {
		List<EquipmentEnergy> found = new ArrayList<>();
		rows.stream().filter(row -> row.equipmentId() != null)
				.collect(Collectors.groupingBy(SessionAggregate::equipmentId))
				.forEach((equipment, group) -> {
					EnergyTotals totals = fold(group);
					if (totals.readings() > 0) {
						found.add(new EquipmentEnergy(equipment, totals.sessions(), totals.energyKwh()));
					}
				});
		return found;
	}

	/** Promedio entre sesiones con lectura: una ausencia no opera como cero (FR-014). */
	private static BigDecimal average(EnergyTotals totals) {
		if (totals.sessions() == 0) {
			return BigDecimal.ZERO;
		}
		return totals.readings() == 0 ? null
				: totals.energyKwh().divide(BigDecimal.valueOf(totals.readings()), 3, RoundingMode.HALF_UP);
	}

	/**
	 * Ranking de energia oficial conocida con posiciones compartidas y saltos. Incluye todos los
	 * empatados con el corte; {@code limit} <= 0 devuelve el ranking completo, sin limite SQL previo.
	 */
	List<RankedEquipment> ranking(SessionCriteria official, int limit) {
		return rank(sessions.byEquipment(official), limit);
	}

	private List<RankedEquipment> rank(List<EquipmentEnergy> withEnergy, int limit) {
		List<EquipmentEnergy> ranked = withinLimit(withEnergy, limit);
		Map<UUID, EquipmentReference> equipment = directory.findByIds(
				ranked.stream().map(EquipmentEnergy::equipmentId).toList());
		ranked.sort(Comparator.comparing(EquipmentEnergy::energyKwh).reversed()
				.thenComparing(item -> equipment.containsKey(item.equipmentId()) ? equipment.get(item.equipmentId()).name() : "",
						Comparator.nullsFirst(String::compareTo))
				.thenComparing(EquipmentEnergy::equipmentId));
		List<RankedEquipment> result = new ArrayList<>();
		BigDecimal previous = null;
		int position = 0;
		for (int index = 0; index < ranked.size(); index++) {
			EquipmentEnergy item = ranked.get(index);
			if (previous == null || previous.compareTo(item.energyKwh()) != 0) {
				position = index + 1;
			}
			previous = item.energyKwh();
			result.add(new RankedEquipment(equipment.get(item.equipmentId()), item.sessions(), item.energyKwh(), position));
		}
		return List.copyOf(result);
	}

	/** Solo la energia decide quien entra; el directorio se consulta unicamente para esos equipos. */
	private static List<EquipmentEnergy> withinLimit(List<EquipmentEnergy> items, int limit) {
		if (limit <= 0 || items.size() <= limit) {
			return new ArrayList<>(items);
		}
		BigDecimal cut = items.stream().map(EquipmentEnergy::energyKwh).sorted(Comparator.reverseOrder())
				.skip(limit - 1L).findFirst().orElseThrow();
		return new ArrayList<>(items.stream().filter(item -> item.energyKwh().compareTo(cut) >= 0).toList());
	}

	static void requirePeriod(SessionCriteria criteria) {
		if (criteria.from() == null || criteria.to() == null) {
			throw new BusinessRuleException("PERIOD_REQUIRED", "Indique el periodo (desde y hasta)");
		}
		if (!criteria.to().isAfter(criteria.from())) {
			throw new BusinessRuleException("INVALID_PERIOD", "El fin del periodo debe ser posterior al inicio");
		}
		if (ChronoUnit.DAYS.between(criteria.from(), criteria.to()) > MAX_PERIOD_DAYS) {
			throw new BusinessRuleException("PERIOD_TOO_LONG", "El periodo no puede superar " + MAX_PERIOD_DAYS + " dias");
		}
	}

	/** Todas las categorias, en orden de declaracion, con cero las que no tuvieron sesiones. */
	private static <K extends Enum<K>> Map<K, EnergyTotals> distribution(Class<K> type, List<SessionAggregate> rows,
			Function<SessionAggregate, K> key) {
		Map<K, List<SessionAggregate>> grouped = rows.stream().filter(row -> key.apply(row) != null)
				.collect(Collectors.groupingBy(key));
		Map<K, EnergyTotals> all = new EnumMap<>(type);
		for (K category : type.getEnumConstants()) {
			all.put(category, fold(grouped.getOrDefault(category, List.of())));
		}
		return all;
	}

	private static List<DailyEnergy> fillDays(LocalDate from, LocalDate to, List<SessionAggregate> rows) {
		Map<LocalDate, List<SessionAggregate>> byDate = rows.stream().collect(Collectors.groupingBy(SessionAggregate::date));
		List<DailyEnergy> days = new ArrayList<>();
		for (LocalDate day = from; day.isBefore(to); day = day.plusDays(1)) {
			EnergyTotals totals = fold(byDate.getOrDefault(day, List.of()));
			days.add(DailyEnergy.of(day, totals.sessions(), totals.energyKwh(), totals.readings()));
		}
		return days;
	}
}
