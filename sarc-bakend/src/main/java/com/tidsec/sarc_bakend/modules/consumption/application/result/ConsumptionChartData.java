package com.tidsec.sarc_bakend.modules.consumption.application.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyTotals;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/**
 * Las seis representaciones del informe, derivadas del resumen ya autorizado: no consultan ni
 * recalculan nada, asi que no pueden contradecir a las tablas del mismo documento.
 */
public record ConsumptionChartData(Series dailyEnergy, Series dailySessions, Series top, Series byNetwork,
		Series bySource, Series byType) {

	/** Textos del documento; viven con el renderer, no con los datos. */
	public interface Labels {

		String day(LocalDate date);

		String equipment(RankedEquipment item);

		String source(SessionSource source);

		String type(InfrastructureType type);
	}

	/** Un valor nulo es energia desconocida, distinto de cero: nunca se imputa. */
	public record Point(String label, BigDecimal value, long sessions) {

		public boolean unknown() {
			return value == null;
		}

		public boolean withoutSessions() {
			return sessions == 0;
		}
	}

	public record Series(String title, String unit, List<Point> points) {

		public boolean hasValues() {
			return points.stream().anyMatch(point -> !point.unknown());
		}
	}

	public static ConsumptionChartData from(ConsumptionSummary summary, Labels labels) {
		String kwh = "kWh";
		return new ConsumptionChartData(
				new Series("Energía diaria", kwh, summary.daily().stream()
						.map(day -> new Point(labels.day(day.date()), day.energyKwh(), day.sessions())).toList()),
				new Series("Sesiones diarias", "sesiones", summary.daily().stream()
						.map(day -> new Point(labels.day(day.date()), BigDecimal.valueOf(day.sessions()), day.sessions()))
						.toList()),
				new Series("Top 5 equipos por energía", kwh, summary.top().stream()
						.map(item -> new Point(labels.equipment(item), item.energyKwh(), item.sessions())).toList()),
				new Series("Energía por red", kwh, summary.byNetwork().entrySet().stream()
						.sorted(Map.Entry.comparingByKey())
						.map(entry -> share(entry.getKey().name(), entry.getValue())).toList()),
				new Series("Energía por plataforma", kwh, summary.bySource().entrySet().stream()
						.sorted(Map.Entry.comparingByKey())
						.map(entry -> share(labels.source(entry.getKey()), entry.getValue())).toList()),
				new Series("Energía por tipo", kwh, summary.byType().entrySet().stream()
						.sorted(Map.Entry.comparingByKey())
						.map(entry -> share(labels.type(entry.getKey()), entry.getValue())).toList()));
	}

	private static Point share(String label, EnergyTotals totals) {
		return new Point(label, totals.energyKwh(), totals.sessions());
	}
}
