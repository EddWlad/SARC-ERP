package com.tidsec.sarc_bakend.shared.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DecimalTextTest {

	@Test
	void commaDecimalIsNeverReadAsThousands() {
		// CA-003-02: 35,41 son 35.41 kWh, no 3541, sea cual sea el separador declarado.
		assertThat(DecimalText.parse("35,41", ',')).isEqualByComparingTo("35.41");
		assertThat(DecimalText.parse("35,41", '.')).isEqualByComparingTo("35.41");
		assertThat(DecimalText.parse("35.41", ',')).isEqualByComparingTo("35.41");
	}

	@Test
	void readsGroupedThousandsWithEitherConvention() {
		assertThat(DecimalText.parse("1.234,56", ',')).isEqualByComparingTo("1234.56");
		assertThat(DecimalText.parse("1,234.56", '.')).isEqualByComparingTo("1234.56");
		assertThat(DecimalText.parse("1.234.567", ',')).isEqualByComparingTo("1234567");
	}

	@Test
	void threeDecimalsWithTheUndeclaredSeparatorAreAmbiguous() {
		assertThatThrownBy(() -> DecimalText.parse("1.234", ','))
				.isInstanceOf(InvalidValueException.class).extracting("code").isEqualTo("AMBIGUOUS_NUMBER");
		assertThat(DecimalText.parse("1.234", '.')).isEqualByComparingTo("1.234");
		assertThat(DecimalText.parse("1,234", ',')).isEqualByComparingTo("1.234");
	}

	@Test
	void acceptsIntegersZeroAndNegatives() {
		assertThat(DecimalText.parse("0", ',')).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(DecimalText.parse(" 22 ", '.')).isEqualByComparingTo("22");
		assertThat(DecimalText.parse("-2,5", ',')).isEqualByComparingTo("-2.5");
	}

	@Test
	void rejectsEmptyAndNonNumericValues() {
		assertThatThrownBy(() -> DecimalText.parse(" ", ',')).extracting("code").isEqualTo("NUMBER_REQUIRED");
		assertThatThrownBy(() -> DecimalText.parse("8 kWh", ',')).extracting("code").isEqualTo("INVALID_NUMBER");
		assertThatThrownBy(() -> DecimalText.parse("1,2,3", ',')).extracting("code").isEqualTo("AMBIGUOUS_NUMBER");
		assertThatThrownBy(() -> DecimalText.parse("5,", ',')).extracting("code").isEqualTo("AMBIGUOUS_NUMBER");
	}
}
