package com.tidsec.sarc_bakend.shared.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.tidsec.sarc_bakend.shared.security.CurrentActor;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "auditDateTimeProvider")
class JpaAuditingConfig {

	@Bean
	AuditorAware<UUID> auditorProvider(CurrentActor currentActor) {
		return currentActor::id;
	}

	@Bean
	DateTimeProvider auditDateTimeProvider(Clock clock) {
		return () -> Optional.of(Instant.now(clock));
	}
}
