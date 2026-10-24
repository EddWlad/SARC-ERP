package com.tidsec.sarc_bakend.modules.identity.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tidsec.sarc_bakend.modules.identity.domain.policy.LoginPolicy;
import com.tidsec.sarc_bakend.modules.identity.domain.policy.LoginThrottlePolicy;

@Configuration(proxyBeanMethods = false)
class IdentityConfig {

	@Bean
	PasswordEncoder passwordEncoder(IdentityProperties properties) {
		return new BCryptPasswordEncoder(properties.bcryptStrength());
	}

	@Bean
	LoginPolicy loginPolicy(IdentityProperties properties) {
		return new LoginPolicy(properties.maxFailedLoginAttempts(), properties.lockoutDuration());
	}

	@Bean
	LoginThrottlePolicy loginThrottlePolicy(IdentityProperties properties) {
		return new LoginThrottlePolicy(properties.maxFailedLoginsPerClient(), properties.clientThrottleWindow());
	}
}
