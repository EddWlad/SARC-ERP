package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class UptimeFormulaTest {

	@Test
	void acceptanceExampleGivesNinetySevenPointTwentyNine() {
		assertThat(UptimeFormula.percent(1296 + 105 + 0 + 39, 39)).isEqualByComparingTo("97.29");
	}

	@Test
	void fullyOfflineIsZeroAndNeverOfflineIsOneHundred() {
		assertThat(UptimeFormula.percent(1440, 1440)).isEqualByComparingTo("0.00");
		assertThat(UptimeFormula.percent(1440, 0)).isEqualByComparingTo("100.00");
	}

	@Test
	void roundsHalfUpToTwoDecimals() {
		// (8000 - 1) * 100 / 8000 = 99.9875 sube; 99.9849 baja.
		assertThat(UptimeFormula.percent(8000, 1)).isEqualByComparingTo("99.99");
		BigDecimal percent = UptimeFormula.percent(20000, 3);
		assertThat(percent.scale()).isEqualTo(2);
		assertThat(percent).isEqualByComparingTo("99.99");
		assertThat(UptimeFormula.percent(1600, 1)).isEqualByComparingTo("99.94");
	}

	@Test
	void occupiedAndFaultDoNotDiscount() {
		assertThat(UptimeFormula.percent(100 + 500 + 400, 0)).isEqualByComparingTo("100.00");
	}

	@Test
	void noEvaluatedTimeHasNoValue() {
		assertThat(UptimeFormula.percent(0, 0)).isNull();
	}
}
