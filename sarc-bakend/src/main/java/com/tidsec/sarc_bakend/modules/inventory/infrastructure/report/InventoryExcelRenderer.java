package com.tidsec.sarc_bakend.modules.inventory.infrastructure.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.application.port.InventoryReportRenderer;
import com.tidsec.sarc_bakend.modules.inventory.application.result.EquipmentView;
import com.tidsec.sarc_bakend.modules.inventory.application.result.InventoryReport;
import com.tidsec.sarc_bakend.modules.inventory.application.result.InventorySummary;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporateWorkbook;
import com.tidsec.sarc_bakend.shared.report.DomainLabels;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** Excel del inventario: una hoja de indicadores y otra con el detalle para verificar las cifras. */
@Component
class InventoryExcelRenderer implements InventoryReportRenderer {

	private final CorporateBranding branding;

	InventoryExcelRenderer(CorporateBranding branding) {
		this.branding = branding;
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.XLSX;
	}

	@Override
	public byte[] render(InventoryReport report) {
		return CorporateWorkbook.create(branding, report.scope(), report.cutOff())
				.sheet("Resumen", "Inventario de infraestructura de carga - indicadores",
						List.of("Dimensión", "Categoría", "Equipos"), summaryRows(report.summary()))
				.sheet("Equipos", "Inventario de infraestructura de carga - detalle",
						List.of("Nombre", "Código asignado", "Serie", "Red", "Tipo", "Marca", "Estado", "Estado de uso",
								"Conectores", "Cantidad de conectores", "Ubicación", "Id interno"),
						report.equipment().stream().map(InventoryExcelRenderer::row).toList())
				.finish();
	}

	private static List<List<Object>> summaryRows(InventorySummary summary) {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(List.of("Total", "Equipos (sin eliminados)", summary.totalEquipment()));
		rows.add(List.of("Total", "Eliminados", summary.deletedEquipment()));
		add(rows, "Red", summary.byNetwork(), Enum::name);
		add(rows, "Tipo", summary.byType(), DomainLabels::of);
		add(rows, "Marca", summary.byBrand(), InventoryLabels::of);
		add(rows, "Estado", summary.byAdminStatus(), InventoryLabels::of);
		add(rows, "Uso (puntos)", summary.pointsByUsageStatus(), InventoryLabels::of);
		add(rows, "Conectores activos", summary.activeConnectorsByType(), key -> key);
		return rows;
	}

	private static <K> void add(List<List<Object>> rows, String dimension, Map<K, Long> counts,
			Function<K, String> label) {
		counts.forEach((key, count) -> rows.add(List.of(dimension, label.apply(key), count)));
	}

	private static List<Object> row(EquipmentView equipment) {
		return List.of(equipment.name(), equipment.assignedCode() == null ? "" : equipment.assignedCode(), equipment.serial(), equipment.network().name(),
				DomainLabels.of(equipment.type()), InventoryLabels.of(equipment.brand()),
				InventoryLabels.of(equipment.adminStatus()), InventoryLabels.of(equipment.usageStatus()),
				InventoryLabels.connectorCodes(equipment.connectors()), equipment.connectors().size(),
				equipment.location() == null ? "" : equipment.location(), equipment.id().toString());
	}
}
