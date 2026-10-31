package com.tidsec.sarc_bakend.modules.consumption.application.port;

import java.util.List;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.modules.consumption.domain.policy.SessionRules;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

/**
 * Fila interpretada; solo la observacion energetica puede acompanar una lectura valida.
 */
public record ParsedSession(int rowNumber, String externalSessionId, SessionReading reading, EquipmentLookup lookup,
		List<RowIssue> issues) {

	public ParsedSession {
		issues = List.copyOf(issues);
	}

	public boolean isValid() {
		return reading != null && issues.stream().noneMatch(SessionRules::isBlocking);
	}
}
