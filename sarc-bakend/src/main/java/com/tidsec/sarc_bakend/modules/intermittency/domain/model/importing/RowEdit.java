package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

import java.util.UUID;

/**
 * Una correccion hecha en la vista previa, con el valor anterior, el autor y el momento (FR-026). El valor
 * original de la fila no se toca: la interpretacion se recalcula aplicando las ediciones en orden.
 *
 * <p>{@code target}: {@code date}, {@code code}, {@code comment}, {@code test} (con o sin par),
 * {@code window} (campos {@code startAt}, {@code endAt} y {@code endsNextDay} de un par),
 * {@code addWindow} (un par nuevo; {@code field} es {@code startAt} o {@code endAt}) y {@code removePair}.
 * {@code at} es un instante ISO-8601.
 */
public record RowEdit(String target, Integer pair, String field, String previous, String value, UUID actor,
		String at) {

	public static final String DATE = "date";
	public static final String CODE = "code";
	public static final String COMMENT = "comment";
	public static final String TEST = "test";
	public static final String WINDOW = "window";
	public static final String ADD_WINDOW = "addWindow";
	public static final String REMOVE_PAIR = "removePair";
	/** Decision sobre un par ({@code field} es la resolucion; {@code value}, su justificacion): solo deja rastro. */
	public static final String DECISION = "decision";
	/** Decision sobre la fila entera ({@code field} es APPLY, SKIP o KEEP_PENDING; {@code value}, el motivo). */
	public static final String ROW_DECISION = "rowDecision";
	public static final String ACKNOWLEDGE_TOTAL = "acknowledgeTotal";
	public static final String LINK = "link";

	public static final String START_AT = "startAt";
	public static final String END_AT = "endAt";
	public static final String ENDS_NEXT_DAY = "endsNextDay";
}
