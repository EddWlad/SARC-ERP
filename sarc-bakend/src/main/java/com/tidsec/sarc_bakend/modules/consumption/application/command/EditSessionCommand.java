package com.tidsec.sarc_bakend.modules.consumption.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;

/**
 * Edicion individual de una sesion (Feature 009, FR-012). Cada campo es opcional: {@code null} conserva el
 * dato; {@code Optional.empty()} lo deja sin informar (fin, energia o equipo); un valor lo reemplaza. Las
 * fechas traen su desplazamiento, que pasa a ser el de origen de ese extremo.
 */
public record EditSessionCommand(UUID id, long version, String reason, Optional<OffsetDateTime> startedAt,
		Optional<OffsetDateTime> endedAt, Optional<BigDecimal> energyKwh, Optional<UUID> equipmentId) {

	/** Lectura resultante. Los datos del cargador que informo la fuente no cambian. */
	public SessionReading applyTo(SessionReading current) {
		Instant start = current.startedAt();
		ZoneOffset startOffset = current.sourceOffset();
		if (startedAt != null) {
			OffsetDateTime value = startedAt.orElse(null);
			start = value == null ? null : value.toInstant();
			startOffset = value == null ? startOffset : value.getOffset();
		}
		// Un fin que no se edita conserva el desplazamiento con que se escribio.
		Instant end = current.endedAt();
		ZoneOffset endOffset = current.endOffset();
		if (endedAt != null) {
			OffsetDateTime value = endedAt.orElse(null);
			end = value == null ? null : value.toInstant();
			endOffset = value == null ? null : value.getOffset();
		}
		BigDecimal energy = energyKwh == null ? current.energyKwh() : energyKwh.orElse(null);
		return new SessionReading(start, end, energy, current.site(), current.charger(), current.serial(),
				current.connector(), startOffset, endOffset);
	}
}
