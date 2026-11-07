package com.tidsec.sarc_bakend.modules.uptime.application.result;

import java.util.UUID;

import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;

/** Conector en la grilla de captura y su situacion para el periodo exacto consultado. */
public record GridRow(MeasurableConnector connector, Situation situation, UUID measurementId, Long version,
        boolean referenceReportPresent) {
    public GridRow(MeasurableConnector connector,Situation situation,UUID measurementId,Long version) {
        this(connector,situation,measurementId,version,false);
    }

	public enum Situation {
		NONE,
		PENDING,
		OBSERVED,
		VALIDATED,
		EXCLUDED
	}
}
