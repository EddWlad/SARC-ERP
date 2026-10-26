package com.tidsec.sarc_bakend.modules.consumption.application.port;

import java.util.List;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

/**
 * Fila interpretada por un {@link SessionReportParser}. Si tiene observaciones, {@code reading} es
 * nulo: la fila no se pudo interpretar sin adivinar y queda invalida.
 */
public record ParsedSession(int rowNumber, String externalSessionId, SessionReading reading, EquipmentLookup lookup,
		List<RowIssue> issues) {

	public ParsedSession {
		issues = List.copyOf(issues);
	}

	public boolean isValid() {
		return reading != null && issues.isEmpty();
	}
}
