package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.util.List;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.uptime.application.result.MeasurementDetail;
import com.tidsec.sarc_bakend.modules.uptime.application.result.MeasurementHistory;
import com.tidsec.sarc_bakend.modules.uptime.application.result.MeasurementView;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementCriteria;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementRepository;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorDirectory;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/**
 * Lectura del historial con proyeccion por rol. Consulta solo ve vigentes no excluidas, y sus
 * parametros no amplian esa proyeccion.
 */
@Service
@Transactional(readOnly = true)
public class MeasurementReadService {

	private final MeasurementRepository measurements;
	private final ConnectorDirectory connectors;
	private final UptimeReadAccess access;
    private final com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementTemporalChangeRepository temporal;

	MeasurementReadService(MeasurementRepository measurements, ConnectorDirectory connectors, UptimeReadAccess access, com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementTemporalChangeRepository temporal) {
		this.measurements = measurements;
		this.connectors = connectors;
		this.access = access;this.temporal=temporal;
	}

	public PageResult<MeasurementDetail> search(UUID connectorId, UUID equipmentId, MeasurementCriteria.Queue queue,
			PageQuery page) {
		boolean operational = access.canReadOperational();
		if (queue != null && !operational) {
			throw new AccessDeniedException("El filtro de cola es operativo");
		}
		return measurements.search(new MeasurementCriteria(connectorId, equipmentId, queue, !operational), page)
				.map(measurement -> detail(measurement, operational));
	}

	public MeasurementDetail detail(UUID id) {
		boolean operational = access.canReadOperational();
		Measurement measurement = measurements.findById(id).orElseThrow(MeasurementReadService::notFound);
		if (!operational && (measurement.getEffectiveRevision() == null || measurement.isExcluded()
            || measurement.getTemporalPrecision() == TemporalPrecision.REQUIRES_RECONCILIATION)) {
			throw notFound();
		}
		return detail(measurement, operational);
	}

	@PreAuthorize(UptimePermissions.EDITORS)
	public MeasurementHistory history(UUID id) {
		measurements.findById(id).orElseThrow(MeasurementReadService::notFound);
		return new MeasurementHistory(measurements.findRevisions(id), measurements.findExclusions(id),temporal.history(id));
	}

	private MeasurementDetail detail(Measurement measurement, boolean operational) {
		int shown = operational ? measurement.getLatestRevision() : measurement.getEffectiveRevision();
		MeasurementRevision revision = measurements.findRevision(measurement.getId(), shown).orElseThrow();
		return new MeasurementDetail(new MeasurementView(measurement, revision),
				connectors.findByIds(List.of(measurement.getConnectorId())).get(measurement.getConnectorId()), operational);
	}

	private static NotFoundException notFound() {
		return new NotFoundException("UPTIME_MEASUREMENT_NOT_FOUND", "Medicion no encontrada.");
	}
}
