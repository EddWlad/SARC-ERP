package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IssueCode;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowDecision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowInterpretation;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.WindowResolution;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una fila de la vista previa: lo que traia el archivo (sin cambios), las correcciones, lo que se entendio y su
 * clasificacion con el motivo de cada observacion (FR-023, FR-025).
 */
public record IntermittencyImportRowResponse(UUID id, long version, int rowNumber,
		RowClassification classification, @Schema(nullable = true) RowDecision decision,
		@Schema(nullable = true) String skipReason, @Schema(nullable = true) UUID equipmentId,
		List<RawCellResponse> raw, List<EditResponse> edits, InterpretationResponse interpretation,
		List<LocalDate> touchedDays) {

	public record RawCellResponse(String key, String label, String value) {
	}

	public record EditResponse(String target, @Schema(nullable = true) Integer pair,
			@Schema(nullable = true) String field, @Schema(nullable = true) String previous,
			@Schema(nullable = true) String value, @Schema(nullable = true) UUID actor, String at) {
	}

	public record IssueResponse(IssueCode code, String message) {
	}

	/** Una ventana interpretada. {@code minutes} es nulo si no se pudo calcular o si esta abierta. */
	public record WindowInterpretationResponse(int pair, @Schema(nullable = true) String startText,
			@Schema(nullable = true) String endText, @Schema(nullable = true) String startAt,
			@Schema(nullable = true) String endAt, @Schema(nullable = true) Integer minutes, boolean endsNextDay,
			boolean test, boolean added, RowClassification classification,
			@Schema(nullable = true) WindowResolution decision, @Schema(nullable = true) UUID conflictWindowId,
			@Schema(nullable = true) Integer mergedIntoPair, List<IssueResponse> issues) {
	}

	/**
	 * Los totales se muestran con y sin el duplicado mientras haya idénticas sin resolver:
	 * {@code computedWithDuplicatesMinutes} es nulo si no las hay.
	 */
	public record InterpretationResponse(@Schema(nullable = true) LocalDate date, String code, String name,
			String comment, @Schema(nullable = true) Integer fileTotalMinutes, Integer computedTotalMinutes,
			@Schema(nullable = true) Integer computedWithDuplicatesMinutes, boolean totalAcknowledged,
			List<IssueResponse> rowIssues, List<WindowInterpretationResponse> windows) {
	}

	public static IntermittencyImportRowResponse from(IntermittencyImportRow row) {
		RowInterpretation interpretation = row.getInterpretation();
		return new IntermittencyImportRowResponse(row.getId(), row.getVersion(), row.getRowNumber(),
				row.getClassification(), row.getDecision(), row.getSkipReason(), row.getEquipmentId(),
				row.getRaw().cells().stream()
						.map(cell -> new RawCellResponse(cell.key(), cell.label(), cell.value())).toList(),
				row.getEdits().stream().map(edit -> new EditResponse(edit.target(), edit.pair(), edit.field(),
						edit.previous(), edit.value(), edit.actor(), edit.at())).toList(),
				new InterpretationResponse(interpretation.day(), interpretation.code(), interpretation.name(),
						interpretation.comment(), interpretation.fileTotalMinutes(),
						interpretation.computedTotalMinutes(), interpretation.computedWithDuplicatesMinutes(),
						interpretation.totalAcknowledged(), issues(interpretation.rowIssues()),
						interpretation.windows().stream().map(IntermittencyImportRowResponse::window).toList()),
				row.getTouchedDays());
	}

	private static WindowInterpretationResponse window(RowWindow window) {
		return new WindowInterpretationResponse(window.pair(), window.startText(), window.endText(),
				window.startAt(), window.endAt(), window.minutes(), window.endsNextDay(), window.test(),
				window.added(), window.classification(), window.decision(),
				window.conflictWindowId() == null ? null : UUID.fromString(window.conflictWindowId()),
				window.mergedIntoPair(), issues(window.issues()));
	}

	private static List<IssueResponse> issues(List<IssueCode> codes) {
		return codes.stream().map(code -> new IssueResponse(code, IssueMessages.of(code))).toList();
	}
}
