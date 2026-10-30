package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

import com.tidsec.sarc_bakend.modules.consumption.application.port.ConsumptionReportRenderer;
import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionReport;
import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionSummary;
import com.tidsec.sarc_bakend.modules.consumption.application.result.SessionView;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionCriteria;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.catalog.ReportArchive;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * Reporte de consumo en PDF o Excel (PRD RF-003.15/16) con el mismo calculo del tablero, por eso
 * coinciden sus totales (CA-003-05). Pendientes, por aprobar y pruebas van aparte. Todos los datos de
 * un reporte se leen en una sola instantanea (REPEATABLE READ): una importacion confirmada mientras
 * se genera no puede hacer diferir el resumen del detalle. Cada emision se archiva con su huella.
 */
@Service
public class ConsumptionReportService {

	static final String KIND = "CONSUMPTION";
	/** Tope de transacciones por reporte; un periodo mas grande se divide. */
	static final int MAX_SESSIONS = 100_000;
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
			.withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	/** Estados que se informan fuera de los indicadores oficiales. */
	private static final List<SessionState> EXCLUDED = List.of(SessionState.PENDING_LINK,
			SessionState.AWAITING_APPROVAL, SessionState.TEST);

	private final EnergySessionRepository sessions;
	private final ConsumptionCalculator calculator;
	private final EquipmentDirectory directory;
	private final Map<ReportFormat, ConsumptionReportRenderer> renderers;
	private final ReportArchive archive;
	private final TransactionTemplate snapshot;
	private final Clock clock;

	ConsumptionReportService(EnergySessionRepository sessions, ConsumptionCalculator calculator,
			EquipmentDirectory directory, List<ConsumptionReportRenderer> renderers, ReportArchive archive,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.sessions = sessions;
		this.calculator = calculator;
		this.directory = directory;
		this.renderers = renderers.stream()
				.collect(Collectors.toMap(ConsumptionReportRenderer::format, Function.identity()));
		this.archive = archive;
		this.snapshot = new TransactionTemplate(transactionManager);
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		this.snapshot.setReadOnly(true);
		this.clock = clock;
	}

	/** Lee en una instantanea, renderiza fuera de la transaccion y archiva la copia emitida. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ReportFile generate(SessionCriteria criteria, ReportFormat format) {
		ConsumptionReportRenderer renderer = renderers.get(format);
		if (renderer == null) {
			throw new BusinessRuleException("REPORT_FORMAT_NOT_SUPPORTED", "Formato de reporte no disponible");
		}
		ConsumptionReport report = Objects.requireNonNull(snapshot.execute(status -> read(criteria)));
		ReportFile file = new ReportFile("consumo-" + FILE_STAMP.format(report.cutOff()) + "." + format.extension(),
				format, renderer.render(report));
		archive.archive(new ReportArchive.IssuedReportFile(KIND, report.scope(), file,
				Map.of("sessions", String.valueOf(report.summary().official().sessions()))));
		return file;
	}

	private ConsumptionReport read(SessionCriteria criteria) {
		ConsumptionSummary summary = calculator.summarize(criteria);
		SessionCriteria official = criteria.withState(SessionState.VALIDATED);
		long rows = summary.official().sessions() + summary.pendingLink().sessions()
				+ summary.awaitingApproval().sessions() + summary.test().sessions();
		if (rows > MAX_SESSIONS) {
			throw new BusinessRuleException("REPORT_TOO_LARGE",
					"El reporte supera " + MAX_SESSIONS + " sesiones; acorte el periodo o aplique mas filtros");
		}
		List<EnergySession> excluded = new ArrayList<>();
		EXCLUDED.forEach(state -> excluded.addAll(sessions.findAll(criteria.withState(state))));
		List<EnergySession> validated = sessions.findAll(official);
		Map<UUID, EquipmentReference> equipment = directory.findByIds(Stream.concat(validated.stream(), excluded.stream())
				.map(EnergySession::getEquipmentId).filter(Objects::nonNull).distinct().toList());
		return new ConsumptionReport(describe(criteria, equipment), Instant.now(clock), summary,
				calculator.ranking(official, 0), views(validated, equipment), views(excluded, equipment));
	}

	/** Alcance legible: periodo y filtros; se imprime en el encabezado y se audita. */
	private String describe(SessionCriteria criteria, Map<UUID, EquipmentReference> equipment) {
		String period = "Periodo " + DAY.format(criteria.from()) + " al " + DAY.format(criteria.to().minusDays(1));
		String filters = Stream.of(
				criteria.network() == null ? null : "red " + criteria.network(),
				criteria.type() == null ? null : "tipo " + criteria.type(),
				criteria.source() == null ? null : "fuente " + criteria.source(),
				criteria.equipmentId() == null ? null : "equipo " + equipmentName(criteria.equipmentId(), equipment))
				.filter(Objects::nonNull).collect(Collectors.joining(", "));
		return filters.isEmpty() ? period : period + " - Filtros: " + filters;
	}

	private String equipmentName(UUID id, Map<UUID, EquipmentReference> loaded) {
		EquipmentReference reference = loaded.containsKey(id) ? loaded.get(id)
				: directory.findByIds(List.of(id)).get(id);
		if (reference == null) {
			return id.toString();
		}
		return reference.assignedCode() == null ? reference.name()
				: reference.name() + " (" + reference.assignedCode() + ")";
	}

	private static List<SessionView> views(List<EnergySession> list, Map<UUID, EquipmentReference> equipment) {
		return list.stream().map(session -> new SessionView(session,
				session.getEquipmentId() == null ? null : equipment.get(session.getEquipmentId()))).toList();
	}
}
