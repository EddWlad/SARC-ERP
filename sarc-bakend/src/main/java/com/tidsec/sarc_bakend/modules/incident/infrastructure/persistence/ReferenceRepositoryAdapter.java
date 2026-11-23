package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceRule;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceRuleId;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceVersion;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.ReferenceRepository;

@Component
class ReferenceRepositoryAdapter implements ReferenceRepository {

	private final ReferenceVersionJpaRepository versions;
	private final ReferenceRuleJpaRepository rules;

	ReferenceRepositoryAdapter(ReferenceVersionJpaRepository versions, ReferenceRuleJpaRepository rules) {
		this.versions = versions;
		this.rules = rules;
	}

	@Override
	public List<ReferenceVersion> findVersionsNewestFirst() {
		return versions.findAllByOrderByEffectiveFromDesc();
	}

	@Override
	public List<ReferenceRule> findRules(UUID versionId) {
		return rules.findByVersionId(versionId);
	}

	@Override
	public Optional<ReferenceVersion> findVersion(UUID versionId) {
		return versions.findById(versionId);
	}
}

interface ReferenceVersionJpaRepository extends JpaRepository<ReferenceVersion, UUID> {

	List<ReferenceVersion> findAllByOrderByEffectiveFromDesc();
}

interface ReferenceRuleJpaRepository extends JpaRepository<ReferenceRule, ReferenceRuleId> {

	List<ReferenceRule> findByVersionId(UUID versionId);
}
