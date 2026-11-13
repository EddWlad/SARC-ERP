package com.tidsec.sarc_bakend.modules.intermittency.application.command;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowDecision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.WindowResolution;

/** Entradas de los casos de uso de importacion. */
public final class ImportCommands {

	private ImportCommands() {
	}

	/** XLSX o CSV de hasta 20 MiB. */
	public record Upload(String fileName, byte[] content) {
	}

	/** Bloque pegado desde una hoja de calculo (tabuladores). */
	public record Paste(String text) {
	}

	/**
	 * Correccion de una fila en la vista previa (FR-026). {@code version} es la que leyo quien edita.
	 * {@code edits}, {@code addWindows} y {@code removePairs} son opcionales.
	 */
	public record EditRow(UUID batchId, UUID rowId, long version, List<Edit> edits, List<NewWindow> addWindows,
			List<Integer> removePairs) {

		public EditRow {
			edits = edits == null ? List.of() : List.copyOf(edits);
			addWindows = addWindows == null ? List.of() : List.copyOf(addWindows);
			removePairs = removePairs == null ? List.of() : List.copyOf(removePairs);
		}
	}

	/**
	 * Una edicion: {@code target} es {@code date}, {@code code}, {@code comment}, {@code test} o {@code window}.
	 * Para {@code window} se indica el par y alguno de {@code startAt}, {@code endAt} (con fecha y hora completas
	 * y desfase) o {@code endsNextDay}; para el resto, {@code value}.
	 */
	public record Edit(String target, Integer pair, String startAt, String endAt, Boolean endsNextDay,
			String value) {
	}

	/** Un par nuevo con instantes completos; sin fin, queda como par incompleto. */
	public record NewWindow(String startAt, String endAt) {
	}

	/** Vincula la fila a un punto KIA elegido de forma explicita (FR-003). */
	public record LinkRow(UUID batchId, UUID rowId, long version, UUID equipmentId) {
	}

	/** Decide que se hace con la fila y con cada una de sus ventanas (FR-027 a FR-029). */
	public record DecideRow(UUID batchId, UUID rowId, long version, RowDecision decision, String skipReason,
			boolean acknowledgeTotal, List<WindowDecision> windows) {

		public DecideRow {
			windows = windows == null ? List.of() : List.copyOf(windows);
		}
	}

	/**
	 * Resuelve una fila KEEP_PENDING de un lote ya confirmado: puede vincular un punto, corregir datos y decidir
	 * cada ventana; {@code decision} es APPLY u SKIP (con motivo).
	 */
	public record ResolvePending(UUID batchId, UUID rowId, long version, UUID equipmentId, List<Edit> edits,
			RowDecision decision, String skipReason, boolean acknowledgeTotal, List<WindowDecision> windows) {

		public ResolvePending {
			edits = edits == null ? List.of() : List.copyOf(edits);
			windows = windows == null ? List.of() : List.copyOf(windows);
		}
	}

	/** Resolucion de un par; {@code resolution} nulo retira la decision anterior. */
	public record WindowDecision(int pair, WindowResolution resolution, String justification) {
	}
}
