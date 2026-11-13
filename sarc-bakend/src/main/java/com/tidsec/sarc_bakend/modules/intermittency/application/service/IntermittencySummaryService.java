package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.IntermittencyQuery;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary.OutsideDetails;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary.OutsidePendingRow;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary.OutsideWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.Figures;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.PointLabel;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.WindowPortion;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Period;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodSlicer;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.IntermittencyImportRepository;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.WindowRepository;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Cifras del dashboard (FR-031 a FR-037) para cualquier usuario autenticado. Cada consulta recalcula desde
 * las ventanas guardadas, asi que una correccion se ve en la siguiente. El detalle de lo que no es oficial
 * solo llega a quien puede leer lo operativo (FR-005). Nunca devuelve disponibilidad estimada (FR-006).
 */
@Service
@Transactional(readOnly = true)
public class IntermittencySummaryService {

	/** Tope de filas de detalle por lista: el resto se resuelve desde Revision. */
	static final int MAX_DETAILS = 100;

	/** Las anuladas no cuentan en nada del dashboard: no se cargan. */
	private static final List<WindowState> COUNTED_STATES = List.of(WindowState.CONFIRMED, WindowState.OPEN,
			WindowState.REVIEW, WindowState.TEST);

	private final WindowRepository windows;

	private final IntermittencyImportRepository imports;

	private final KiaPointDirectory points;

	private final IntermittencyReadAccess access;

	private final Clock clock;

	private final OperatingZone zone;

	private final PeriodSlicer slicer;

	private final IntermittencyCalculator calculator;

	IntermittencySummaryService(WindowRepository windows, IntermittencyImportRepository imports,
			KiaPointDirectory points, IntermittencyReadAccess access, Clock clock, OperatingZone zone) {
		this.windows = windows;
		this.imports = imports;
		this.points = points;
		this.access = access;
		this.clock = clock;
		this.zone = zone;
		this.slicer = new PeriodSlicer(zone.zone());
		this.calculator = new IntermittencyCalculator(slicer);
	}

	/**
	 * Las cifras del dashboard y los datos con que se calcularon. Un informe usa las mismas ventanas para su
	 * detalle, asi que dashboard, PDF y Excel no pueden diferir (SC-006).
	 */
	public record Computed(IntermittencySummary summary, List<IntermittencyWindow> windows, Instant from, Instant to,
			Map<UUID, PointLabel> labels) {
	}

	/** Las ventanas confirmadas con su porcion, calculadas con el mismo calculador que las cifras (SC-006). */
	public List<WindowPortion> confirmedPortions(Computed computed) {
		return calculator.confirmedWindows(computed.windows(), computed.from(), computed.to(),
				computed.summary().cutOff());
	}

	public IntermittencySummary summary(IntermittencyQuery query) {
		return compute(query).summary();
	}

	public Computed compute(IntermittencyQuery query) {
		Instant now = Instant.now(clock);
		Period period = PeriodPresets.resolve(query.preset(), query.month(), query.from(), query.to(),
				zone.dateOf(now));
		Instant from = zone.startOf(period.from());
		Instant to = zone.startOf(period.to());
		List<IntermittencyWindow> found = windows.findInRange(query.equipmentIds(), from, to, now, COUNTED_STATES);

		Map<UUID, PointLabel> labels = labels(found);
		Figures figures = calculator.calculate(found, from, to, now, labels);

		// Las filas pendientes se asignan al periodo por los dias que tocan; ambos limites van incluidos.
		LocalDate lastDay = period.to().minusDays(1);
		Collection<UUID> points = query.equipmentIds();
		int pendingLink = (int) imports.countPendingRows(period.from(), lastDay, RowClassification.PENDING_LINK, points);
		int conflict = (int) imports.countPendingRows(period.from(), lastDay, RowClassification.CONFLICT, points);
		OutsideDetails details = access.canReadOperational()
				? details(found, pendingRows(period.from(), lastDay, points), from, to, now) : null;
		return new Computed(new IntermittencySummary(period, now, figures, pendingLink, conflict, details, now),
				found, from, to, labels);
	}

	private Map<UUID, PointLabel> labels(List<IntermittencyWindow> found) {
		Set<UUID> ids = new java.util.HashSet<>();
		found.forEach(window -> ids.add(window.getEquipmentId()));
		Map<UUID, PointLabel> labels = new HashMap<>();
		for (Map.Entry<UUID, KiaPointReference> entry : points.findByIds(ids).entrySet()) {
			labels.put(entry.getKey(), new PointLabel(entry.getValue().assignedCode(), entry.getValue().name()));
		}
		return labels;
	}

	/**
	 * Primeras filas pendientes de lotes confirmados que tocan el periodo, para el detalle de los editores. Con
	 * puntos elegidos, solo las de esos puntos: una fila por vincular no tiene punto y no se atribuye a ninguno.
	 */
	private PageResult<IntermittencyImportRow> pendingRows(LocalDate firstDay, LocalDate lastDay,
			Collection<UUID> equipmentIds) {
		return imports.searchPendingRows(firstDay, lastDay, null, equipmentIds, new PageQuery(0, MAX_DETAILS));
	}

	private OutsideDetails details(List<IntermittencyWindow> found, PageResult<IntermittencyImportRow> pending, Instant from,
			Instant to, Instant now) {
		List<OutsideWindow> outside = new ArrayList<>();
		for (IntermittencyWindow window : found) {
			WindowState state = window.getState();
			if (state != WindowState.OPEN && state != WindowState.REVIEW && state != WindowState.TEST) {
				continue;
			}
			long minutes = slicer.minutesWithin(window.getStartAt(), window.getEndAt(), from, to, now);
			if (state != WindowState.OPEN && minutes == 0) {
				continue;
			}
			outside.add(new OutsideWindow(window.getId(), window.getEquipmentId(), window.getPointLabel(), state,
					window.getStartAt(), window.getEndAt(), minutes, window.getReviewReasons()));
		}
		// Lo mas reciente primero: si el detalle se acota, se pierde lo mas antiguo, no lo que pide atencion.
		outside.sort(Comparator.comparing(OutsideWindow::startAt).reversed().thenComparing(OutsideWindow::windowId));
		List<OutsidePendingRow> rows = pending.content().stream()
				.map(row -> new OutsidePendingRow(row.getBatchId(), row.getRowNumber(), row.getClassification(),
						row.getEquipmentId()))
				.toList();
		boolean truncated = outside.size() > MAX_DETAILS || pending.totalElements() > MAX_DETAILS;
		return new OutsideDetails(outside.stream().limit(MAX_DETAILS).toList(),
				rows, truncated);
	}
}
