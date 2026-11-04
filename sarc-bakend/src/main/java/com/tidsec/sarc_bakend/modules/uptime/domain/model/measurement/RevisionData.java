package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contenido de una revision. La etiqueta es la de la fecha del hecho (D12). */
public record RevisionData(ReadingClass readingClass, PeriodType periodType, Integer availableMinutes,
		Integer occupiedMinutes, Integer faultMinutes, Integer offlineMinutes, BigDecimal reportedPercent,
		List<Observation> observations, String justification, MeasurementOrigin origin, UUID importBatchId,
		Map<String, Object> sourceValues, String equipmentLabel, String equipmentCode, String connectorTypeLabel,
		UUID exactConfirmedBy, Instant exactConfirmedAt, UUID enteredBy, Instant enteredAt, UptimeBasis uptimeBasis) {
}
