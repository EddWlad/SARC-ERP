package com.tidsec.sarc_bakend.modules.consumption.infrastructure.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.consumption.application.port.EquipmentLookup;
import com.tidsec.sarc_bakend.modules.consumption.application.port.ParsedSession;
import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/** Formatos tomados de los reportes reales de Wallbox y Enerlink, con datos ficticios. */
class SessionParsersTest {

	private static final OperatingZone ECUADOR = new OperatingZone(ZoneId.of("America/Guayaquil"));

	private final TabularReader reader = new TabularReader();
	private final WallboxSessionParser wallbox = new WallboxSessionParser(ECUADOR);
	private final EnerlinkSessionParser enerlink = new EnerlinkSessionParser(ECUADOR);

	@Test
	void wallboxReadsLocalTimesCommaDecimalsAndTheChargerSerial() {
		List<ParsedSession> parsed = wallbox.parse(reader.readText("""
				Serial number\tCharger\tSession id\tStart\tEnd\tEnergy (kWh)
				1172071\tMC DONALDS ORELLANA #2\t01M5E8ZNVYY5PR2Z1ET0FWN1CA\t2026-10-21 05:44:53\t2026-10-21 06:56:27\t8,07
				1172622\t\t01M5E5SQNKFJAW4AX7W0GF6PXW\t2026-10-20 23:49:12\t2026-10-21 00:57:03\t0,00
				"""));

		ParsedSession first = parsed.getFirst();
		assertThat(first.isValid()).isTrue();
		assertThat(first.externalSessionId()).isEqualTo("01M5E8ZNVYY5PR2Z1ET0FWN1CA");
		assertThat(first.reading().startedAt()).isEqualTo(Instant.parse("2026-10-21T10:44:53Z"));
		assertThat(first.reading().energyKwh()).isEqualByComparingTo("8.07");
		assertThat(first.reading().charger()).isEqualTo("MC DONALDS ORELLANA #2");
		assertThat(first.lookup()).isEqualTo(new EquipmentLookup.BySerial("1172071"));
		// Una sesion de 0 kWh valida cuenta (RF-003.11); el cargador sin nombre no impide identificarla.
		assertThat(parsed.get(1).isValid()).isTrue();
		assertThat(parsed.get(1).reading().charger()).isNull();
	}

	@Test
	void enerlinkTakesTheZoneFromTheHeaderAndIgnoresExtraColumns() {
		List<ParsedSession> parsed = enerlink.parse(reader.readText("""
				\t\tTransacciones\t\t\t23-10-2026 13:56:11 UTC-05:00
				ID\tINICIO (UTC-05:00)\tTÉRMINO (UTC-05:00)\tACTIVO\tCARGADOR\tCONECTOR\tVEHÍCULO\tSOC INICIAL (%)\tENERGIA CARGADA (kWh)\tRFID DE INICIO
				27151\t22-10-2026 23:45:12\t23-10-2026 00:14:50\tConectz\tESTACION 4\tA - PUESTO 8 - CCS1\t\t49\t22.42\tDDCBCABB
				"""));

		ParsedSession session = parsed.getFirst();
		assertThat(session.isValid()).isTrue();
		assertThat(session.reading().startedAt()).isEqualTo(Instant.parse("2026-10-23T04:45:12Z"));
		assertThat(session.reading().endedAt()).isEqualTo(Instant.parse("2026-10-23T05:14:50Z"));
		assertThat(session.reading().energyKwh()).isEqualByComparingTo("22.42");
		assertThat(session.reading().connector()).isEqualTo("A - PUESTO 8 - CCS1");
		assertThat(session.lookup())
				.isEqualTo(new EquipmentLookup.ByIdentity(new ExternalKey("Conectz", "ESTACION 4")));
	}

	@Test
	void invalidValuesAreReportedPerFieldWithoutGuessing() {
		List<ParsedSession> parsed = wallbox.parse(reader.readText("""
				Serial number\tCharger\tSession id\tStart\tEnd\tEnergy (kWh)
				\tPlaza\tS-1\t31/02/2026 10:00:00\t2026-10-21 06:56:27\t1.234
				1172071\tPlaza\tS-2\t2026-10-21 08:00:00\t2026-10-21 07:00:00\t5,5
				"""));

		assertThat(parsed.getFirst().isValid()).isFalse();
		assertThat(parsed.getFirst().issues()).extracting(RowIssue::code)
				.containsExactlyInAnyOrder("INVALID_DATE", "AMBIGUOUS_NUMBER", "EQUIPMENT_REFERENCE_REQUIRED");
		assertThat(parsed.get(1).issues()).extracting(RowIssue::code).containsExactly("END_BEFORE_START");
	}

