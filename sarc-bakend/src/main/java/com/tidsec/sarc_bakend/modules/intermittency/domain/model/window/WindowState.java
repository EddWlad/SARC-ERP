package com.tidsec.sarc_bakend.modules.intermittency.domain.model.window;

/** Estado de una ventana. Pendiente de vincular no es un estado: es una clasificacion de la fila de un lote. */
public enum WindowState {
	/** Sin fin: el punto sigue fuera de linea. Su duracion es provisional y no suma a lo oficial. */
	OPEN,
	/** Cerrada pero con una observacion que un editor debe confirmar, por ejemplo una duracion de 4 h o mas. */
	REVIEW,
	/** Cerrada y vigente: es la unica que cuenta en los indicadores oficiales. */
	CONFIRMED,
	/** Marcada como prueba: se conserva y se excluye de lo oficial. Siempre esta cerrada. */
	TEST,
	/** Dejada sin efecto con justificacion. Nunca se borra fisicamente. */
	ANNULLED
}
