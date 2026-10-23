package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.inventory.application.port.InventoryReportRenderer;
import com.tidsec.sarc_bakend.modules.inventory.application.result.EquipmentView;
import com.tidsec.sarc_bakend.modules.inventory.application.result.InventoryReport;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * Reporte de inventario en PDF o Excel con los mismos filtros y calculos del tablero (PRD RN-G-005).
 * Nunca incluye UID/PUK ni URL OCPP. Cada emision queda auditada con sus filtros y la huella
 * SHA-256 del archivo, para poder reconstruir que se entrego (RF-RPT-08).
 */
@Service
@Transactional
public class InventoryReportService {

	/** El inventario maestro es de cientos de equipos; este tope evita reportes desbordados. */
	static final int MAX_ROWS = 5000;
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
			.withZone(ZoneOffset.UTC);

	private final EquipmentRepository equipment;
	private final EquipmentQueryService queries;
	private final EquipmentViewFactory views;
	private final Map<ReportFormat, InventoryReportRenderer> renderers;
	private final AuditTrail audit;
	private final Clock clock;

	InventoryReportService(EquipmentRepository equipment, EquipmentQueryService queries, EquipmentViewFactory views,
			List<InventoryReportRenderer> renderers, AuditTrail audit, Clock clock) {
		this.equipment = equipment;
		this.queries = queries;
		this.views = views;
		this.renderers = renderers.stream()
				.collect(Collectors.toMap(InventoryReportRenderer::format, Function.identity()));
		this.audit = audit;
		this.clock = clock;
	}

	public ReportFile generate(EquipmentSearchCriteria criteria, ReportFormat format) {
		InventoryReportRenderer renderer = renderers.get(format);
		if (renderer == null) {
			throw new BusinessRuleException("REPORT_FORMAT_NOT_SUPPORTED", "Formato de reporte no disponible");
		}
		List<EquipmentView> rows = equipment.findAll(criteria).stream().map(views::withoutRestrictedData).toList();
		if (rows.size() > MAX_ROWS) {
			throw new BusinessRuleException("REPORT_TOO_LARGE",
					"El reporte supera " + MAX_ROWS + " equipos; aplique mas filtros");
		}
		Instant cutOff = Instant.now(clock);
		byte[] content = renderer.render(new InventoryReport(describe(criteria), cutOff, queries.summary(criteria), rows));
		audit.record("INVENTORY_REPORT_GENERATED", "INVENTORY_REPORT", UUID.randomUUID(), Map.of(
				"format", format.name(),
				"filters", describe(criteria),
				"rows", String.valueOf(rows.size()),
				"sha256", sha256(content)));
		return new ReportFile("inventario-" + FILE_STAMP.format(cutOff) + "." + format.extension(), format, content);
	}

	/** Alcance legible del reporte; se imprime en el encabezado y se audita. */
	static String describe(EquipmentSearchCriteria criteria) {
		String filters = Stream.of(
				criteria.text() == null || criteria.text().isBlank() ? null : "texto \"" + criteria.text().strip() + "\"",
				criteria.network() == null ? null : "red " + criteria.network(),
				criteria.type() == null ? null : "tipo " + criteria.type(),
				criteria.brand() == null ? null : "marca " + criteria.brand(),
				criteria.adminStatus() == null ? null : "estado " + criteria.adminStatus(),
				criteria.usageStatus() == null ? null : "uso " + criteria.usageStatus())
				.filter(value -> value != null).collect(Collectors.joining(", "));
		return filters.isEmpty() ? "Todo el inventario (excepto eliminados)" : "Filtros: " + filters;
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}
}
