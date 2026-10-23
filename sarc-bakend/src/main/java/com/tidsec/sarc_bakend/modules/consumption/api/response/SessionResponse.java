package com.tidsec.sarc_bakend.modules.consumption.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.application.result.SessionView;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sesion del historico (PRD RF-003.11): ID de la fuente, procedencia, equipo, inicio/termino, dia en
 * que se cuenta y kWh. {@code site}/{@code charger}/{@code serial}/{@code connector} son tal como
 * los reporto la plataforma.
 */
public record SessionResponse(
		UUID id,
		SessionSource source,
		String externalSessionId,
		Instant startedAt,
		Instant endedAt,
		LocalDate startDate,
		BigDecimal energyKwh,
		SessionState state,
		@Schema(nullable = true) EquipmentRefResponse equipment,
		@Schema(nullable = true) String site,
		@Schema(nullable = true) String charger,
		@Schema(nullable = true) String serial,
		@Schema(nullable = true) String connector,
		int revision,
		UUID importBatchId,
		long version) {

	public static SessionResponse from(SessionView view) {
		EnergySession session = view.session();
		SessionReading reading = session.reading();
		return new SessionResponse(session.getId(), session.getSource(), session.getExternalSessionId(),
				session.getStartedAt(), session.getEndedAt(), session.getStartDate(), session.getEnergyKwh(),
				session.getState(), EquipmentRefResponse.from(view.equipment()), reading.site(), reading.charger(),
				reading.serial(), reading.connector(), session.getRevision(), session.getImportBatchId(),
				session.getVersion());
	}
}
