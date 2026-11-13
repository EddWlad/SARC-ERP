package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowRevision;

interface WindowRevisionJpaRepository extends JpaRepository<WindowRevision, UUID> {

	List<WindowRevision> findByWindowIdOrderByRevisionAsc(UUID windowId);
}
