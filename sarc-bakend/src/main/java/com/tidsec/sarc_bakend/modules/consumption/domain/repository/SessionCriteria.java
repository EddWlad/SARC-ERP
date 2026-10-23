package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.time.LocalDate;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/**
 * Filtro de sesiones. El periodo es por dia local de inicio, desde inclusivo y hasta exclusivo
 * (PRD 3.2). Un campo nulo no filtra; {@code text} busca en el ID de la sesion y en los nombres que
 * reporta la fuente.
 */
public record SessionCriteria(LocalDate from, LocalDate to, ChargingNetwork network, InfrastructureType type,
		SessionSource source, UUID equipmentId, SessionState state, String text) {

	public SessionCriteria withState(SessionState newState) {
		return new SessionCriteria(from, to, network, type, source, equipmentId, newState, text);
	}
}
