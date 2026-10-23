package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionRevision;

interface SessionRevisionJpaRepository extends JpaRepository<SessionRevision, UUID> {
}
