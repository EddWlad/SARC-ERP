package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceRule;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceVersion;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.ReferenceRepository;

/**
 * Catalogo de referencias de tiempo por version (FR-051a). Cada episodio usa la version vigente en su T0 y sus
 * juicios se calculan siempre con ella, de modo que un cambio futuro no altera episodios ni informes anteriores.
 */
@Service
@Transactional(readOnly = true)
public class ReferenceCatalog {

	private final ReferenceRepository repository;

	ReferenceCatalog(ReferenceRepository repository) {
		this.repository = repository;
	}

	/** La ultima version con vigencia anterior o igual a T0. */
	public ReferenceVersion versionAt(Instant t0) {
		return repository.findVersionsNewestFirst().stream()
				.filter(version -> !version.getEffectiveFrom().isAfter(t0)).findFirst()
				.orElseThrow(() -> new IllegalStateException("No hay una version de referencias vigente en " + t0));
	}

	/** La regla del tipo en esa version; vacio si el tipo no tiene referencia aprobada. */
	public Optional<ReferenceRule> ruleFor(ReferenceVersion version, IncidentType type) {
		return ruleFor(version.getId(), type);
	}

	public Optional<ReferenceRule> ruleFor(UUID versionId, IncidentType type) {
		return repository.findRules(versionId).stream().filter(rule -> rule.getIncidentType() == type).findFirst();
	}

	/** Prioridad propuesta para el tipo en esa version; Media si el tipo no tiene regla (FR-034). */
	public Priority proposedPriority(ReferenceVersion version, IncidentType type) {
		return ruleFor(version, type).map(ReferenceRule::getProposedPriority).orElse(Priority.MEDIUM);
	}

	public Optional<ReferenceVersion> version(UUID versionId) {
		return repository.findVersion(versionId);
	}
}
