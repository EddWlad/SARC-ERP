package com.tidsec.sarc_bakend.modules.consumption.infrastructure.report;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;

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

	static String of(SessionState state) {
		return switch (state) {
			case VALIDATED -> "Validada";
			case PENDING_LINK -> "Pendiente de vincular";
			case TEST -> "Prueba";
		};
	}

	static String name(EquipmentReference equipment) {
		return equipment == null ? "Sin vincular" : equipment.name();
	}

	static String code(EquipmentReference equipment) {
		return equipment == null ? "" : equipment.assignedCode();
	}

	static String network(EquipmentReference equipment) {
		return equipment == null ? "" : equipment.network().name();
	}
}
