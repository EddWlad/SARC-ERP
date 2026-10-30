package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Lo que la plataforma reporta de una sesion. {@code site}/{@code charger} identifican el equipo en
 * Enerlink (ACTIVO/CARGADOR); {@code serial} lo identifica en Wallbox. La energia se guarda con 3
 * decimales (Wh), suficiente para kWh de sesiones de carga. {@code endedAt} y {@code energyKwh} son
 * nulos si la plataforma no los informo: nulo significa ausente, nunca cero.
 */
public record SessionReading(Instant startedAt, Instant endedAt, BigDecimal energyKwh, String site, String charger,
		String serial, String connector) {

	public static final int ENERGY_SCALE = 3;

	public SessionReading {
		energyKwh = energyKwh == null ? null : energyKwh.setScale(ENERGY_SCALE, RoundingMode.HALF_UP);
		site = blankToNull(site);
		charger = blankToNull(charger);
		serial = blankToNull(serial);
		connector = blankToNull(connector);
	}

	/** Mismos valores; la energia se compara por magnitud, no por escala. */
	public boolean sameAs(SessionReading other) {
		return Objects.equals(startedAt, other.startedAt) && Objects.equals(endedAt, other.endedAt)
				&& sameEnergy(energyKwh, other.energyKwh) && Objects.equals(site, other.site)
				&& Objects.equals(charger, other.charger) && Objects.equals(serial, other.serial)
				&& Objects.equals(connector, other.connector);
	}

	private static boolean sameEnergy(BigDecimal current, BigDecimal incoming) {
		return current == null || incoming == null ? current == incoming : current.compareTo(incoming) == 0;
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}
}
