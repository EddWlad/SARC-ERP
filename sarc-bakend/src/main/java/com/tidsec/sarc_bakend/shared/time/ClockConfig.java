package com.tidsec.sarc_bakend.shared.time;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	OperatingZone operatingZone(@Value("${sarc.time-zone}") String zone) {
		return new OperatingZone(ZoneId.of(zone));
	}
}
