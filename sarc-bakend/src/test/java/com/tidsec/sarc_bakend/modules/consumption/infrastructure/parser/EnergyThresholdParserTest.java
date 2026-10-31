package com.tidsec.sarc_bakend.modules.consumption.infrastructure.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

class EnergyThresholdParserTest {
	@Test
	void bothSourcesNormalizeBeforeObservingAndNeverRelaxRealErrors() {
		var reader = new TabularReader();
		var zone = new OperatingZone(ZoneId.of("America/Guayaquil"));
		for (String energy : new String[] {"1000.000", "1000.0004", "1000.001", "1000.0005", "-1", "1000000000", "text"}) {
			var e = new EnerlinkSessionParser(zone).parse(reader.readText(
					"ID\tINICIO (UTC-05:00)\tTÉRMINO (UTC-05:00)\tACTIVO\tCARGADOR\tCONECTOR\tENERGIA CARGADA (kWh)\n"
					+ "1\t21-10-2026 08:00:00\t21-10-2026 09:00:00\tQA\tC1\tCCS2\t" + energy + "\n")).getFirst();
			var w = new WallboxSessionParser(zone).parse(reader.readText(
					"Serial number\tCharger\tSession id\tStart\tEnd\tEnergy (kWh)\n"
					+ "SERIAL\tQA\t1\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t" + energy.replace('.', ',') + "\n")).getFirst();
			for (var row : java.util.List.of(e, w)) {
				assertThat(row.isValid()).isEqualTo(energy.startsWith("1000."));
				if (energy.equals("1000.001") || energy.equals("1000.0005")) {
					assertThat(row.issues()).extracting(i -> i.code()).containsExactly("ENERGY_ABOVE_THRESHOLD");
					assertThat(row.reading()).isNotNull();
				}
			}
		}
	}
}
