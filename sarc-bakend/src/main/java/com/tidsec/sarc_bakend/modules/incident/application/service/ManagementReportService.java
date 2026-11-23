package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.tidsec.sarc_bakend.modules.incident.application.port.ManagementReportRenderer;
import com.tidsec.sarc_bakend.modules.incident.application.result.ManagementData;
import com.tidsec.sarc_bakend.shared.catalog.ReportArchive;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.report.ReportContentProfile;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;
import com.tidsec.sarc_bakend.shared.report.ReportPublication;

/**
 * Reporte de gestion de incidencias en PDF o Excel (FR-080 a FR-086). Usa la misma lectura que el tablero, asi que las
 * cifras coinciden; renderiza y archiva fuera de la transaccion. La emision queda archivada con su huella y con la
 * huella de las filas de episodios ({@code rowsSha256}), para comprobar despues que mostro (FR-086).
 */
@Service
@PreAuthorize(IncidentPermissions.READERS)
public class ManagementReportService {

	static final String KIND = "INCIDENT_MANAGEMENT";

	private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

	/** Documento emitido con la identidad que lo distingue en el historial. */
	public record GeneratedReport(ReportFile file, UUID id, Instant cutOff) {
	}

	private final IncidentSummaryService summaries;
	private final Map<ReportFormat, ManagementReportRenderer> renderers;
	private final ReportArchive archive;

	ManagementReportService(IncidentSummaryService summaries, List<ManagementReportRenderer> renderers,
			ReportArchive archive) {
		this.summaries = summaries;
		this.renderers = renderers.stream()
				.collect(Collectors.toMap(ManagementReportRenderer::format, Function.identity()));
		this.archive = archive;
	}

	public GeneratedReport generate(IncidentManagementQuery.Selection selection, ReportFormat format) {
		ManagementReportRenderer renderer = renderers.get(format);
		if (renderer == null) {
			throw new BusinessRuleException("REPORT_FORMAT_NOT_SUPPORTED", "Formato de informe no disponible");
		}
		ManagementData data = summaries.summary(selection);
		ReportFile file = new ReportFile("gestion-incidencias_" + FILE_DAY.format(selection.from()) + "-"
				+ FILE_DAY.format(selection.to()) + "." + format.extension(), format, renderer.render(data));
		Map<String, String> details = new HashMap<>();
		details.put("rowCount", String.valueOf(data.episodes().size()));
		details.put("rowsSha256", rowsSha256(data));
		details.put("cutOff", data.cutOff().toString());
		UUID id = archive.archive(new ReportArchive.IssuedReportFile(KIND, scope(data), file, details,
				publication(data)));
		return new GeneratedReport(file, id, data.cutOff());
	}

	/**
	 * Huella de las filas de episodios: SHA-256 de un CSV canonico con codigo, episodio, estado, instantes y minutos, sin
	 * nombres visibles ni textos, que pueden cambiar despues (FR-086).
	 */
	public static String rowsSha256(ManagementData data) {
		StringBuilder csv = new StringBuilder("code,ordinal,state,t0,t1,t2,t3,t4,m1,m2,m3,m4,m43\n");
		for (ManagementData.EpisodeLine line : data.episodes()) {
			csv.append(String.join(",", line.code(), String.valueOf(line.ordinal()), line.state(), text(line.t0()),
					text(line.t1()), text(line.t2()), text(line.t3()), text(line.t4()), text(line.t1Minutes()),
					text(line.t2Minutes()), text(line.t3Minutes()), text(line.t4Minutes()), text(line.t4MinusT3())))
					.append('\n');
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(csv.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}

	private static String scope(ManagementData data) {
		StringBuilder scope = new StringBuilder("Período: " + data.from() + " al " + data.to());
		if (data.network() != null) {
			scope.append(" · Red: ").append(data.network());
		}
		if (data.elementClass() != null) {
			scope.append(" · Clase: ").append(data.elementClass());
		}
		if (data.incidentType() != null) {
			scope.append(" · Tipo: ").append(data.incidentType());
		}
		if (data.priority() != null) {
			scope.append(" · Prioridad: ").append(data.priority());
		}
		return scope.toString();
	}

	/** Criterios archivados: el rango va con el fin exclusivo, como los demas informes. */
	private static ReportPublication publication(ManagementData data) {
		Map<String, String> criteria = new HashMap<>();
		criteria.put("timeZone", ReportPublication.TIME_ZONE);
		criteria.put("from", data.from().toString());
		criteria.put("to", data.to().plusDays(1).toString());
		if (data.network() != null) {
			criteria.put("network", data.network());
		}
		if (data.elementClass() != null) {
			criteria.put("elementClass", data.elementClass().name());
		}
		if (data.incidentType() != null) {
			criteria.put("incidentType", data.incidentType().name());
		}
		if (data.priority() != null) {
			criteria.put("priority", data.priority().name());
		}
		return new ReportPublication(ReportContentProfile.OFFICIAL_ONLY, data.cutOff(), criteria);
	}
}
