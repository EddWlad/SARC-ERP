package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.IntermittencyQuery;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate.Exclusion;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate.ExclusionReason;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Period;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodSlicer;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.IntermittencyImportRepository;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.ReviewedDayRepository;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.WindowRepository;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Disponibilidad estimada de prueba (FR-042 a FR-047), solo para quien tiene el permiso de disponibilidad
 * (FR-006). Reune las ventanas, los dias revisados y las filas que bloquean la cobertura y deja el calculo a
 * {@link AvailabilityEstimator}. No lee ni escribe nada del modulo de Uptime y nunca registra valores.
 */
@Service
@PreAuthorize(IntermittencyPermissions.AVAILABILITY)
@Transactional(readOnly = true)
public class AvailabilityService {

	/** De donde salen los datos: el registro manual que hacen los analistas desde Kibana (FR-046). */
	public static final String ORIGIN = "Registro manual de intermitencias (Kibana)";

	static final List<String> LIMITATIONS = List.of(
			"No observa los estados Ocupado ni Con falla: solo descuenta el tiempo fuera de línea registrado.",
			"La resolución es el minuto.",
			"No hay medición fuera de los días marcados como revisados: un día sin marca no se evalúa y nunca cuenta como 100 %.");

	/** Las de prueba no descuentan ni bloquean; las anuladas no cuentan en nada. */
	private static final List<WindowState> STATES = List.of(WindowState.CONFIRMED, WindowState.OPEN,
			WindowState.REVIEW);

	private final WindowRepository windows;

	private final IntermittencyImportRepository imports;

	private final ReviewedDayRepository reviewedDays;

	private final KiaPointDirectory directory;

	private final Clock clock;

	private final OperatingZone zone;

	private final PeriodSlicer slicer;

	private final AvailabilityEstimator estimator = new AvailabilityEstimator();

	AvailabilityService(WindowRepository windows, IntermittencyImportRepository imports,
			ReviewedDayRepository reviewedDays, KiaPointDirectory directory, Clock clock, OperatingZone zone) {
		this.windows = windows;
		this.imports = imports;
		this.reviewedDays = reviewedDays;
		this.directory = directory;
		this.clock = clock;
		this.zone = zone;
		this.slicer = new PeriodSlicer(zone.zone());
	}

	public AvailabilityEstimate estimate(IntermittencyQuery query) {
		return estimate(query, Instant.now(clock));
	}

	/**
	 * La disponibilidad a un corte dado. Un informe usa el corte de su resumen, para que todo el documento hable
	 * del mismo momento (SC-006).
	 */
	public AvailabilityEstimate estimate(IntermittencyQuery query, Instant now) {
		LocalDate today = zone.dateOf(now);
		Period period = PeriodPresets.resolve(query.preset(), query.month(), query.from(), query.to(), today);
		Instant from = zone.startOf(period.from());
		Instant to = zone.startOf(period.to());
		LocalDate lastDay = period.to().minusDays(1);

		Map<UUID, Set<LocalDate>> blocked = new HashMap<>();
		Map<UUID, Map<LocalDate, Long>> offline = new HashMap<>();
		Set<UUID> withWindows = new HashSet<>();
		for (IntermittencyWindow window : windows.findInRange(query.equipmentIds(), from, to, now, STATES)) {
			UUID point = window.getEquipmentId();
			withWindows.add(point);
			switch (window.getState()) {
				case CONFIRMED -> slicer.byDay(window.getStartAt(), window.getEndAt(), from, to, now)
						.forEach(slice -> offline.computeIfAbsent(point, key -> new HashMap<>()).merge(slice.day(),
								slice.minutes(), Long::sum));
				case REVIEW -> slicer.byDay(window.getStartAt(), window.getEndAt(), from, to, now)
						.forEach(slice -> blocked.computeIfAbsent(point, key -> new HashSet<>()).add(slice.day()));
				case OPEN -> blockFromStart(blocked.computeIfAbsent(point, key -> new HashSet<>()),
						zone.dateOf(window.getStartAt()), period, today);
				default -> {
				}
			}
		}

		// Filas pendientes de lotes confirmados: por vincular bloquean la red entera; en conflicto, solo su punto.
		CoverageBlocks blocks = new CoverageBlocks(imports.findBlockingDays(period.from(), lastDay), period.from(),
				lastDay);

		Set<UUID> chosen = Set.copyOf(query.equipmentIds());
		List<AvailabilityEstimator.Point> points = new ArrayList<>();
		List<Exclusion> exclusions = new ArrayList<>();
		for (KiaPointReference point : directory.allKiaPoints()) {
			if (!chosen.isEmpty() && !chosen.contains(point.id())) {
				continue;
			}
			ExclusionReason reason = exclusionOf(point, withWindows.contains(point.id()));
			if (reason != null) {
				exclusions.add(new Exclusion(point.id(), point.assignedCode(), point.name(), reason));
				continue;
			}
			points.add(new AvailabilityEstimator.Point(point.id(), point.assignedCode(), point.name(),
					union(blocked.get(point.id()), blocks.daysOf(point.id())), offline.getOrDefault(point.id(), Map.of()),
					firstFullDay(point)));
		}

		AvailabilityEstimator.Input input = new AvailabilityEstimator.Input(period.from(), period.to(), today,
				Set.copyOf(reviewedDays.findActiveDays(period.from(), period.to())), blocks.networkRowsByDay(), points);
		return new AvailabilityEstimate(period, now, estimator.estimate(input), List.copyOf(exclusions), ORIGIN,
				LIMITATIONS);
	}

	private static Set<LocalDate> union(Set<LocalDate> windows, Set<LocalDate> rows) {
		if (windows == null) {
			return rows;
		}
		Set<LocalDate> all = new HashSet<>(windows);
		all.addAll(rows);
		return all;
	}

	/**
	 * Primer dia completo en que el punto ya existia: el dia de su alta si se creo a las 00:00 exactas y, si no,
	 * el siguiente. Antes de ese dia no se evalua, para no contar como 100 % un dia en que no existia (FR-045).
	 */
	private LocalDate firstFullDay(KiaPointReference point) {
		LocalDate createdDay = zone.dateOf(point.createdAt());
		return point.createdAt().isAfter(zone.startOf(createdDay)) ? createdDay.plusDays(1) : createdDay;
	}

	/** Una ventana abierta deja sin cubrir su punto cada dia desde el de su inicio hasta el corte (FR-045). */
	private static void blockFromStart(Set<LocalDate> days, LocalDate startDay, Period period, LocalDate today) {
		LocalDate day = startDay.isBefore(period.from()) ? period.from() : startDay;
		for (; day.isBefore(period.to()) && day.isBefore(today); day = day.plusDays(1)) {
			days.add(day);
		}
	}

	/** DESCONECTADO queda fuera; inactivo o eliminado, solo si no tiene ventanas en el rango (FR-045). */
	private static ExclusionReason exclusionOf(KiaPointReference point, boolean hasWindows) {
		if (point.disconnected()) {
			return ExclusionReason.DISCONNECTED;
		}
		if (hasWindows || point.adminStatus() == AdministrativeStatus.ACTIVE) {
			return null;
		}
		return point.adminStatus() == AdministrativeStatus.DELETED ? ExclusionReason.DELETED
				: ExclusionReason.INACTIVE;
	}
}
