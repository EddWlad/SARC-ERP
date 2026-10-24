package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionSummary;
import com.tidsec.sarc_bakend.modules.consumption.application.result.RankedEquipment;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.DailyEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyTotals;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EquipmentEnergy;
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

	ConsumptionSummary summarize(SessionCriteria criteria) {
		requirePeriod(criteria);
		SessionCriteria official = criteria.withState(SessionState.VALIDATED);
		EnergyTotals totals = sessions.totals(official);
		BigDecimal average = totals.sessions() == 0 ? BigDecimal.ZERO
				: totals.energyKwh().divide(BigDecimal.valueOf(totals.sessions()), 3, RoundingMode.HALF_UP);
		return new ConsumptionSummary(criteria.from(), criteria.to(), totals, average,
				ranking(official, TOP_SIZE), fillDays(criteria.from(), criteria.to(), sessions.daily(official)),
				complete(ChargingNetwork.class, sessions.byNetwork(official)),
				complete(SessionSource.class, sessions.bySource(official)),
				complete(InfrastructureType.class, sessions.byType(official)),
				sessions.totals(criteria.withState(SessionState.PENDING_LINK)),
				sessions.totals(criteria.withState(SessionState.TEST)), Instant.now(clock));
	}

	/** Equipos con actividad oficial ordenados por energia; {@code limit} <= 0 los trae todos. */
	List<RankedEquipment> ranking(SessionCriteria official, int limit) {
		List<EquipmentEnergy> ranked = sessions.byEquipment(official, limit);
		Map<UUID, EquipmentReference> equipment = directory.findByIds(
				ranked.stream().map(EquipmentEnergy::equipmentId).toList());
		return ranked.stream()
				.map(item -> new RankedEquipment(equipment.get(item.equipmentId()), item.sessions(), item.energyKwh()))
				.toList();
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
	private static <K extends Enum<K>> Map<K, EnergyTotals> complete(Class<K> type, Map<K, EnergyTotals> found) {
		Map<K, EnergyTotals> all = new EnumMap<>(type);
		for (K key : type.getEnumConstants()) {
			all.put(key, found.getOrDefault(key, EnergyTotals.EMPTY));
		}
		return all;
	}

	private static List<DailyEnergy> fillDays(LocalDate from, LocalDate to, List<DailyEnergy> withActivity) {
		Map<LocalDate, DailyEnergy> byDate = withActivity.stream()
				.collect(Collectors.toMap(DailyEnergy::date, Function.identity()));
		List<DailyEnergy> days = new ArrayList<>();
		for (LocalDate day = from; day.isBefore(to); day = day.plusDays(1)) {
			days.add(byDate.getOrDefault(day, new DailyEnergy(day, 0, BigDecimal.ZERO)));
		}
		return days;
	}
}
