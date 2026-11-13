package com.tidsec.sarc_bakend.modules.intermittency.domain.model.window;

/** Motivo de cada revision del historial de una ventana. */
public enum WindowChange {
	CREATED,
	CORRECTED,
	CLOSED,
	REVIEW_CONFIRMED,
	MARKED_TEST,
	UNMARKED_TEST,
	ANNULLED,
	REPLACED_BY_IMPORT
}
