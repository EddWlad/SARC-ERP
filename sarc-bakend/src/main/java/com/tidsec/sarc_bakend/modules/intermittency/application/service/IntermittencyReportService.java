package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.IntermittencyQuery;
import com.tidsec.sarc_bakend.modules.intermittency.application.port.IntermittencyReportRenderer;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport.PointRow;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport.WindowRow;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.IntermittencySummaryService.Computed;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.DurationBand;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.PointLabel;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.TopPoint;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.WindowPortion;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Period;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Preset;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.catalog.ReportArchive;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.report.ReportContentProfile;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;
import com.tidsec.sarc_bakend.shared.report.ReportPublication;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Informe de intermitencias en PDF o Excel (FR-038 a FR-041) con el mismo calculo, los mismos filtros y las
 * mismas ventanas que el dashboard, por eso coinciden sus cifras (SC-006). Todo se lee en una sola instantanea
 * (REPEATABLE READ): un cambio confirmado mientras se genera no puede hacer diferir el resumen del detalle.
 * Cada emision se archiva con su huella; las correcciones posteriores no la modifican.
 */
@Service
public class IntermittencyReportService {

	static final String KIND = "INTERMITTENCY";

	/** Tope de ventanas por informe; un periodo mas grande se divide. */
	static final int MAX_WINDOWS = 50_000;

	/** Hasta este numero de puntos elegidos se nombran en el alcance; mas, solo se cuentan. */
	private static final int NAMED_POINTS = 5;

	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
			.withZone(ZoneOffset.UTC);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/** Documento emitido con la identidad que lo distingue en el historial. */
	public record GeneratedReport(ReportFile file, UUID id, Instant cutOff) {
	}

	private final IntermittencySummaryService summaries;

	private final KiaPointDirectory directory;

	private final Map<ReportFormat, IntermittencyReportRenderer> renderers;

	private final ReportArchive archive;

	private final TransactionTemplate snapshot;

	private final OperatingZone zone;

	private final IntermittencyReadAccess access;

	private final AvailabilityService availability;

	IntermittencyReportService(IntermittencySummaryService summaries, KiaPointDirectory directory,
			List<IntermittencyReportRenderer> renderers, ReportArchive archive,
			PlatformTransactionManager transactionManager, OperatingZone zone, IntermittencyReadAccess access,
			AvailabilityService availability) {
		this.summaries = summaries;
		this.access = access;
		this.availability = availability;
		this.directory = directory;
		this.renderers = renderers.stream()
				.collect(Collectors.toMap(IntermittencyReportRenderer::format, Function.identity()));
		this.archive = archive;
		this.snapshot = new TransactionTemplate(transactionManager);
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		this.snapshot.setReadOnly(true);
		this.zone = zone;
	}

	/** Lee en una instantanea, renderiza fuera de la transaccion y archiva la copia emitida. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public GeneratedReport generateIssued(IntermittencyQuery query, ReportFormat format) {
		IntermittencyReportRenderer renderer = renderers.get(format);
		if (renderer == null) {
			throw new BusinessRuleException("REPORT_FORMAT_NOT_SUPPORTED", "Formato de informe no disponible");
		}
		IntermittencyReport report = Objects.requireNonNull(snapshot.execute(status -> read(query)));
		ReportFile file = new ReportFile("intermitencias-" + FILE_STAMP.format(report.cutOff()) + "."
				+ format.extension(), format, renderer.render(report));
		UUID issuedId = archive.archive(new ReportArchive.IssuedReportFile(KIND, report.scope(), file,
				Map.of("windows", String.valueOf(report.summary().figures().official().windows())),
				publication(report, query)));
		return new GeneratedReport(file, issuedId, report.cutOff());
	}

	private IntermittencyReport read(IntermittencyQuery query) {
		Computed computed = summaries.compute(query);
		IntermittencySummary summary = computed.summary();
		Period period = summary.period();
		Instant cutOff = summary.cutOff();
		List<WindowPortion> portions = summaries.confirmedPortions(computed);
		requireWithinLimit(portions.size());
		// La disponibilidad estimada solo entra en el informe de quien tiene el permiso en este momento (FR-006).
		AvailabilityEstimate estimated = access.canReadAvailability() ? availability.estimate(query, cutOff) : null;
		int calendarDays = calendarDays(period, cutOff);
		List<PointRow> points = summary.figures().byPoint().stream().map(point -> point(point, calendarDays)).toList();
		List<WindowRow> windows = new ArrayList<>();
		for (WindowPortion portion : portions) {
			var window = portion.window();
			PointLabel label = computed.labels().get(window.getEquipmentId());
			windows.add(new WindowRow(label == null ? null : label.code(),
					label == null ? window.getPointLabel() : label.name(), window.getPointLabel(), window.getStartAt(),
					window.getEndAt(), window.getDurationMinutes(), portion.minutes(),
					DurationBand.of(window.getDurationMinutes())));
		}
		// Con detalle del servidor el informe es operativo: lo recibe quien puede leer lo operativo (FR-005).
		boolean operational = summary.details() != null;
		return new IntermittencyReport(describe(period, query.equipmentIds()), cutOff, summary, operational,
				estimated, points, windows);
	}

	/** Rechaza un informe con mas ventanas que el tope, sin generar ni archivar nada (FR-041a). */
	static void requireWithinLimit(int windows) {
		if (windows > MAX_WINDOWS) {
			throw new BusinessRuleException("REPORT_TOO_LARGE", "El informe supera " + MAX_WINDOWS
					+ " ventanas; acorte el período o elija menos puntos");
		}
	}

