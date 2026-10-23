package com.tidsec.sarc_bakend.shared.importing;

/**
 * Un valor del archivo no se puede interpretar sin adivinar. No es un error de la peticion: el
 * importador lo convierte en una observacion de la fila y la fila queda invalida en la vista previa.
 */
public class InvalidValueException extends RuntimeException {

	private final String code;

	public InvalidValueException(String code, String message) {
		super(message, null, false, false);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
