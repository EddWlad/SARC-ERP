package com.tidsec.sarc_bakend.modules.incident.infrastructure.config;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.application.port.IncidentSettings;

/** Entrega {@link IncidentProperties} a los casos de uso a traves del puerto {@link IncidentSettings}. */
@Component
class IncidentSettingsAdapter implements IncidentSettings {

	private final IncidentProperties properties;

	IncidentSettingsAdapter(IncidentProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<String> expectedNumberingEnvironment() {
		return properties.numbering().expectedEnvironment();
	}

	@Override
	public long evidenceMaxBytes() {
		return properties.evidence().maxBytes();
	}
}
