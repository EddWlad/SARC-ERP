package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Period;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Preset;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/** Periodos del dashboard con el fin exclusivo (FR-031). */
class PeriodPresetsTest {

	private static final LocalDate TODAY = LocalDate.parse("2026-10-20");

	private static Period resolve(Preset preset, String month, String from, String to) {
		return PeriodPresets.resolve(preset, month == null ? null : YearMonth.parse(month),
				from == null ? null : LocalDate.parse(from), to == null ? null : LocalDate.parse(to), TODAY);
	}

	@Test
	void fortnightsOfAMonthWith31DaysCoverEveryDay() {
		Period first = resolve(Preset.FIRST_HALF, "2026-10", null, null);
		Period second = resolve(Preset.SECOND_HALF, "2026-10", null, null);

		assertThat(first.from()).isEqualTo(LocalDate.parse("2026-10-01"));
		assertThat(first.to()).isEqualTo(LocalDate.parse("2026-10-16"));
		assertThat(second.from()).isEqualTo(LocalDate.parse("2026-10-16"));
		assertThat(second.to()).isEqualTo(LocalDate.parse("2026-11-01"));
		assertThat(first.days() + second.days()).isEqualTo(31);
	}

	@Test
	void fortnightsOfFebruaryEndOnTheLastDayOfTheMonth() {
		Period second = resolve(Preset.SECOND_HALF, "2026-02", null, null);
		Period leapSecond = resolve(Preset.SECOND_HALF, "2028-02", null, null);

		assertThat(second.to()).isEqualTo(LocalDate.parse("2026-03-01"));
		assertThat(second.days()).isEqualTo(13);
		assertThat(leapSecond.days()).isEqualTo(14);
	}

	@Test
	void theLast15DaysAreTodayAndTheFourteenBefore() {
		Period period = resolve(Preset.LAST_15_DAYS, null, null, null);

		assertThat(period.from()).isEqualTo(LocalDate.parse("2026-10-06"));
		assertThat(period.to()).isEqualTo(LocalDate.parse("2026-10-21"));
		assertThat(period.days()).isEqualTo(15);
	}

	@Test
	void aCustomRangeOf366DaysIsAcceptedAndOf367IsRejected() {
		Period accepted = resolve(Preset.CUSTOM, null, "2025-10-01", "2026-10-02");

		assertThat(accepted.days()).isEqualTo(366);
		assertThatThrownBy(() -> resolve(Preset.CUSTOM, null, "2025-10-01", "2026-10-03"))
				.isInstanceOfSatisfying(BusinessRuleException.class,
						ex -> assertThat(ex.code()).isEqualTo("PERIOD_TOO_LONG"));
	}

	@Test
	void aCustomRangeNeedsBothDaysAndAtLeastOne() {
		assertThatThrownBy(() -> resolve(Preset.CUSTOM, null, "2026-10-01", null))
				.isInstanceOfSatisfying(BusinessRuleException.class,
						ex -> assertThat(ex.code()).isEqualTo("PERIOD_INVALID"));
		assertThatThrownBy(() -> resolve(Preset.CUSTOM, null, "2026-10-05", "2026-10-05"))
				.isInstanceOfSatisfying(BusinessRuleException.class,
						ex -> assertThat(ex.code()).isEqualTo("PERIOD_INVALID"));
	}

	@Test
	void withoutAnythingItIsTheCurrentFortnightCutAtToday() {
		Period firstHalfToday = PeriodPresets.resolve(null, null, null, null, LocalDate.parse("2026-10-15"));
		Period secondHalfToday = PeriodPresets.resolve(null, null, null, null, LocalDate.parse("2026-10-16"));

		assertThat(firstHalfToday.preset()).isEqualTo(Preset.FIRST_HALF);
		assertThat(firstHalfToday.from()).isEqualTo(LocalDate.parse("2026-10-01"));
		assertThat(secondHalfToday.preset()).isEqualTo(Preset.SECOND_HALF);
		assertThat(secondHalfToday.to()).isEqualTo(LocalDate.parse("2026-11-01"));
	}

	@Test
	void aFortnightWithoutMonthUsesTheMonthOfToday() {
		Period period = resolve(Preset.FIRST_HALF, null, null, null);

		assertThat(period.from()).isEqualTo(LocalDate.parse("2026-10-01"));
	}

	@Test
	void fromAndToWithoutAPresetMeanACustomRange() {
		Period period = resolve(null, null, "2026-10-03", "2026-10-09");

		assertThat(period.preset()).isEqualTo(Preset.CUSTOM);
		assertThat(period.days()).isEqualTo(6);
	}
}
