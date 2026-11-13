package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary.OutsideDetails;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.ReviewReason;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.DurationBand;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.Figures;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Preset;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cifras del dashboard de intermitencias. No tiene ningun campo de disponibilidad: esa consulta es aparte y
 * solo para quien tiene el permiso (FR-006). {@code details} es nulo para quien no edita.
 */
public record IntermittencySummaryResponse(SummaryPeriod period, OfficialIndicators official,
		OutsideOfficialIndicators outsideOfficial, List<TopPointRow> top, boolean topExtendedByTies,
		List<DailyRow> daily, boolean dailyWindowsMayExceedTotal, List<DurationBandRow> byDurationBand,
		ConcentrationShare concentration, RecurrenceShare recurrence, Instant generatedAt) {

	/** Tipo de periodo y su rango real: {@code to} es exclusivo; {@code cutOff} es el momento de la consulta. */
	public record SummaryPeriod(Preset preset, LocalDate from, LocalDate to, Instant cutOff, String timeZone) {
	}

	public record OfficialIndicators(long minutes, int affectedPoints, int windows, int pointDays,
			int recurrentPoints, @Schema(nullable = true) BigDecimal averageMinutes) {
	}

	public record OpenEvents(int count, long provisionalMinutes) {
	}

	public record OutsideOfficialIndicators(OpenEvents open, int review, int test, int pendingLink, int conflict,
			@Schema(nullable = true) OutsideDetailsResponse details) {
	}

	public record OutsideDetailsResponse(List<OutsideWindowRow> windows, List<OutsidePendingRowRow> pendingRows,
			boolean truncated) {
	}

	public record OutsideWindowRow(UUID windowId, UUID equipmentId, String pointLabel, WindowState state,
			Instant startAt, @Schema(nullable = true) Instant endAt, long minutes, List<ReviewReason> reasons) {
	}

	public record OutsidePendingRowRow(UUID batchId, int rowNumber, RowClassification classification,
			@Schema(nullable = true) UUID equipmentId) {
	}

	public record TopPointRow(UUID equipmentId, @Schema(nullable = true) String code, String name, long minutes,
			int windows, int days) {
	}

	public record DailyRow(LocalDate day, long minutes, int windows) {
	}

	public record DurationBandRow(DurationBand band, long minutes, int windows) {
	}

	public record ConcentrationShare(long topMinutes, long restMinutes) {
	}

	public record RecurrenceShare(long recurrentMinutes, long nonRecurrentMinutes) {
	}

	public static IntermittencySummaryResponse from(IntermittencySummary summary, String timeZone) {
		Figures figures = summary.figures();
		var official = figures.official();
		var unofficial = figures.unofficial();
		return new IntermittencySummaryResponse(
				new SummaryPeriod(summary.period().preset(), summary.period().from(), summary.period().to(),
						summary.cutOff(), timeZone),
				new OfficialIndicators(official.minutes(), official.affectedPoints(), official.windows(),
						official.pointDays(), official.recurrentPoints(), official.averageMinutes()),
				new OutsideOfficialIndicators(
						new OpenEvents(unofficial.open().count(), unofficial.open().provisionalMinutes()),
						unofficial.review(), unofficial.test(), summary.pendingLink(), summary.conflict(),
						details(summary.details())),
				figures.top().stream().map(point -> new TopPointRow(point.equipmentId(), point.code(), point.name(),
						point.minutes(), point.windows(), point.days())).toList(),
				figures.topExtendedByTies(),
				figures.daily().stream().map(day -> new DailyRow(day.day(), day.minutes(), day.windows())).toList(),
				figures.dailyWindowsMayExceedTotal(),
				figures.byDurationBand().stream()
						.map(band -> new DurationBandRow(band.band(), band.minutes(), band.windows())).toList(),
				new ConcentrationShare(figures.concentration().topMinutes(), figures.concentration().restMinutes()),
				new RecurrenceShare(figures.recurrence().recurrentMinutes(),
						figures.recurrence().nonRecurrentMinutes()),
				summary.generatedAt());
	}

	private static OutsideDetailsResponse details(OutsideDetails details) {
		if (details == null) {
			return null;
		}
		return new OutsideDetailsResponse(
				details.windows().stream().map(window -> new OutsideWindowRow(window.windowId(),
						window.equipmentId(), window.pointLabel(), window.state(), window.startAt(), window.endAt(),
						window.minutes(), window.reasons())).toList(),
				details.pendingRows().stream().map(row -> new OutsidePendingRowRow(row.batchId(), row.rowNumber(),
						row.classification(), row.equipmentId())).toList(),
				details.truncated());
	}
}
