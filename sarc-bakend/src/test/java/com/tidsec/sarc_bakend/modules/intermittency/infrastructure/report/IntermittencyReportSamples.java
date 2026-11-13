package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.BeanUtils;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate.Exclusion;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate.ExclusionReason;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator.Aggregate;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator.BlockedDay;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator.Coverage;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator.Estimate;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator.PointResult;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport.PointRow;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport.WindowRow;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary.OutsideDetails;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary.OutsidePendingRow;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary.OutsideWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowSource;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.DurationBand;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.Figures;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.PointLabel;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Period;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Preset;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodSlicer;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Informes de prueba con datos ficticios (codigos 9xxxxx), calculados con el mismo calculador del dashboard.
 * La primera quincena de octubre de 2026 con corte el 20 de octubre: periodo cerrado, 15 dias calendario.
 */
final class IntermittencyReportSamples {

	static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

	static final Instant CUT_OFF = at("2026-10-20", "10:00");

	static final Instant FROM = at("2026-10-01", "00:00");

	static final Instant TO = at("2026-10-16", "00:00");

	static final Period PERIOD = new Period(Preset.FIRST_HALF, LocalDate.parse("2026-10-01"),
			LocalDate.parse("2026-10-16"));

	private IntermittencyReportSamples() {
	}

	static Instant at(String day, String time) {
		return LocalDate.parse(day).atTime(LocalTime.parse(time)).atZone(ZONE).toInstant();
	}

	static CorporateBranding branding() {
		try {
			return BeanUtils.instantiateClass(CorporateBranding.class.getDeclaredConstructor(OperatingZone.class),
					new OperatingZone(ZONE));
		}
		catch (NoSuchMethodException missing) {
			throw new IllegalStateException(missing);
		}
	}

	private static IntermittencyWindow window(UUID point, String label, Instant start, Instant end, boolean test) {
		IntermittencyWindow window = IntermittencyWindow.register(point, label, start, end, null, WindowSource.MANUAL,
				null, test, CUT_OFF);
		if (window.getState() == WindowState.REVIEW) {
			window.confirmReview("Revisada", UUID.randomUUID(), CUT_OFF);
		}
		return window;
	}

	/** Un cargador y cuanto duran sus ventanas, una por dia desde el dia 2. */
	private record Spec(String code, String name, int... minutesPerDay) {
	}

	/** Cargadores con ventanas confirmadas, una por dia; ademas una ventana por revisar, una abierta y una de prueba. */
	static IntermittencyReport report(boolean operational) {
		return build(operational, List.of(new Spec("900001", "QA Punto A", 600, 600, 402, 30),
				new Spec("900002", "QA Punto B", 80, 40), new Spec("900003", "QA Punto C", 3)));
	}

	/** Doce cargadores y tres empatados con el decimo puesto: el Top 10 supera los diez por empate. */
	/** El mismo informe con el apartado de disponibilidad estimada, como lo recibe quien tiene el permiso. */
	static IntermittencyReport withAvailability(boolean operational) {
		IntermittencyReport base = report(operational);
		return new IntermittencyReport(base.scope(), base.cutOff(), base.summary(), base.operationalAllowed(),
				availability(), base.points(), base.windows());
	}

	/** Diez puntos de los que uno tiene 72 minutos fuera de linea en un dia revisado: 95,00 y 99,50 %. */
	static AvailabilityEstimate availability() {
		List<PointResult> points = List.of(
				new PointResult(UUID.randomUUID(), "900001", "QA Punto A", new BigDecimal("95.00"), 1_440, 72, 1),
				new PointResult(UUID.randomUUID(), "900002", "QA Punto B", new BigDecimal("100.00"), 1_440, 0, 1),
				new PointResult(UUID.randomUUID(), null, "QA Punto Sin Codigo", null, 0, 0, 0));
		Estimate estimate = new Estimate(new Aggregate(new BigDecimal("99.50"), 14_400, 72), new Coverage(1, 15),
				List.of(new BlockedDay(LocalDate.parse("2026-10-03"), 2)), points);
		return new AvailabilityEstimate(PERIOD, CUT_OFF, estimate,
				List.of(new Exclusion(UUID.randomUUID(), "900009", "QA Punto Desconectado", ExclusionReason.DISCONNECTED)),
				"Registro manual de intermitencias (Kibana)",
				List.of("No observa Ocupado ni Con falla.", "La resolución es el minuto."));
	}

