package com.tidsec.sarc_bakend.modules.uptime.application.result;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;

/** Medicion con la revision que se presenta. */
public record MeasurementView(Measurement measurement, MeasurementRevision revision) {
}
