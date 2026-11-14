package com.tidsec.sarc_bakend.modules.consumption.application.result;

import java.util.List;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionRevision;

/**
 * Detalle de una sesion (Feature 009, FR-011): la sesion con su equipo y, solo para quien lee datos
 * operativos, su baja y sus revisiones (importacion, edicion y baja), de la mas reciente a la mas antigua.
 */
public record SessionDetail(SessionView view, List<SessionRevision> revisions, boolean operational) {
}
