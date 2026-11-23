package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import java.time.Instant;
import java.util.List;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

/**
 * Estado de un ticket en un instante y su pertenencia a un periodo (FR-073, FR-074), para el informe «Control de
 * tickets» y el listado con periodo. Es una funcion pura sobre los episodios del ticket tal como se conocian en el corte:
 * cada uno aporta su T0 y su T4 (nulo si no tiene un T4 registrado). El inicio del rango es inclusivo y el fin exclusivo.
 *
 * <p>Un ticket esta <b>abierto</b> en un instante si algun episodio tiene T0 anterior a ese instante y no tiene T4 o su T4
 * es igual o posterior; en otro caso esta cerrado. Un ticket reabierto cuenta una sola vez, con el estado del episodio
 * vigente en ese instante.
 */
public final class TicketStateAt {

	/** T0 y T4 de un episodio; {@code t4} es nulo si el episodio no tiene un T4 registrado. */
	public record Span(Instant t0, Instant t4) {
	}

	private TicketStateAt() {
	}

	/**
	 * El fin efectivo de un periodo: si el fin exclusivo es posterior al corte, se usa el corte (FR-074). El corte
	 * incluye lo registrado en su mismo instante, asi que el fin exclusivo es un instante despues: un ticket abierto
	 * exactamente en el corte cuenta, y uno cerrado en ese instante ya esta cerrado.
	 */
	public static Instant effectiveEnd(Instant exclusiveEnd, Instant cutOff) {
		return exclusiveEnd.isAfter(cutOff) ? cutOff.plusNanos(1) : exclusiveEnd;
	}

	/** El ticket estuvo abierto en algun momento de {@code [start, end)}, con {@code end} ya acotado al corte (FR-073). */
	public static boolean includedIn(List<Span> spans, Instant start, Instant end) {
		return spans.stream().anyMatch(span -> span.t0().isBefore(end) && (span.t4() == null || !span.t4().isBefore(start)));
	}

	/** El estado en {@code instant}: abierto si algun episodio ya habia empezado y aun no habia cerrado (FR-074). */
	public static TicketState at(List<Span> spans, Instant instant) {
		boolean open = spans.stream()
				.anyMatch(span -> span.t0().isBefore(instant) && (span.t4() == null || !span.t4().isBefore(instant)));
		return open ? TicketState.OPEN : TicketState.CLOSED;
	}
}
