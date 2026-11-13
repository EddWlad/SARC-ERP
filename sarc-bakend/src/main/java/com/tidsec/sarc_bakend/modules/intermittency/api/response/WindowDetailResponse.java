package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.WindowDetail;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Detalle de una ventana. {@code revisions} y la procedencia del lote son nulos para quien no es editor
 * (FR-005): solo ve el resultado vigente de una ventana confirmada. {@code currentPointName} distingue el nombre
 * actual del punto de la etiqueta registrada en la fecha del hecho ({@code window.pointLabel}).
 */
public record WindowDetailResponse(WindowResponse window, @Schema(nullable = true) List<WindowRevisionResponse> revisions,
		@Schema(nullable = true) UUID batchId, @Schema(nullable = true) Integer rowNumber,
		@Schema(nullable = true) Integer pairNumber, @Schema(nullable = true) String currentPointName) {

	public static WindowDetailResponse from(WindowDetail detail) {
		IntermittencyWindow window = detail.view().window();
		boolean editor = detail.revisions() != null;
		return new WindowDetailResponse(WindowResponse.from(detail.view()),
				editor ? detail.revisions().stream().map(WindowRevisionResponse::from).toList() : null,
				editor ? window.getBatchId() : null, editor ? window.getRowNumber() : null,
				editor ? window.getPairNumber() : null, detail.currentPointName());
	}
}
