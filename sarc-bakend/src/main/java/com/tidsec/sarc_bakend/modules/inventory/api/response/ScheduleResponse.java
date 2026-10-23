package com.tidsec.sarc_bakend.modules.inventory.api.response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.tidsec.sarc_bakend.modules.inventory.application.result.EquipmentView;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ScheduleMode;

/** Horario del equipo; un intervalo con cierre anterior a la apertura cruza la medianoche. */
public record ScheduleResponse(ScheduleMode mode, List<IntervalResponse> intervals, List<ExceptionResponse> exceptions) {

	public record IntervalResponse(DayOfWeek day, LocalTime opensAt, LocalTime closesAt, boolean overnight) {
	}

	public record ExceptionResponse(LocalDate date, boolean closed, @Schema(nullable = true) LocalTime opensAt,
			@Schema(nullable = true) LocalTime closesAt, @Schema(nullable = true) String note) {
	}

	public static ScheduleResponse from(EquipmentView equipment) {
		return new ScheduleResponse(equipment.scheduleMode(),
				equipment.weeklyIntervals().stream()
						.map(i -> new IntervalResponse(i.dayOfWeek(), i.opensAt(), i.closesAt(), i.isOvernight()))
						.toList(),
				equipment.scheduleExceptions().stream()
						.map(e -> new ExceptionResponse(e.date(), e.closed(), e.opensAt(), e.closesAt(), e.note()))
						.toList());
	}
}
