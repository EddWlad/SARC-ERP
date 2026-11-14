package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Lo que la plataforma reporta de una sesion. {@code site}/{@code charger} identifican el equipo en
 * Enerlink (ACTIVO/CARGADOR); {@code serial} lo identifica en Wallbox. La energia se guarda con 3
 * decimales (Wh), suficiente para kWh de sesiones de carga. {@code endedAt} y {@code energyKwh} son
 * nulos si la plataforma no los informo: nulo significa ausente, nunca cero. {@code sourceOffset} es el
 * desplazamiento UTC con que la fuente escribio el inicio (Feature 009): fija la fecha de atribucion y la
 * hora que se muestra. Nulo solo si no hay evidencia de origen: se usa la fecha operativa de Ecuador.
 * {@code sourceEndOffset} es el desplazamiento con que la fuente escribio el fin cuando difiere del inicio;
 * nulo = el mismo que el inicio.
 */
public record SessionReading(Instant startedAt, Instant endedAt, BigDecimal energyKwh, String site, String charger,
		String serial, String connector, ZoneOffset sourceOffset, ZoneOffset sourceEndOffset) {

	public static final int ENERGY_SCALE = 3;

	public SessionReading {
		energyKwh = energyKwh == null ? null : energyKwh.setScale(ENERGY_SCALE, RoundingMode.HALF_UP);
		site = blankToNull(site);
		charger = blankToNull(charger);
		serial = blankToNull(serial);
		connector = blankToNull(connector);
		// Solo se guarda cuando aporta: igual al del inicio o sin fin, no hay nada distinto que conservar.
		if (endedAt == null || Objects.equals(sourceEndOffset, sourceOffset)) {
			sourceEndOffset = null;
		}
	}

	/** Lectura cuyo fin se escribio con el mismo desplazamiento que el inicio. */
	public SessionReading(Instant startedAt, Instant endedAt, BigDecimal energyKwh, String site, String charger,
			String serial, String connector, ZoneOffset sourceOffset) {
		this(startedAt, endedAt, energyKwh, site, charger, serial, connector, sourceOffset, null);
	}

	/** Lectura sin desplazamiento de origen conocido. */
	public SessionReading(Instant startedAt, Instant endedAt, BigDecimal energyKwh, String site, String charger,
			String serial, String connector) {
		this(startedAt, endedAt, energyKwh, site, charger, serial, connector, null, null);
	}

	/** Desplazamiento con que se escribio el fin: el propio si difiere, si no el del inicio. */
	public ZoneOffset endOffset() {
		return sourceEndOffset != null ? sourceEndOffset : sourceOffset;
	}

	/** Fecha de inicio tal como la escribio la fuente; nula si no se conoce su desplazamiento. */
	public LocalDate sourceStartDate() {
		return startedAt == null || sourceOffset == null ? null : startedAt.atOffset(sourceOffset).toLocalDate();
	}

	/** Mismos valores; la energia se compara por magnitud, no por escala. */
	public boolean sameAs(SessionReading other) {
		return Objects.equals(startedAt, other.startedAt) && Objects.equals(endedAt, other.endedAt)
				&& sameEnergy(energyKwh, other.energyKwh) && Objects.equals(site, other.site)
				&& Objects.equals(charger, other.charger) && Objects.equals(serial, other.serial)
				&& Objects.equals(connector, other.connector) && Objects.equals(sourceOffset, other.sourceOffset)
				&& Objects.equals(sourceEndOffset, other.sourceEndOffset);
	}

	private static boolean sameEnergy(BigDecimal current, BigDecimal incoming) {
		return current == null || incoming == null ? current == incoming : current.compareTo(incoming) == 0;
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}
}
