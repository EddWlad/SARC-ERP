package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.uptime.application.command.UptimeFilter;
import com.tidsec.sarc_bakend.modules.uptime.application.port.UptimeReportRenderer;
import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeReport;
import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeView;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementFact;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator.Unit;
import com.tidsec.sarc_bakend.shared.catalog.ReportArchive;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.report.ReportContentProfile;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;
import com.tidsec.sarc_bakend.shared.report.ReportPublication;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Reporte de uptime en PDF o Excel con el mismo calculo del tablero (FR-037). Lee en una instantanea,
 * renderiza fuera de la transaccion y archiva la copia emitida.
 */
@Service
public class UptimeReportService {

	static final String KIND = "UPTIME";
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
			.withZone(ZoneOffset.UTC);

	private final UptimeQueryService queries;
	private final Map<ReportFormat, UptimeReportRenderer> renderers;
	private final ReportArchive archive;
	private final TransactionTemplate snapshot;
	private final OperatingZone zone;
	private final DateTimeFormatter moment;
	private final Clock clock;

	/** Documento emitido con la identidad que lo distingue en el historial. */
	public record GeneratedReport(ReportFile file, UUID id, Instant cutOff) {
	}

	UptimeReportService(UptimeQueryService queries, List<UptimeReportRenderer> renderers, ReportArchive archive,
			PlatformTransactionManager transactionManager, OperatingZone zone, Clock clock) {
		this.queries = queries;
		this.renderers = renderers.stream().collect(Collectors.toMap(UptimeReportRenderer::format, Function.identity()));
		this.archive = archive;
		this.snapshot = new TransactionTemplate(transactionManager);
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		this.snapshot.setReadOnly(true);
		this.zone = zone;
		this.moment = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone.zone());
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public GeneratedReport generateIssued(UptimeFilter filter, ReportFormat format) {
		UptimeReportRenderer renderer = renderers.get(format);
		if (renderer == null) {
			throw new BusinessRuleException("REPORT_FORMAT_NOT_SUPPORTED", "Formato de reporte no disponible");
		}
		UptimeReport report = Objects.requireNonNull(snapshot.execute(status -> read(filter)));
		ReportFile file = new ReportFile("uptime-" + FILE_STAMP.format(report.cutOff()) + "." + format.extension(), format,
				renderer.render(report));
		UUID issuedId = archive.archive(new ReportArchive.IssuedReportFile(KIND, report.scope(), file,
				Map.of("connectors", String.valueOf(report.coverage().included())), publication(report, filter)));
		return new GeneratedReport(file, issuedId, report.cutOff());
	}

	private UptimeReport read(UptimeFilter filter) {
		UptimeView view = queries.view(filter);
		return new UptimeReport(describe(view, filter), Instant.now(clock), view.resolved(),
				section(view.officialUnits(), view.official()), section(view.provisionalUnits(), view.provisional()),
				withGaps(view.trend()), weeks(view.officialUnits()), view.coverage(),
				UptimeCalculator.exclusions(view.units(), false), view.pendingLink());
	}

	private static UptimeReport.Section section(List<Unit> units, UptimeCalculator.Kpis kpis) {
		if (kpis == null) {
			return null;
		}
		List<MeasurementFact> facts = units.stream().map(Unit::fact).filter(Objects::nonNull).toList();
		return new UptimeReport.Section(facts.getFirst().periodType(),
				facts.stream().map(MeasurementFact::periodStart).min(Comparator.naturalOrder()).orElseThrow(),
				facts.stream().map(MeasurementFact::periodEnd).max(Comparator.naturalOrder()).orElseThrow(), kpis,
				UptimeCalculator.ranking(units), UptimeCalculator.equipment(units));
	}

	private static List<UptimeReport.WeekResult> weeks(List<Unit> units) {
		Map<Instant, List<Unit>> byWeek = units.stream().collect(
				Collectors.groupingBy(Unit::weekStart, LinkedHashMap::new, Collectors.toList()));
		return byWeek.entrySet().stream().map(entry -> new UptimeReport.WeekResult(entry.getKey(),
				UptimeCalculator.kpis(entry.getValue()))).filter(week -> week.kpis().uptimePercent() != null).toList();
	}

