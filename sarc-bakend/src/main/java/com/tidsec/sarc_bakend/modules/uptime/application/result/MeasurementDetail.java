package com.tidsec.sarc_bakend.modules.uptime.application.result;

import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;

/** Medicion con la identificacion vigente del conector; la etiqueta del hecho va en la revision. */
public record MeasurementDetail(MeasurementView view, MeasurableConnector connector, boolean operational) {
}
