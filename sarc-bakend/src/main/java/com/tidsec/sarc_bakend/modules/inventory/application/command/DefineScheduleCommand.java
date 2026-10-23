package com.tidsec.sarc_bakend.modules.inventory.application.command;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.ScheduleException;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.ScheduleMode;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.WeeklyInterval;

public record DefineScheduleCommand(UUID equipmentId, long version, ScheduleMode mode, List<WeeklyInterval> intervals,
		List<ScheduleException> exceptions) {
}
