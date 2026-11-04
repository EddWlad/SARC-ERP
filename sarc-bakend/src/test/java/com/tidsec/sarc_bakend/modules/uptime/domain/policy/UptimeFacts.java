package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementFact;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Datos de las pruebas de calculo. */
final class UptimeFacts {

	static final Instant WEEK_1 = Instant.parse("2026-10-12T05:00:00Z");
	static final Instant WEEK_2 = Instant.parse("2026-10-19T05:00:00Z");
	static final UUID EQUIPMENT_A = new UUID(1, 1);
	static final UUID EQUIPMENT_B = new UUID(1, 2);

	private UptimeFacts() {
	}

	static MeasurableConnector connector(UUID equipment, String name, int ordinal) {
		return connector(equipment, name, ordinal, true, AdministrativeStatus.ACTIVE);
	}

	static MeasurableConnector connector(UUID equipment, String name, int ordinal, boolean active,
			AdministrativeStatus status) {
		return new MeasurableConnector(new UUID(equipment.getLeastSignificantBits(), ordinal), ordinal, "CCS2", "CCS2",
				active, equipment, name, name + "-code", ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION,
				"BENNY", status);
	}

	static MeasurementFact week(MeasurableConnector connector, Instant start, long evaluated, long offline) {
		return fact(connector, PeriodType.EXACT_WEEK, start, ReadingClass.MEASURED, true, false, evaluated, offline);
	}

	static MeasurementFact window(MeasurableConnector connector, PeriodType type, Instant start, long evaluated,
			long offline) {
		return fact(connector, type, start, ReadingClass.MEASURED, true, false, evaluated, offline);
	}

	static MeasurementFact special(MeasurableConnector connector, PeriodType type, Instant start, ReadingClass kind) {
		return fact(connector, type, start, kind, true, false, 0, 0);
	}

	static MeasurementFact pending(MeasurableConnector connector, PeriodType type, Instant start) {
		return fact(connector, type, start, ReadingClass.MEASURED, false, false, 1440, 0);
	}

	static MeasurementFact excluded(MeasurableConnector connector, PeriodType type, Instant start) {
		return fact(connector, type, start, ReadingClass.MEASURED, true, true, 1440, 0);
	}

	/** Uptime informado: pesa la semana completa y descuenta la parte que el porcentaje deja fuera. */
	static MeasurementFact reported(MeasurableConnector connector, Instant start, String percent) {
		long evaluated = PeriodType.EXACT_WEEK.nominal().toMinutes();
		BigDecimal down = BigDecimal.valueOf(evaluated).multiply(BigDecimal.valueOf(100).subtract(new BigDecimal(percent)))
				.divide(BigDecimal.valueOf(100));
		return new MeasurementFact(UUID.randomUUID(), connector.connectorId(), connector.equipmentId(), start,
				start.plus(PeriodType.EXACT_WEEK.nominal()), PeriodType.EXACT_WEEK, ReadingClass.MEASURED, true, false,
				evaluated, 0, 0, false, false, down);
	}

	private static MeasurementFact fact(MeasurableConnector connector, PeriodType type, Instant start,
			ReadingClass kind, boolean validated, boolean excluded, long evaluated, long offline) {
		return new MeasurementFact(UUID.randomUUID(), connector.connectorId(), connector.equipmentId(), start,
				start.plus(type.nominal()), type, kind, validated, excluded, evaluated, offline, 0, !validated, false,
				BigDecimal.valueOf(offline));
	}
}
