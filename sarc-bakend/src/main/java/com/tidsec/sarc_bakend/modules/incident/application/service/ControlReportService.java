package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.incident.application.port.ControlReportRenderer;
import com.tidsec.sarc_bakend.modules.incident.application.result.ControlReportView;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentLabels;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;
import com.tidsec.sarc_bakend.shared.catalog.ReportArchive;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.report.ReportContentProfile;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;
import com.tidsec.sarc_bakend.shared.report.ReportPublication;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Informe «Control de tickets» en PDF o Excel (FR-070 a FR-077). Lee todo con un solo corte en una transaccion de solo
 * lectura {@code REPEATABLE READ}, con la misma consulta que el listado con periodo, y renderiza fuera de ella. Cada
 * emision se archiva con su huella y la huella de sus filas; una correccion posterior no la modifica (FR-074a).
 */
@Service
@PreAuthorize(IncidentPermissions.READERS)
public class ControlReportService {

	static final String KIND = "INCIDENT_CONTROL";

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

	/** Documento emitido con la identidad que lo distingue en el historial. */
	public record GeneratedReport(ReportFile file, UUID id, Instant cutOff) {
	}

	private final IncidentReportQuery query;
	private final Map<ReportFormat, ControlReportRenderer> renderers;
	private final ReportArchive archive;
	private final TransactionTemplate snapshot;
	private final OperatingZone zone;
	private final Clock clock;

	ControlReportService(IncidentReportQuery query, List<ControlReportRenderer> renderers, ReportArchive archive,
			PlatformTransactionManager transactionManager, OperatingZone zone, Clock clock) {
		this.query = query;
		this.renderers = renderers.stream().collect(Collectors.toMap(ControlReportRenderer::format, Function.identity()));
		this.archive = archive;
		this.snapshot = new TransactionTemplate(transactionManager);
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		this.snapshot.setReadOnly(true);
		this.zone = zone;
		this.clock = clock;
	}

	/** Lee en una instantanea, renderiza fuera de la transaccion y archiva la copia emitida. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public GeneratedReport generate(IncidentReportQuery.Selection selection, ReportFormat format) {
		ControlReportRenderer renderer = renderers.get(format);
		if (renderer == null) {
			throw new BusinessRuleException("REPORT_FORMAT_NOT_SUPPORTED", "Formato de informe no disponible");
		}
		Instant cutOff = Instant.now(clock);
		IncidentReportQuery.ControlData data = Objects.requireNonNull(snapshot.execute(status -> query.control(selection, cutOff)));
		ControlReportView view = view(data);
		ReportFile file = new ReportFile(fileName(selection, cutOff, format), format, renderer.render(view));
		Map<String, String> details = new HashMap<>();
		details.put("rowCount", String.valueOf(view.total()));
		details.put("rowsSha256", view.rowsSha256());
		details.put("cutOff", cutOff.toString());
		UUID issuedId = archive.archive(new ReportArchive.IssuedReportFile(KIND, view.period() + " · " + view.filters(), file,
				details, publication(selection, cutOff)));
		return new GeneratedReport(file, issuedId, cutOff);
	}

	/** Las filas y los textos del informe: el mismo contenido para el PDF, el Excel y la huella. */
	ControlReportView view(IncidentReportQuery.ControlData data) {
		IncidentReportQuery.Selection selection = data.selection();
		List<ControlReportView.Line> lines = data.rows().stream().map(row -> new ControlReportView.Line(row.code(),
				row.elementLabel(), IncidentLabels.state(row.stateAtEnd()))).toList();
		return new ControlReportView(period(selection), filters(selection), data.cutOff(), lines, data.open(), data.closed(),
				IncidentReportQuery.rowsSha256(data.rows()));
	}

	private static String period(IncidentReportQuery.Selection selection) {
		return selection.allHistory() ? "Todo el historial"
				: "Período: " + DAY.format(selection.from()) + " al " + DAY.format(selection.to());
	}

	private static String filters(IncidentReportQuery.Selection selection) {
		List<String> active = new ArrayList<>();
		if (selection.state() != null) {
			active.add("Estado: " + IncidentLabels.state(selection.state()));
		}
		if (selection.network() != null) {
			active.add("Red: " + selection.network());
		}
		return active.isEmpty() ? "Sin filtros" : "Filtros: " + String.join(", ", active);
	}

	private String fileName(IncidentReportQuery.Selection selection, Instant cutOff, ReportFormat format) {
		String base = selection.allHistory()
				? "control-tickets_todo-el-historial_" + FILE_STAMP.format(cutOff.atZone(zone.zone()))
				: "control-tickets_" + FILE_DAY.format(selection.from()) + "-" + FILE_DAY.format(selection.to());
		return base + "." + format.extension();
	}

	/** Criterios archivados: el rango va con el fin exclusivo, como los demas informes; sin periodo, ALL_HISTORY. */
	private static ReportPublication publication(IncidentReportQuery.Selection selection, Instant cutOff) {
		Map<String, String> criteria = new HashMap<>();
		criteria.put("timeZone", ReportPublication.TIME_ZONE);
		if (selection.allHistory()) {
			criteria.put("selectionBasis", ReportPublication.ALL_HISTORY);
		}
		else {
			LocalDate to = selection.to().plusDays(1);
			criteria.put("from", selection.from().toString());
			criteria.put("to", to.toString());
		}
		if (selection.state() != null) {
			criteria.put("ticketState", selection.state() == TicketState.OPEN ? "OPEN" : "CLOSED");
		}
		if (selection.network() != null) {
			criteria.put("network", selection.network());
		}
		return new ReportPublication(ReportContentProfile.OFFICIAL_ONLY, cutOff, criteria);
	}
}
