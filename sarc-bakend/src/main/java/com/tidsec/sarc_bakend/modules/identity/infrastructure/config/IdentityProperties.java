package com.tidsec.sarc_bakend.modules.identity.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("sarc.security")
record IdentityProperties(
		@DefaultValue("12") int bcryptStrength,
		@DefaultValue("5") int maxFailedLoginAttempts,
		@DefaultValue("15m") Duration lockoutDuration) {
}
