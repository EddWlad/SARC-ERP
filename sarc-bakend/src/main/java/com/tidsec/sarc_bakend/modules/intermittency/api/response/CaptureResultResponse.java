package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.CaptureResult;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.DurationText;

/** Ventanas creadas y el total de cada dia local con tiempo cerrado. */
public record CaptureResultResponse(UUID equipmentId, List<WindowResponse> windows, List<DayTotalResponse> dayTotals) {

	public record DayTotalResponse(LocalDate day, long minutes, String text, int windows) {
	}

	public static CaptureResultResponse from(CaptureResult result) {
		return new CaptureResultResponse(result.equipmentId(),
				result.windows().stream().map(WindowResponse::from).toList(),
				result.dayTotals().stream().map(total -> new DayTotalResponse(total.day(), total.minutes(),
						DurationText.hoursAndMinutes(total.minutes()), total.windows())).toList());
	}
}
