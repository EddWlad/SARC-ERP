package com.tidsec.sarc_bakend.modules.intermittency.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.ReviewReason;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.Figures;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Period;

/**
 * Lo que ve el dashboard para un periodo y un corte. Las cifras oficiales y las series salen de
 * {@link Figures}; {@code pendingLink} y {@code conflict} son filas de lotes ya confirmados que siguen sin
 * resolver y no cuentan en lo oficial. {@code details} solo se llena para editores (FR-005).
 */
public record IntermittencySummary(Period period, Instant cutOff, Figures figures, int pendingLink, int conflict,
		OutsideDetails details, Instant generatedAt) {

	/** Registros por revisar del tablero y de los informes: ventanas por revisar, filas en conflicto y por vincular. */
	public int toReview() {
		return figures.unofficial().review() + conflict + pendingLink;
	}

	/** Una ventana que no es oficial: abierta (duracion provisional), por revisar o de prueba. */
	public record OutsideWindow(UUID windowId, UUID equipmentId, String pointLabel, WindowState state, Instant startAt,
			Instant endAt, long minutes, List<ReviewReason> reasons) {
	}

	/** Una fila de un lote confirmado que sigue pendiente. */
	public record OutsidePendingRow(UUID batchId, int rowNumber, RowClassification classification, UUID equipmentId) {
	}

	/** Detalle acotado de lo que esta fuera de lo oficial; {@code truncated} avisa que hay mas. */
	public record OutsideDetails(List<OutsideWindow> windows, List<OutsidePendingRow> pendingRows,
			boolean truncated) {
	}
}
