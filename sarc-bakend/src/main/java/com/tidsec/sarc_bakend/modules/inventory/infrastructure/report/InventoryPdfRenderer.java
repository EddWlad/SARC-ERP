package com.tidsec.sarc_bakend.modules.inventory.infrastructure.report;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.application.port.InventoryReportRenderer;
import com.tidsec.sarc_bakend.modules.inventory.application.result.EquipmentView;
import com.tidsec.sarc_bakend.modules.inventory.application.result.InventoryReport;
import com.tidsec.sarc_bakend.modules.inventory.application.result.InventorySummary;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf;
import com.tidsec.sarc_bakend.shared.report.DomainLabels;
import com.tidsec.sarc_bakend.shared.report.Kpi;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** PDF gerencial del inventario: KPIs, distribuciones, detalle de equipos y metodologia. */
@Component
class InventoryPdfRenderer implements InventoryReportRenderer {

	private final CorporateBranding branding;

	InventoryPdfRenderer(CorporateBranding branding) {
		this.branding = branding;
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.PDF;
	}

	@Override
	public byte[] render(InventoryReport report) {
		InventorySummary summary = report.summary();
		CorporatePdf pdf = CorporatePdf.create(branding, "Inventario de infraestructura de carga", report.scope(),
				report.cutOff());
		pdf.section("Resumen")
				.kpis(List.of(
						new Kpi("Equipos", String.valueOf(summary.totalEquipment())),
						new Kpi("Puntos de carga", count(summary.byType(), InfrastructureType.CHARGING_POINT)),
						new Kpi("Electrolineras", count(summary.byType(), InfrastructureType.CHARGING_STATION)),
						new Kpi("Activos", count(summary.byAdminStatus(), AdministrativeStatus.ACTIVE)),
						new Kpi("Inactivos", count(summary.byAdminStatus(), AdministrativeStatus.INACTIVE))))
				.section("Estado de uso de los puntos de carga")
				.kpis(List.of(
						new Kpi("Libres", count(summary.pointsByUsageStatus(), UsageStatus.FREE)),
						new Kpi("Bloqueados", count(summary.pointsByUsageStatus(), UsageStatus.BLOCKED)),
						new Kpi("Desconectados", count(summary.pointsByUsageStatus(), UsageStatus.DISCONNECTED))))
				.section("Distribución")
				.table(List.of("Dimensión", "Categoría", "Equipos"), distribution(summary), new float[] { 2, 3, 1 })
				.section("Conectores activos por tipo")
				.table(List.of("Tipo de conector", "Cantidad"), summary.activeConnectorsByType().entrySet().stream()
						.map(entry -> List.of(entry.getKey(), String.valueOf(entry.getValue()))).toList(),
						new float[] { 3, 1 })
				.section("Detalle de equipos (" + report.equipment().size() + ")")
				.table(List.of("Nombre", "Código", "Red", "Tipo", "Marca", "Estado", "Uso", "Conectores", "Ubicación"),
						report.equipment().stream().map(InventoryPdfRenderer::row).toList(),
						new float[] { 3, 1.4f, 1, 1.4f, 1, 1, 1.2f, 1.8f, 3 })
				.section("Notas de metodología")
				.note("Fuente: inventario maestro de SARC (RF-002). Cada equipo se cuenta una vez por su "
						+ "identificador interno, aunque cambie de nombre.")
				.note("Los totales excluyen equipos eliminados (" + summary.deletedEquipment() + " en este alcance). "
						+ "El estado de uso solo aplica a puntos de carga.")
				.note("Por seguridad el reporte no incluye UID/PUK ni URL OCPP.");
		return pdf.finish();
	}

	private static List<List<String>> distribution(InventorySummary summary) {
		return Stream.of(
				rows("Red", summary.byNetwork(), Enum::name),
				rows("Tipo", summary.byType(), DomainLabels::of),
				rows("Marca", summary.byBrand(), InventoryLabels::of))
				.flatMap(List::stream).toList();
	}

	private static <K> List<List<String>> rows(String dimension, Map<K, Long> counts, Function<K, String> label) {
		return counts.entrySet().stream()
				.map(entry -> List.of(dimension, label.apply(entry.getKey()), String.valueOf(entry.getValue())))
				.toList();
	}

	private static List<String> row(EquipmentView equipment) {
		return List.of(equipment.name(), equipment.assignedCode() == null ? "" : equipment.assignedCode(), equipment.network().name(),
				DomainLabels.of(equipment.type()), InventoryLabels.of(equipment.brand()),
				InventoryLabels.of(equipment.adminStatus()), InventoryLabels.of(equipment.usageStatus()),
				InventoryLabels.connectorCodes(equipment.connectors()), equipment.location() == null ? "" : equipment.location());
	}

	private static <K> String count(Map<K, Long> counts, K key) {
		return String.valueOf(counts.getOrDefault(key, 0L));
	}
}
