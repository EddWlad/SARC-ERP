package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.IntermittencyQuery;
import com.tidsec.sarc_bakend.modules.intermittency.application.port.IntermittencyReportRenderer;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.IntermittencySummaryService.Computed;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.WindowPortion;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Preset;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.ReportArchive;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/** El tope de ventanas de un informe (FR-041a): en el limite se genera, uno mas se rechaza sin archivar nada. */
class IntermittencyReportServiceTest {

	@Test
	void aReportAtTheLimitIsAccepted() {
		assertThatCode(() -> IntermittencyReportService.requireWithinLimit(IntermittencyReportService.MAX_WINDOWS))
				.doesNotThrowAnyException();
		assertThatCode(() -> IntermittencyReportService.requireWithinLimit(0)).doesNotThrowAnyException();
	}

	@Test
	void oneWindowOverTheLimitIsRejectedWithItsStableCode() {
		assertThatThrownBy(() -> IntermittencyReportService.requireWithinLimit(IntermittencyReportService.MAX_WINDOWS + 1))
				.isInstanceOfSatisfying(BusinessRuleException.class, error -> {
					assertThat(error.code()).isEqualTo("REPORT_TOO_LARGE");
					assertThat(error.getMessage()).contains("50000").contains("acorte el período");
				});
	}

	@Test
	void theLimitMatchesTheSpecification() {
		assertThat(IntermittencyReportService.MAX_WINDOWS).isEqualTo(50_000);
	}

	@Test
	void aRejectedReportIsNeverRenderedNorArchived() {
		IntermittencySummaryService summaries = mock(IntermittencySummaryService.class);
		IntermittencySummary summary = mock(IntermittencySummary.class);
		IntermittencyQuery query = new IntermittencyQuery(Preset.FIRST_HALF, YearMonth.of(2026, 10), null, null, null);
		Computed computed = new Computed(summary, List.of(), null, null, Map.of());
		when(summaries.compute(query)).thenReturn(computed);
		when(summaries.confirmedPortions(computed)).thenReturn(Collections.nCopies(
				IntermittencyReportService.MAX_WINDOWS + 1, new WindowPortion(null, 1)));
		IntermittencyReportRenderer renderer = mock(IntermittencyReportRenderer.class);
		when(renderer.format()).thenReturn(ReportFormat.PDF);
		ReportArchive archive = mock(ReportArchive.class);
		IntermittencyReportService service = new IntermittencyReportService(summaries, mock(KiaPointDirectory.class),
				List.of(renderer), archive, mock(PlatformTransactionManager.class),
				new OperatingZone(ZoneId.of("America/Guayaquil")), mock(IntermittencyReadAccess.class),
				mock(AvailabilityService.class));

		assertThatThrownBy(() -> service.generateIssued(query, ReportFormat.PDF))
				.isInstanceOfSatisfying(BusinessRuleException.class,
						error -> assertThat(error.code()).isEqualTo("REPORT_TOO_LARGE"));

		verify(renderer, never()).render(any());
		verifyNoInteractions(archive);
	}
}