	static IntermittencyReport withTies(boolean operational) {
		List<Spec> specs = new ArrayList<>();
		for (int rank = 0; rank < 9; rank++) {
			specs.add(new Spec("90010" + rank, "QA Cargador " + rank, 200 - 10 * rank));
		}
		for (int tied = 0; tied < 3; tied++) {
			specs.add(new Spec("90011" + tied, "QA Empatado " + tied, 20));
		}
		return build(operational, specs);
	}

	/** Un periodo sin ventanas confirmadas: solo una abierta, que se informa aparte. */
	static IntermittencyReport empty(boolean operational) {
		List<IntermittencyWindow> windows = new ArrayList<>();
		UUID point = new UUID(0, 1);
		windows.add(window(point, "QA Punto A", at("2026-10-14", "08:00"), null, false));
		return assemble(operational, windows, Map.of(point, new PointLabel("900001", "QA Punto A")));
	}

	private static IntermittencyReport build(boolean operational, List<Spec> specs) {
		List<IntermittencyWindow> windows = new ArrayList<>();
		Map<UUID, PointLabel> labels = new HashMap<>();
		long index = 1;
		for (Spec spec : specs) {
			UUID point = new UUID(0, index++);
			labels.put(point, new PointLabel(spec.code(), spec.name()));
			int day = 2;
			for (int minutes : spec.minutesPerDay()) {
				Instant start = at("2026-10-%02d".formatted(day++), "08:00");
				windows.add(window(point, spec.name() + " (" + spec.code() + ")", start, start.plusSeconds(minutes * 60L),
						false));
			}
		}
		UUID first = new UUID(0, 1);
		// Fuera de lo oficial: por revisar (sin confirmar), abierta y de prueba.
		IntermittencyWindow review = IntermittencyWindow.register(first, "QA Punto A (900001)", at("2026-10-12", "08:00"),
				at("2026-10-12", "13:00"), null, WindowSource.MANUAL, null, false, CUT_OFF);
		windows.add(review);
		windows.add(window(first, "QA Punto A (900001)", at("2026-10-14", "08:00"), null, false));
		windows.add(window(first, "QA Punto A (900001)", at("2026-10-13", "08:00"), at("2026-10-13", "09:00"), true));
		return assemble(operational, windows, labels);
	}

	private static IntermittencyReport assemble(boolean operational, List<IntermittencyWindow> windows,
			Map<UUID, PointLabel> labels) {
		IntermittencyCalculator calculator = new IntermittencyCalculator(new PeriodSlicer(ZONE));
		Figures figures = calculator.calculate(windows, FROM, TO, CUT_OFF, labels);
		OutsideDetails details = null;
		if (operational) {
			List<OutsideWindow> outside = new ArrayList<>();
			for (IntermittencyWindow window : windows) {
				if (window.getState() == WindowState.OPEN || window.getState() == WindowState.REVIEW
						|| window.getState() == WindowState.TEST) {
					long minutes = new PeriodSlicer(ZONE).minutesWithin(window.getStartAt(), window.getEndAt(), FROM, TO,
							CUT_OFF);
					outside.add(new OutsideWindow(window.getId(), window.getEquipmentId(), window.getPointLabel(),
							window.getState(), window.getStartAt(), window.getEndAt(), minutes,
							window.getReviewReasons()));
				}
			}
			details = new OutsideDetails(outside,
					List.of(new OutsidePendingRow(UUID.randomUUID(), 4, RowClassification.PENDING_LINK, null)), false);
		}
		IntermittencySummary summary = new IntermittencySummary(PERIOD, CUT_OFF, figures, 2, 1, details, CUT_OFF);
		int calendarDays = 15;
		List<PointRow> points = figures.byPoint().stream()
				.map(point -> new PointRow(point.code(), point.name(), point.days(),
						BigDecimal.valueOf(point.days()).multiply(BigDecimal.valueOf(100))
								.divide(BigDecimal.valueOf(calendarDays), 1, RoundingMode.HALF_UP),
						point.windows(), point.minutes()))
				.toList();
		List<WindowRow> rows = calculator.confirmedWindows(windows, FROM, TO, CUT_OFF).stream().map(portion -> {
			var window = portion.window();
			PointLabel label = labels.get(window.getEquipmentId());
			return new WindowRow(label.code(), label.name(), window.getPointLabel(), window.getStartAt(),
					window.getEndAt(), window.getDurationMinutes(), portion.minutes(),
					DurationBand.of(window.getDurationMinutes()));
		}).toList();
		return new IntermittencyReport("Primera quincena · 01/10/2026 al 15/10/2026 · Zona America/Guayaquil · "
				+ "Todos los puntos KIA", CUT_OFF, summary, operational, null, points, rows);
	}
}
