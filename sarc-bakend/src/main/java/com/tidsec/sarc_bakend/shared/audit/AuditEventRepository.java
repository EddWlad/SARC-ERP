package com.tidsec.sarc_bakend.shared.audit;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}
