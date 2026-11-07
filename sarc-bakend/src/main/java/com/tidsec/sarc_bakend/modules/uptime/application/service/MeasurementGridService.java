package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementPeriod;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.uptime.application.result.GridRow;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReviewState;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeScope;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementRepository;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorDirectory;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorQuery;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/** Grilla de captura: conectores en orden estable, con su situacion por identidad exacta. */
@Service
@Transactional(readOnly = true)
@PreAuthorize(UptimePermissions.EDITORS)
public class MeasurementGridService {

	private final ConnectorDirectory connectors;
	private final MeasurementRepository measurements;

	MeasurementGridService(ConnectorDirectory connectors, MeasurementRepository measurements) {
		this.connectors = connectors;
		this.measurements = measurements;
	}

	public List<GridRow> grid(ConnectorQuery query, Instant periodStart, Instant periodEnd, boolean showInactive) {
		List<MeasurableConnector> inScope = connectors.search(query).stream()
				.filter(connector -> UptimeScope.includes(connector.network(), connector.type()))
				.filter(connector -> showInactive || isActive(connector)).toList();
		List<Measurement> found = measurements.findByPeriod(
				inScope.stream().map(MeasurableConnector::connectorId).toList(), MeasurementSource.ENERLINK,
				periodStart, periodEnd);
		Map<UUID, Measurement> byConnector = found.stream()
				.collect(Collectors.toMap(Measurement::getConnectorId, Function.identity()));
		Map<UUID, MeasurementRevision> latest = measurements.findLatestRevisions(found).stream()
				.collect(Collectors.toMap(MeasurementRevision::getMeasurementId, Function.identity()));
		return inScope.stream().map(connector -> {
			Measurement measurement = byConnector.get(connector.connectorId());
			if (measurement == null) {
				return new GridRow(connector, GridRow.Situation.NONE, null, null);
			}
			return new GridRow(connector, situation(measurement, latest.get(measurement.getId())),
					measurement.getId(), measurement.getVersion());
		}).toList();
	}

    public List<GridRow> gridReference(ConnectorQuery query,LocalDate week,boolean showInactive) {
        MeasurementPeriod.reference(week,"America/Guayaquil");
        var scope=connectors.search(query).stream()
            .filter(c->UptimeScope.includes(c.network(),c.type()))
            .filter(c->showInactive || isActive(c)).toList();
        var ids=scope.stream().map(MeasurableConnector::connectorId).toList();
        var known=measurements.findByReferenceWeek(ids,MeasurementSource.ENERLINK,week,TemporalPrecision.KNOWN_RANGE);
        var revisions=measurements.findLatestRevisions(known).stream()
            .collect(Collectors.toMap(MeasurementRevision::getMeasurementId,Function.identity()));
        Map<UUID,Measurement> byConnector=known.stream().filter(m->revisions.get(m.getId()).getPeriodType()==com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType.ROLLING_7D).collect(Collectors.toMap(Measurement::getConnectorId,
            Function.identity(),(first,later)->first));
        Set<UUID> reports=measurements.findByReferenceWeek(ids,MeasurementSource.ENERLINK,week,TemporalPrecision.REFERENCE_WEEK)
            .stream().map(Measurement::getConnectorId).collect(Collectors.toSet());
        return scope.stream().map(c->{
            var m=byConnector.get(c.connectorId());
            return new GridRow(c,m==null?GridRow.Situation.NONE:situation(m,revisions.get(m.getId())),
                m==null?null:m.getId(),m==null?null:m.getVersion(),reports.contains(c.connectorId()));
        }).toList();
    }

	private static boolean isActive(MeasurableConnector connector) {
		return connector.connectorActive() && connector.equipmentStatus() == AdministrativeStatus.ACTIVE;
	}

	private static GridRow.Situation situation(Measurement measurement, MeasurementRevision latest) {
		if (measurement.isExcluded()) {
			return GridRow.Situation.EXCLUDED;
		}
		if (latest.getReviewState() == ReviewState.VALIDATED) {
			return GridRow.Situation.VALIDATED;
		}
		return latest.getObservations().isEmpty() ? GridRow.Situation.PENDING : GridRow.Situation.OBSERVED;
	}
}
