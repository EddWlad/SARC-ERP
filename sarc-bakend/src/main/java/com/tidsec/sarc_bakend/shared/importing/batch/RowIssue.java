package com.tidsec.sarc_bakend.shared.importing.batch;

/** Observacion sobre una fila de la vista previa: codigo estable y mensaje para el analista. */
public record RowIssue(String code, String message) {
}
