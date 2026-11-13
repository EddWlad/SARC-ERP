package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.BlockingDay;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;

/**
 * Lo que dejan sin cubrir las filas pendientes de lotes confirmados (FR-045): una fila por vincular deja sin
 * cubrir el dia para toda la red y una fila en conflicto, solo el dia de su punto. Una fila en conflicto sin punto
 * conocido no se puede atribuir a nadie y se trata como por vincular: ninguna fila sin resolver deja un dia
 * cubierto. Es la unica regla de bloqueo: el panel de dias revisados y la disponibilidad la comparten.
 */
final class CoverageBlocks {

	private final Map<LocalDate, Integer> networkRows = new TreeMap<>();

	private final Map<UUID, Set<LocalDate>> pointDays = new HashMap<>();

	CoverageBlocks(List<BlockingDay> blocking, LocalDate from, LocalDate to) {
		for (BlockingDay block : blocking) {
			if (block.day().isBefore(from) || block.day().isAfter(to)) {
				continue;
			}
			if (block.classification() == RowClassification.CONFLICT && block.equipmentId() != null) {
				pointDays.computeIfAbsent(block.equipmentId(), key -> new HashSet<>()).add(block.day());
			}
			else {
				networkRows.merge(block.day(), 1, Integer::sum);
			}
		}
	}

	/** Filas que dejan cada dia sin cubrir para toda la red, en orden de dia. */
	Map<LocalDate, Integer> networkRowsByDay() {
		return networkRows;
	}

	/** Dias que las filas en conflicto dejan sin cubrir para ese punto. */
	Set<LocalDate> daysOf(UUID equipmentId) {
		return pointDays.getOrDefault(equipmentId, Set.of());
	}
}
