package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

/** Precision temporal independiente de la base de calculo y la aprobacion de valores. */
public enum TemporalPrecision {
    KNOWN_RANGE, REFERENCE_WEEK, REQUIRES_RECONCILIATION
}
