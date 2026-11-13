package com.tidsec.sarc_bakend.modules.intermittency.application.result;

import java.util.List;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowRevision;

/**
 * Una ventana con su historial. {@code revisions} es nulo para quien no es editor: solo ve el resultado vigente de
 * una ventana confirmada, sin autores, motivos ni procedencia (FR-005). {@code currentPointName} es el nombre actual
 * del punto, que puede diferir de la etiqueta registrada en la fecha del hecho (US3-6, D12).
 */
public record WindowDetail(WindowView view, List<WindowRevision> revisions, String currentPointName) {
}