	private static final String ENERLINK_HEADER = "ID\tINICIO\tTERMINO\tACTIVO\tCARGADOR\tCONECTOR\tENERGIA CARGADA\n";

	@Test
	void enerlinkAdmitsMissingEnergyAndEndWithoutInventingThem() {
		List<ParsedSession> parsed = enerlink.parse(reader.readText(ENERLINK_HEADER
				+ "1\t22-10-2026 10:00:00\t\tA\tB\tC1\t\n"
				+ "2\t22-10-2026 10:00:00\t   \tA\tB\tC1\t   \n"
				+ "3\t22-10-2026 10:00:00\t\tA\tB\tC1\t0\n"));

		assertThat(parsed).allMatch(ParsedSession::isValid);
		assertThat(parsed.getFirst().reading().endedAt()).isNull();
		assertThat(parsed.getFirst().reading().energyKwh()).isNull();
		assertThat(parsed.get(1).reading().energyKwh()).isNull();
		assertThat(parsed.get(2).reading().energyKwh()).isEqualByComparingTo("0");
	}

	@Test
	void toleratingAbsenceDoesNotAuthorizeUnreadableOrIncoherentValues() {
		List<ParsedSession> parsed = enerlink.parse(reader.readText(ENERLINK_HEADER
				+ "1\t\t22-10-2026 11:00:00\tA\tB\tC1\t1\n"
				+ "2\t22-10-2026 10:00:00\tmas tarde\tA\tB\tC1\t1\n"
				+ "3\t22-10-2026 10:00:00\t\tA\tB\tC1\tmucho\n"
				+ "4\t22-10-2026 10:00:00\t\tA\tB\tC1\t-1\n"
				+ "5\t22-10-2026 10:00:00\t22-10-2026 09:00:00\tA\tB\tC1\t\n"));

		assertThat(parsed.stream().map(row -> row.issues().getFirst().code())).containsExactly("DATE_REQUIRED",
				"INVALID_DATE", "INVALID_NUMBER", "NEGATIVE_ENERGY", "END_BEFORE_START");
	}

	@Test
	void anEnerlinkRowWithoutThePairIsNotInvalidAndItsKeyIsIncomplete() {
		List<ParsedSession> parsed = enerlink.parse(reader.readText(ENERLINK_HEADER
				+ "1\t22-10-2026 10:00:00\t22-10-2026 11:00:00\t\tSOLO CARGADOR\tC1\t1\n"
				+ "2\t22-10-2026 10:00:00\t22-10-2026 11:00:00\t\t\tC1\t1\n"));

		assertThat(parsed).allMatch(ParsedSession::isValid);
		assertThat(parsed).allSatisfy(row -> assertThat(row.lookup()).isInstanceOfSatisfying(
				EquipmentLookup.ByIdentity.class, lookup -> assertThat(lookup.key().isComplete()).isFalse()));
	}

	@Test
	void wallboxKeepsRequiringEnergyEndAndReference() {
		List<ParsedSession> parsed = wallbox.parse(reader.readText("""
				Serial number\tCharger\tSession id\tStart\tEnd\tEnergy (kWh)
				1172071\tPlaza\tS-1\t2026-10-21 05:44:53\t\t8,07
				1172071\tPlaza\tS-2\t2026-10-21 05:44:53\t2026-10-21 06:56:27\t
				\tPlaza\tS-3\t2026-10-21 05:44:53\t2026-10-21 06:56:27\t1,00
				"""));

		assertThat(parsed.stream().map(row -> row.issues().getFirst().code())).containsExactly("DATE_REQUIRED",
				"NUMBER_REQUIRED", "EQUIPMENT_REFERENCE_REQUIRED");
	}

	@Test
	void aFileFromTheOtherPlatformIsRejectedAsAWhole() {
		assertThatThrownBy(() -> enerlink.parse(reader.readText("""
				Serial number\tCharger\tSession id\tStart\tEnd\tEnergy (kWh)
				1172071\tPlaza\tS-1\t2026-10-21 05:44:53\t2026-10-21 06:56:27\t8,07
				"""))).extracting("code").isEqualTo("IMPORT_COLUMNS_MISSING");
	}
}
