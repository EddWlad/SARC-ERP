package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/** Formatos de evidencia aceptados: imagen o PDF. Se reconocen por su firma binaria, nunca por el nombre. */
public enum EvidenceFormat {

	JPEG("jpg"),
	PNG("png"),
	WEBP("webp"),
	PDF("pdf");

	private final String extension;

	EvidenceFormat(String extension) {
		this.extension = extension;
	}

	/** Extension canonica con la que se guarda el archivo; la del nombre que envio el cliente no se usa. */
	public String extension() {
		return extension;
	}
}
