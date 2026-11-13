package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.BlockingDay;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;

/** Lo que dejan sin cubrir las filas pendientes (FR-045): una sola regla para el panel y la disponibilidad. */
class CoverageBlocksTest {

	private static final LocalDate OCT_5 = LocalDate.parse("2026-10-05");
	private static final LocalDate OCT_6 = LocalDate.parse("2026-10-06");
	private static final LocalDate OCT_7 = LocalDate.parse("2026-10-07");
	private static final UUID POINT = UUID.randomUUID();

	private static BlockingDay pending(LocalDate day) {
		return new BlockingDay(RowClassification.PENDING_LINK, null, day);
	}

	private static BlockingDay conflict(UUID point, LocalDate day) {
		return new BlockingDay(RowClassification.CONFLICT, point, day);
	}

	@Test
	void aRowToLinkBlocksTheDayForTheWholeNetworkAndRowsAreCountedPerDay() {
		CoverageBlocks blocks = new CoverageBlocks(List.of(pending(OCT_5), pending(OCT_5), pending(OCT_6)), OCT_5, OCT_7);

		assertThat(blocks.networkRowsByDay()).containsEntry(OCT_5, 2).containsEntry(OCT_6, 1).hasSize(2);
		assertThat(blocks.daysOf(POINT)).isEmpty();
	}

	@Test
	void aConflictRowOnlyBlocksTheDaysOfItsOwnPoint() {
		CoverageBlocks blocks = new CoverageBlocks(List.of(conflict(POINT, OCT_5), conflict(POINT, OCT_6)), OCT_5, OCT_7);

		assertThat(blocks.daysOf(POINT)).containsExactlyInAnyOrder(OCT_5, OCT_6);
		assertThat(blocks.daysOf(UUID.randomUUID())).isEmpty();
		assertThat(blocks.networkRowsByDay()).isEmpty();
	}

	@Test
	void aConflictRowWithoutAKnownPointIsTreatedAsToLinkSoNoUnresolvedRowLeavesADayCovered() {
		CoverageBlocks blocks = new CoverageBlocks(List.of(conflict(null, OCT_6)), OCT_5, OCT_7);

		assertThat(blocks.networkRowsByDay()).containsOnlyKeys(OCT_6);
	}

	@Test
	void daysOutsideTheRangeAreIgnoredAndBothLimitsAreIncluded() {
		CoverageBlocks blocks = new CoverageBlocks(List.of(pending(OCT_5.minusDays(1)), pending(OCT_5), pending(OCT_7),
				pending(OCT_7.plusDays(1))), OCT_5, OCT_7);

		assertThat(blocks.networkRowsByDay()).containsOnlyKeys(OCT_5, OCT_7);
	}
}
