package com.tidsec.sarc_bakend.modules.intermittency.application.result;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;

/**
 * Una ventana con lo que depende del momento y de quien consulta. {@code provisionalMinutes} son los minutos de
 * una ventana abierta, desde su inicio hasta ahora: nunca suman a lo oficial (FR-012) y son nulos si la ventana
 * esta cerrada. {@code operational} es falso para quien solo consulta: no ve los motivos de revision (FR-005).
 */
public record WindowView(IntermittencyWindow window, Long provisionalMinutes, boolean operational) {

	/** Vista de la ventana en el instante {@code now}; una abierta lleva su duracion provisional. */
	public static WindowView of(IntermittencyWindow window, java.time.Instant now, boolean operational) {
		Long provisional = window.isOpen()
				? Math.max(0, java.time.Duration.between(window.getStartAt(), now).toMinutes()) : null;
		return new WindowView(window, provisional, operational);
	}
}
