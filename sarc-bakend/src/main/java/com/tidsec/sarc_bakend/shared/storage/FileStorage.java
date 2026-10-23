package com.tidsec.sarc_bakend.shared.storage;

import java.util.Optional;

/**
 * Puerto de almacenamiento externo privado (TRD 02.1: imagenes y documentos no van al disco de
 * la VPS). Las referencias son opacas: solo este puerto sabe convertirlas en una URL.
 */
public interface FileStorage {

	/** Guarda el contenido dentro de la carpeta logica indicada y devuelve su referencia. */
	String store(String folder, byte[] content);

	/**
	 * Guarda un documento original (p. ej. el archivo de una importacion) para trazabilidad. No se
	 * expone URL de descarga: solo queda la referencia junto al registro que lo origino.
	 */
	String storeDocument(String folder, String filename, byte[] content);

	void delete(String reference);

	/**
	 * URL firmada para mostrar el archivo, o vacio si el almacenamiento no esta disponible en este
	 * entorno. La autorizacion de quien la pide la decide el backend.
	 */
	Optional<String> accessUrl(String reference);
}
