package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.inventory.application.port.InventoryReportRenderer;
import com.tidsec.sarc_bakend.modules.inventory.application.result.EquipmentView;
import com.tidsec.sarc_bakend.modules.inventory.application.result.InventoryReport;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.catalog.ReportArchive;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * Reporte de inventario en PDF o Excel con los mismos filtros y calculos del tablero (PRD RN-G-005).
 * Nunca incluye UID/PUK ni URL OCPP. Los datos se leen en una sola instantanea (REPEATABLE READ) y
 * cada emision se archiva con su huella, para poder mostrar despues lo que se entrego (RF-RPT-08).
 */
@Service
public class InventoryReportService {

	static final String KIND = "INVENTORY";
	/** El inventario maestro es de cientos de equipos; este tope evita reportes desbordados. */
	static final int MAX_ROWS = 5000;
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
			.withZone(ZoneOffset.UTC);

	private final EquipmentRepository equipment;
	private final EquipmentQueryService queries;
	private final EquipmentViewFactory views;
	private final Map<ReportFormat, InventoryReportRenderer> renderers;
	private final ReportArchive archive;
	private final TransactionTemplate snapshot;
	private final Clock clock;

	InventoryReportService(EquipmentRepository equipment, EquipmentQueryService queries, EquipmentViewFactory views,
			List<InventoryReportRenderer> renderers, ReportArchive archive,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.equipment = equipment;
		this.queries = queries;
		this.views = views;
		this.renderers = renderers.stream()
				.collect(Collectors.toMap(InventoryReportRenderer::format, Function.identity()));
		this.archive = archive;
		this.snapshot = new TransactionTemplate(transactionManager);
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		this.snapshot.setReadOnly(true);
		this.clock = clock;
	}

	/** Lee en una instantanea, renderiza fuera de la transaccion y archiva la copia emitida. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ReportFile generate(EquipmentSearchCriteria criteria, ReportFormat format) {
		InventoryReportRenderer renderer = renderers.get(format);
		if (renderer == null) {
			throw new BusinessRuleException("REPORT_FORMAT_NOT_SUPPORTED", "Formato de reporte no disponible");
		}
		InventoryReport report = Objects.requireNonNull(snapshot.execute(status -> read(criteria)));
		ReportFile file = new ReportFile("inventario-" + FILE_STAMP.format(report.cutOff()) + "." + format.extension(),
				format, renderer.render(report));
		archive.archive(new ReportArchive.IssuedReportFile(KIND, report.scope(), file,
				Map.of("rows", String.valueOf(report.equipment().size()))));
		return file;
	}

	/** El tope se comprueba contando, antes de cargar y transformar los equipos. */
	private InventoryReport read(EquipmentSearchCriteria criteria) {
		if (equipment.count(criteria) > MAX_ROWS) {
			throw new BusinessRuleException("REPORT_TOO_LARGE",
					"El reporte supera " + MAX_ROWS + " equipos; aplique mas filtros");
		}
		List<EquipmentView> rows = equipment.findAll(criteria).stream().map(views::withoutRestrictedData).toList();
		return new InventoryReport(describe(criteria), Instant.now(clock), queries.summary(criteria), rows);
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
				.filter(Objects::nonNull).collect(Collectors.joining(", "));
		return filters.isEmpty() ? "Todo el inventario (excepto eliminados)" : "Filtros: " + filters;
	}
}
