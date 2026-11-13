package com.tidsec.sarc_bakend.modules.intermittency.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Un dia y el estado de su marca de revisado (FR-044). {@code active} es falso para un dia sin marca o con la
 * marca retirada; entonces {@code markedBy}, {@code markedAt} y {@code version} pueden ser nulos.
 * {@code pendingLinkRows} son las filas por vincular de lotes confirmados que tocan el dia y lo dejan sin
 * cubrir para toda la red; {@code unconfirmedBatches}, los lotes en vista previa que lo tocan y todavia no
 * afectan la cobertura (FR-045).
 */
public record ReviewedDayView(LocalDate day, boolean active, UUID markedBy, Instant markedAt, Long version,
		boolean finished, int pendingLinkRows, int unconfirmedBatches) {

	/** Un lote en vista previa que toca el dia que se acaba de marcar. */
	public record UnconfirmedBatch(UUID batchId, String fileName) {
	}

	/** El dia ya marcado y los lotes sin confirmar de los que hay que avisar. */
	public record Marked(ReviewedDayView day, List<UnconfirmedBatch> warnings) {
	}
}
