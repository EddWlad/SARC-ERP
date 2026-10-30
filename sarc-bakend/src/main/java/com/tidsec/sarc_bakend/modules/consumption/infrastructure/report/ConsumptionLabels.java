package com.tidsec.sarc_bakend.modules.consumption.infrastructure.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionSummary;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.report.DomainLabels;

/** Textos y formatos en espanol (Ecuador) para los reportes de consumo. */
final class ConsumptionLabels {

	private static final Locale ES_EC = Locale.forLanguageTag("es-EC");
	static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", ES_EC);

	private ConsumptionLabels() {
	}

	/** kWh con dos decimales y separador de miles, como se leen en el tablero. */
	static String kwh(BigDecimal value) {
		NumberFormat format = NumberFormat.getNumberInstance(ES_EC);
		format.setMinimumFractionDigits(2);
		format.setMaximumFractionDigits(2);
		return format.format(value);
	}

	static String count(long value) {
		return NumberFormat.getIntegerInstance(ES_EC).format(value);
	}

	/** Renglon de la distribucion de la energia oficial: dimension, categoria y su participacion. */
	record Share(String dimension, String category, long sessions, BigDecimal energyKwh) {
	}

	/** Las mismas distribuciones del tablero: por red, por plataforma y por tipo. */
	static List<Share> distribution(ConsumptionSummary summary) {
		List<Share> shares = new ArrayList<>();
		summary.byNetwork().forEach((network, totals) -> shares.add(
				new Share("Red", network.name(), totals.sessions(), totals.energyKwh())));
		summary.bySource().forEach((source, totals) -> shares.add(
				new Share("Plataforma", of(source), totals.sessions(), totals.energyKwh())));
		summary.byType().forEach((type, totals) -> shares.add(
				new Share("Tipo", DomainLabels.of(type), totals.sessions(), totals.energyKwh())));
		return shares;
	}

	/** Porcentaje de la energia total con un decimal; "0,0 %" si no hubo energia. */
	static String percent(BigDecimal part, BigDecimal total) {
		if (total.signum() == 0) {
			return "0,0 %";
		}
		NumberFormat format = NumberFormat.getNumberInstance(ES_EC);
		format.setMinimumFractionDigits(1);
		format.setMaximumFractionDigits(1);
		return format.format(part.multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP)) + " %";
	}

	static String of(SessionSource source) {
		return switch (source) {
			case ENERLINK -> "Enerlink";
			case WALLBOX -> "Wallbox";
		};
	}

	static String of(SessionState state) {
		return switch (state) {
			case VALIDATED -> "Validada";
			case PENDING_LINK -> "Pendiente de vincular";
			case AWAITING_APPROVAL -> "Por aprobar";
			case TEST -> "Prueba";
		};
	}

	static String name(EquipmentReference equipment) {
		return equipment == null ? "Sin vincular" : equipment.name();
	}

	static String code(EquipmentReference equipment) {
		return equipment == null || equipment.assignedCode() == null ? "" : equipment.assignedCode();
	}

	static String network(EquipmentReference equipment) {
		return equipment == null ? "" : equipment.network().name();
	}
}