	/** Dias calendario del periodo hasta el dia del corte, incluido hoy; en un periodo cerrado, todos. */
	private int calendarDays(Period period, Instant cutOff) {
		LocalDate end = period.to().isBefore(zone.dateOf(cutOff).plusDays(1)) ? period.to()
				: zone.dateOf(cutOff).plusDays(1);
		return (int) Math.max(0, ChronoUnit.DAYS.between(period.from(), end));
	}

	private static PointRow point(TopPoint point, int calendarDays) {
		BigDecimal percent = calendarDays == 0 ? null
				: BigDecimal.valueOf(point.days()).multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(calendarDays), 1, RoundingMode.HALF_UP);
		return new PointRow(point.code(), point.name(), point.days(), percent, point.windows(),
				point.minutes());
	}

	/** Alcance legible: tipo de periodo, rango real y puntos; se imprime en el encabezado y se audita. */
	private String describe(Period period, Collection<UUID> equipmentIds) {
		String range = DAY.format(period.from()) + " al " + DAY.format(period.to().minusDays(1));
		String scope = presetName(period.preset()) + " · " + range + " · Zona America/Guayaquil";
		if (equipmentIds.isEmpty()) {
			return scope + " · Todos los puntos KIA";
		}
		if (equipmentIds.size() > NAMED_POINTS) {
			return scope + " · " + equipmentIds.size() + " puntos elegidos";
		}
		Map<UUID, KiaPointReference> found = directory.findByIds(equipmentIds);
		List<String> names = new ArrayList<>();
		for (UUID id : equipmentIds) {
			KiaPointReference point = found.get(id);
			// Un equipo que no es un punto KIA en alcance no se nombra: ni su nombre ni su codigo salen en el informe.
			names.add(point == null || !point.inScope() ? "no disponible"
					: point.assignedCode() == null ? point.name() : point.name() + " (" + point.assignedCode() + ")");
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return scope + " · Puntos: " + String.join(", ", names);
	}

	private static String presetName(Preset preset) {
		return switch (preset) {
			case FIRST_HALF -> "Primera quincena";
			case SECOND_HALF -> "Segunda quincena";
			case LAST_15_DAYS -> "Últimos 15 días";
			case CUSTOM -> "Rango personalizado";
		};
	}

	private static ReportPublication publication(IntermittencyReport report, IntermittencyQuery query) {
		Period period = report.summary().period();
		Map<String, String> criteria = new HashMap<>();
		criteria.put("from", period.from().toString());
		criteria.put("to", period.to().toString());
		criteria.put("periodPreset", period.preset().name());
		criteria.put("timeZone", ReportPublication.TIME_ZONE);
		if (!query.equipmentIds().isEmpty()) {
			criteria.put("equipmentIds", query.equipmentIds().stream().map(UUID::toString).sorted()
					.collect(Collectors.joining(",")));
		}
		ReportContentProfile profile = report.operationalAllowed() || report.availability() != null
				? ReportContentProfile.WITH_OPERATIONS
				: ReportContentProfile.OFFICIAL_ONLY;
		return new ReportPublication(profile, report.cutOff(), criteria);
	}
}
