package com.tidsec.sarc_bakend.modules.uptime.infrastructure.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.uptime.application.port.MeasurementBlockParser;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class EnerlinkBlockParserTest {

	private final EnerlinkBlockParser parser = new EnerlinkBlockParser();

	@Test
	void readsTheFourStatesInAnyOrder() {
		MeasurementBlockParser.Block block = parser.parse("""
				Fuera de línea: 39 min
				Con falla: 0 min
				Ocupado: 105 min
				Disponible: 1296 min
				""");

		assertThat(block.available()).isEqualByComparingTo("1296");
		assertThat(block.occupied()).isEqualByComparingTo("105");
		assertThat(block.fault()).isEqualByComparingTo("0");
		assertThat(block.offline()).isEqualByComparingTo("39");
		assertThat(block.reportedPercent()).isNull();
	}

	@Test
	void ignoresCaseAccentsAndRepeatedSpaces() {
		MeasurementBlockParser.Block block = parser.parse("DISPONIBLE\n1296\nocupado   105\nCON  FALLA 0\nfuera de linea 39");

		assertThat(block.offline()).isEqualByComparingTo("39");
	}

	@Test
	void readsTheOptionalPercent() {
		MeasurementBlockParser.Block block = parser
				.parse("Uptime 97,29 %\nDisponible 1296\nOcupado 105\nCon falla 0\nFuera de línea 39");

		assertThat(block.reportedPercent()).isEqualByComparingTo(new BigDecimal("97.29"));
	}

	@Test
	void keepsDecimalMinutesForTheObservation() {
		MeasurementBlockParser.Block block = parser.parse("Disponible 1296,5\nOcupado 105\nCon falla 0\nFuera de línea 39");

		assertThat(block.available()).isEqualByComparingTo("1296.5");
	}

	@Test
	void rejectsIncompleteNamingDetectedAndMissing() {
		assertThatThrownBy(() -> parser.parse("Disponible 1296\nOcupado 105"))
				.isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
					assertThat(ex.code()).isEqualTo("UPTIME_BLOCK_AMBIGUOUS");
					assertThat(ex.properties().get("detected")).isEqualTo(List.of("Disponible", "Ocupado"));
					assertThat(ex.properties().get("missing")).isEqualTo(List.of("Con falla", "Fuera de línea"));
				});
	}

	@Test
	void rejectsRepeatedLabel() {
		assertAmbiguous("Disponible 1296\nDisponible 1200\nOcupado 105\nCon falla 0\nFuera de línea 39");
	}

	@Test
	void rejectsLabelWithoutValue() {
		assertAmbiguous("Disponible\nOcupado 105\nCon falla 0\nFuera de línea 39");
	}

	@Test
	void rejectsTwoValuesForOneLabel() {
		assertAmbiguous("Disponible 1296 1200\nOcupado 105\nCon falla 0\nFuera de línea 39");
	}

	@Test
	void rejectsAnotherUnitInsteadOfGuessing() {
		assertAmbiguous("Disponible 21 h\nOcupado 105\nCon falla 0\nFuera de línea 39");
	}

	@Test
	void rejectsTwoPercents() {
		assertAmbiguous("Disponible 90 %\nOcupado 7 %\nCon falla 0\nFuera de línea 39");
	}

	private void assertAmbiguous(String text) {
		assertThatThrownBy(() -> parser.parse(text)).isInstanceOfSatisfying(BusinessRuleException.class,
				ex -> assertThat(ex.code()).isEqualTo("UPTIME_BLOCK_AMBIGUOUS"));
	}
}