	/** Una semana sin cifra queda nula para que el grafico muestre el hueco. */
	private static List<UptimeView.TrendPoint> withGaps(List<UptimeView.TrendPoint> trend) {
		if (trend.isEmpty()) {
			return trend;
		}
		Map<LocalDate, UptimeView.TrendPoint> known = trend.stream()
				.collect(Collectors.toMap(UptimeView.TrendPoint::weekStart, Function.identity()));
		List<UptimeView.TrendPoint> filled = new ArrayList<>();
		for (LocalDate week = trend.getFirst().weekStart(); !week.isAfter(trend.getLast().weekStart()); week = week
				.plusWeeks(1)) {
			filled.add(known.getOrDefault(week, new UptimeView.TrendPoint(week, null)));
		}
		return filled;
	}

	private ReportPublication publication(UptimeReport report, UptimeFilter filter) {
		Map<String, String> criteria = new HashMap<>();
		UptimeView.ResolvedPeriod period = report.period();
		LocalDate today = zone.dateOf(report.cutOff());
		criteria.put("from", (filter.from() != null ? filter.from()
				: period.from() != null ? zone.dateOf(period.from()) : today).toString());
		criteria.put("to", (filter.to() != null ? filter.to()
				: period.to() != null ? zone.dateOf(period.to().minusNanos(1)).plusDays(1) : today.plusDays(1)).toString());
		criteria.put("timeZone", ReportPublication.TIME_ZONE);
		criteria.put("periodKind", period.kind().name());
		if (period.periodType() != null) {
			criteria.put("periodType", period.periodType().name());
		}
		if (filter.network() != null) {
			criteria.put("network", filter.network().name());
		}
		if (filter.type() != null) {
			criteria.put("type", filter.type().name());
		}
		if (filter.brand() != null && !filter.brand().isBlank()) {
			criteria.put("brand", filter.brand());
		}
		if (filter.equipmentId() != null) {
			criteria.put("equipmentId", filter.equipmentId().toString());
		}
		if (filter.connectorId() != null) {
			criteria.put("connectorId", filter.connectorId().toString());
		}
		return new ReportPublication(ReportContentProfile.OFFICIAL_ONLY, report.cutOff(), criteria);
	}

	/** Alcance legible: periodo real y filtros; se imprime en el encabezado y se audita. */
	private String describe(UptimeView view, UptimeFilter filter) {
		UptimeView.ResolvedPeriod period = view.resolved();
		String range = period.kind() == OpeningPeriodPolicy.Kind.NONE || period.from() == null ? "Sin período con datos"
				: label(period.periodType()) + " del " + moment.format(period.from()) + " al " + moment.format(period.to())
						+ " (hora de Ecuador)";
		String filters = Stream.of(filter.network() == null ? null : "red " + filter.network(),
				filter.type() == null ? null : "tipo " + filter.type(),
				filter.brand() == null || filter.brand().isBlank() ? null : "marca " + filter.brand(),
				filter.equipmentId() == null ? null : "equipo " + equipmentName(view, filter),
				filter.connectorId() == null ? null : "un conector").filter(Objects::nonNull)
				.collect(Collectors.joining(", "));
		return filters.isEmpty() ? range : range + " - Filtros: " + filters;
	}

	private static String label(PeriodType type) {
		return type.isExact() ? "Semanas oficiales" : "Ventana provisional";
	}

	private static String equipmentName(UptimeView view, UptimeFilter filter) {
		return view.units().stream().map(Unit::connector)
				.filter(connector -> connector.equipmentId().equals(filter.equipmentId())).findFirst()
				.map(connector -> connector.assignedCode() == null ? connector.equipmentName()
						: connector.equipmentName() + " (" + connector.assignedCode() + ")")
				.orElse(filter.equipmentId().toString());
	}
}
