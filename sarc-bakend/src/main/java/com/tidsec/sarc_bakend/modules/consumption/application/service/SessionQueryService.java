package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionSummary;
import com.tidsec.sarc_bakend.modules.consumption.application.result.SessionDetail;
import com.tidsec.sarc_bakend.modules.consumption.application.result.SessionView;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReview;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReviewStatus;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyReviewRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionCriteria;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.error.OperationalAccessDeniedException;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.security.ConsumptionReadAccess;

/** Consultas de consumo para todos los roles: historico de sesiones e indicadores (PRD 7.3). */
@Service
@Transactional(readOnly = true)
public class SessionQueryService {

	private final EnergySessionRepository sessions;
	private final ConsumptionCalculator calculator;
	private final EquipmentDirectory directory;
	private final EnergyReviewRepository reviews;
	private final ConsumptionReadAccess access;

	SessionQueryService(EnergySessionRepository sessions, ConsumptionCalculator calculator,
			EquipmentDirectory directory, EnergyReviewRepository reviews, ConsumptionReadAccess access) {
		this.sessions = sessions;
		this.calculator = calculator;
		this.directory = directory;
		this.reviews = reviews;
		this.access = access;
	}

	public PageResult<SessionView> search(SessionCriteria criteria, PageQuery pageQuery) {
		SessionCriteria visible = criteria;
		if (!access.canReadOperational()) {
			if (criteria.deleted() || (criteria.state() != null && criteria.state() != SessionState.VALIDATED)
					|| (criteria.energyReview() != null && criteria.energyReview() != EnergyReviewStatus.APPROVED)) {
				throw new OperationalAccessDeniedException("CONSUMPTION_OPERATIONAL_ACCESS_DENIED",
						"Los filtros operativos requieren Administrador o Analista de Monitoreo");
			}
			visible = criteria.withState(SessionState.VALIDATED);
		}
		PageResult<EnergySession> page = sessions.search(visible, pageQuery);
		Map<UUID, EquipmentReference> equipment = directory.findByIds(page.content().stream()
				.map(EnergySession::getEquipmentId).filter(Objects::nonNull).distinct().toList());
		return page.map(session -> new SessionView(session,
				session.getEquipmentId() == null ? null : equipment.get(session.getEquipmentId())));
	}

	/**
	 * Detalle de una sesion (Feature 009, FR-011). Quien no lee datos operativos solo ve sesiones validadas y
	 * activas, sin baja ni revisiones; cualquier otra le responde 404, como si no existiera.
	 */
	public SessionDetail detail(UUID id) {
		EnergySession session = sessions.findById(id)
				.orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "La sesión no existe"));
		boolean operational = access.canReadOperational();
		if (!operational && (session.isDeleted() || session.getState() != SessionState.VALIDATED)) {
			throw new NotFoundException("SESSION_NOT_FOUND", "La sesión no existe");
		}
		EquipmentReference equipment = session.getEquipmentId() == null ? null
				: directory.findByIds(List.of(session.getEquipmentId())).get(session.getEquipmentId());
		return new SessionDetail(new SessionView(session, equipment),
				operational ? sessions.history(id) : List.of(), operational);
	}

	public List<EnergyReview> energyReviews(UUID id) {
		EnergySession session = sessions.findById(id)
				.orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "La sesión no existe"));
		if (access.canReadOperational()) {
			return reviews.history(id);
		}
		if (session.getState() != SessionState.VALIDATED) {
			throw new NotFoundException("SESSION_NOT_FOUND", "La sesión no existe");
		}
		if (session.getEnergyReview() != EnergyReviewStatus.APPROVED) {
			return List.of();
		}
		return reviews.history(id).stream()
				.filter(review -> review.getDecision() == EnergyReviewStatus.APPROVED
						&& review.getEnergyKwh().compareTo(session.getEnergyKwh()) == 0)
				.max(Comparator.comparingLong(EnergyReview::getSessionVersion)).stream().toList();
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public ConsumptionSummary summary(SessionCriteria criteria) {
		return calculator.summarize(criteria, access.canReadOperational());
	}
}
