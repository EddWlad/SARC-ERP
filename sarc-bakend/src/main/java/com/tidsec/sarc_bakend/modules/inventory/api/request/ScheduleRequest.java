package com.tidsec.sarc_bakend.modules.inventory.api.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.tidsec.sarc_bakend.modules.inventory.application.command.DefineScheduleCommand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ScheduleException;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ScheduleMode;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.WeeklyInterval;

/**
 * Horario completo del equipo. En WEEKLY cada intervalo es un dia con apertura y cierre (si cierra
 * antes de abrir, cruza la medianoche). Las excepciones por fecha prevalecen sobre el habitual.
 */
public record ScheduleRequest(
		@NotNull Long version,
		@NotNull ScheduleMode mode,
		@NotNull @Size(max = 50) List<@Valid IntervalRequest> intervals,
		@NotNull @Size(max = 366) List<@Valid ExceptionRequest> exceptions) {

	public record IntervalRequest(@NotNull DayOfWeek day, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {
	}

	public record ExceptionRequest(@NotNull LocalDate date, boolean closed, LocalTime opensAt, LocalTime closesAt,
			@Size(max = 200) String note) {
	}

	public DefineScheduleCommand toCommand(UUID equipmentId) {
		return new DefineScheduleCommand(equipmentId, version, mode,
				intervals.stream().map(i -> new WeeklyInterval(i.day(), i.opensAt(), i.closesAt())).toList(),
				exceptions.stream()
						.map(e -> new ScheduleException(e.date(), e.closed(), e.opensAt(), e.closesAt(), e.note()))
						.toList());
	}
}
