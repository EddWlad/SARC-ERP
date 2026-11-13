package com.tidsec.sarc_bakend.modules.intermittency.application.result;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ventanas creadas por una captura y el total de cada dia local, sin contar las abiertas. */
public record CaptureResult(UUID equipmentId, List<WindowView> windows, List<DayTotal> dayTotals) {

	/** Minutos cerrados de la captura en un dia local; una ventana que cruza la medianoche aporta a ambos dias. */
	public record DayTotal(LocalDate day, long minutes, int windows) {
	}
}
