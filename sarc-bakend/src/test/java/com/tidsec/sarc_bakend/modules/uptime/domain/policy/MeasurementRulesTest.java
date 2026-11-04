package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Observation;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.UptimeBasis;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.MeasurementRules.Assessment;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.MeasurementRules.Values;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class MeasurementRulesTest {

	private static final long DAY = 1440;

	private static Values values(String available, String occupied, String fault, String offline, String percent) {
		return new Values(new BigDecimal(available), new BigDecimal(occupied), new BigDecimal(fault),
				new BigDecimal(offline), percent == null ? null : new BigDecimal(percent));
	}

	private static Assessment day(Values values) {
		return MeasurementRules.assess(values, DAY, PeriodType.ROLLING_24H);
	}

	@Test
	void cleanMeasurementHasNoObservationsAndKeepsMinutes() {
		Assessment result = day(values("1296", "105", "0", "39", "97.29"));

		assertThat(result.observations()).isEmpty();
		assertThat(result.available()).isEqualTo(1296);
		assertThat(result.offline()).isEqualTo(39);
		assertThat(result.percent()).isEqualByComparingTo("97.29");
		assertThat(result.validatable()).isTrue();
	}

	@ParameterizedTest
	@CsvSource(textBlock = """
			1440, ''
			1439, ''
			1441, ''
			1438, SUM_BELOW_DURATION
			1442, SUM_ABOVE_DURATION
			""")
	void toleratesOneMinuteAgainstRealDuration(String available, String expected) {
		Assessment result = day(values(available, "0", "0", "0", null));

		if (expected == null || expected.isEmpty()) {
			assertThat(result.observations()).isEmpty();
		}
		else {
			assertThat(result.observations()).containsExactly(Observation.valueOf(expected));
		}
	}

	@Test
	void sumZeroIsNotValidatableAndHasNoPercent() {
		Assessment result = day(values("0", "0", "0", "0", null));

		assertThat(result.observations()).containsExactly(Observation.SUM_ZERO);
		assertThat(result.validatable()).isFalse();
		assertThat(result.percent()).isNull();
	}

	@Test
	void percentDifferenceUpToOneHundredthIsCoherent() {
		assertThat(day(values("1296", "105", "0", "39", "97.30")).observations()).isEmpty();
		assertThat(day(values("1296", "105", "0", "39", "97.3001")).observations())
				.containsExactly(Observation.PERCENT_MISMATCH);
	}

	@Test
	void percentMismatchIsValidatableAndKeepsPercent() {
		Assessment result = day(values("1296", "105", "0", "39", "90"));

		assertThat(result.validatable()).isTrue();
		assertThat(result.percent()).isEqualByComparingTo("97.29");
	}

	@Test
	void reportedPercentOutOfRangeIsNotValidatable() {
		Assessment result = day(values("1296", "105", "0", "39", "9907"));

		assertThat(result.observations()).containsExactly(Observation.PERCENT_OUT_OF_RANGE);
		assertThat(result.validatable()).isFalse();
		assertThat(result.percent()).isNull();
	}

	@Test
	void percentBeyondColumnIsCaptureError() {
		assertThatThrownBy(() -> day(values("1440", "0", "0", "0", "123456789")))
				.isInstanceOf(BusinessRuleException.class).hasFieldOrPropertyWithValue("code", "UPTIME_PERCENT_TOO_LARGE");
	}

	@Test
	void eightySixThousandMinutesInOneWeekExceedsDuration() {
		Assessment result = MeasurementRules.assess(values("0", "0", "0", "86400", null), 10080,
				PeriodType.EXACT_WEEK);

		assertThat(result.observations()).containsExactly(Observation.SUM_ABOVE_DURATION);
		assertThat(result.validatable()).isFalse();
	}

	@Test
	void negativeValueKeepsNoMinutes() {
		Assessment result = day(values("-1", "1441", "0", "0", null));

		assertThat(result.observations()).containsExactly(Observation.DURATION_INVALID);
		assertThat(result.available()).isNull();
		assertThat(result.occupied()).isEqualTo(1441);
		assertThat(result.percent()).isNull();
	}

	@Test
	void fractionalValueIsObservedNotRounded() {
		Assessment result = day(values("9.57", "0", "0", "0", null));

		assertThat(result.observations()).containsExactly(Observation.DURATION_INVALID);
		assertThat(result.available()).isNull();
		assertThat(result.needsSourceValues()).isTrue();
	}

	@Test
	void integerWrittenWithDecimalsIsStillInteger() {
		Assessment result = day(values("1440.00", "0", "0", "0", null));

		assertThat(result.observations()).isEmpty();
		assertThat(result.available()).isEqualTo(1440);
		assertThat(result.needsSourceValues()).isFalse();
	}

	@Test
	void rollingWindowOfIrregularDurationIsNotValidatable() {
		Assessment result = MeasurementRules.assess(values("1500", "0", "0", "0", null), 1500,
				PeriodType.ROLLING_24H);

		assertThat(result.observations()).containsExactly(Observation.WINDOW_DURATION_MISMATCH);
		assertThat(result.validatable()).isFalse();
	}

	@Test
	void rollingWindowToleratesOneMinute() {
		assertThat(MeasurementRules.assess(values("1441", "0", "0", "0", null), 1441, PeriodType.ROLLING_24H)
				.observations()).isEmpty();
	}

	@Test
	void sumToleranceIsOneMinuteUpToSevenDaysAndProportionalBeyond() {
		assertThat(MeasurementRules.sumTolerance(1_440)).isEqualTo(1);
		assertThat(MeasurementRules.sumTolerance(10_080)).isEqualTo(1);
		assertThat(MeasurementRules.sumTolerance(43_200)).isEqualTo(9);
		assertThat(MeasurementRules.sumTolerance(46_080)).isEqualTo(10);
	}

	@Test
	void thirtyDayWindowAdmitsTheRoundingOfEnerlinkHours() {
		long thirtyDays = 43_200;

		assertThat(MeasurementRules.assess(values("43209", "0", "0", "0", null), thirtyDays, PeriodType.ROLLING_30D)
				.observations()).isEmpty();
		assertThat(MeasurementRules.assess(values("43210", "0", "0", "0", null), thirtyDays, PeriodType.ROLLING_30D)
				.observations()).containsExactly(Observation.SUM_ABOVE_DURATION);
	}

	@Test
	void validatableDependsOnEveryCode() {
		Assessment result = day(values("1000", "0", "0", "39", "90"));

		assertThat(result.observations()).contains(Observation.SUM_BELOW_DURATION, Observation.PERCENT_MISMATCH);
		assertThat(result.validatable()).isTrue();
		assertThat(MeasurementRules.assess(values("1000", "0", "0", "39", "9907"), DAY, PeriodType.ROLLING_24H)
				.validatable()).isFalse();
	}

	private static Values reported(String available, String fault, String offline, String percent) {
		return new Values(new BigDecimal(available), null, new BigDecimal(fault), new BigDecimal(offline),
				percent == null ? null : new BigDecimal(percent), UptimeBasis.REPORTED_PERCENT);
	}

	@Test
	void reportedUptimeIsTakenAsItCameWithoutOccupied() {
		Assessment result = MeasurementRules.assess(reported("9990", "0", "61", "99.4"), 10080, PeriodType.ROLLING_7D);

		assertThat(result.observations()).isEmpty();
		assertThat(result.basis()).isEqualTo(UptimeBasis.REPORTED_PERCENT);
		assertThat(result.occupied()).isNull();
		assertThat(result.percent()).isEqualByComparingTo("99.40");
		assertThat(result.validatable()).isTrue();
	}

	@Test
	void reportedUptimeStatesCannotExceedTheWeek() {
		Assessment result = MeasurementRules.assess(reported("10080", "0", "61", "99.4"), 10080, PeriodType.ROLLING_7D);

		assertThat(result.observations()).containsExactly(Observation.SUM_ABOVE_DURATION);
		assertThat(result.percent()).isNull();
	}

	@Test
	void reportedUptimeOutOfRangeAndRetiredRowsAreObserved() {
		assertThat(MeasurementRules.assess(reported("100", "0", "0", "100.5"), 10080, PeriodType.ROLLING_7D)
				.observations()).contains(Observation.PERCENT_OUT_OF_RANGE);
		assertThat(MeasurementRules.assess(reported("0", "0", "0", "0"), 10080, PeriodType.ROLLING_7D).observations())
				.containsExactly(Observation.SUM_ZERO);
	}

	@Test
	void reportedBasisRequiresThePercent() {
		assertThatThrownBy(() -> MeasurementRules.assess(reported("9990", "0", "61", null), 10080,
				PeriodType.ROLLING_7D)).isInstanceOf(BusinessRuleException.class).extracting("code")
				.isEqualTo("UPTIME_PERCENT_REQUIRED");
	}
}
