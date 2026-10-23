package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionSummary;
import com.tidsec.sarc_bakend.modules.consumption.application.result.SessionView;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionCriteria;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Consultas de consumo para todos los roles: historico de sesiones e indicadores (PRD 7.3). */
@Service
@Transactional(readOnly = true)
public class SessionQueryService {

	private final EnergySessionRepository sessions;
	private final ConsumptionCalculator calculator;
	private final EquipmentDirectory directory;

	SessionQueryService(EnergySessionRepository sessions, ConsumptionCalculator calculator,
			EquipmentDirectory directory) {
		this.sessions = sessions;
		this.calculator = calculator;
		this.directory = directory;
	}

	public PageResult<SessionView> search(SessionCriteria criteria, PageQuery pageQuery) {
		PageResult<EnergySession> page = sessions.search(criteria, pageQuery);
		Map<UUID, EquipmentReference> equipment = directory.findByIds(page.content().stream()
				.map(EnergySession::getEquipmentId).filter(Objects::nonNull).distinct().toList());
		return page.map(session -> new SessionView(session,
				session.getEquipmentId() == null ? null : equipment.get(session.getEquipmentId())));
	}

	public ConsumptionSummary summary(SessionCriteria criteria) {
		return calculator.summarize(criteria);
	}
}
