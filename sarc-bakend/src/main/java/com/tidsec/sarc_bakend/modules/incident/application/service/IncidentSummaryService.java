package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.incident.application.result.ManagementData;

/**
 * Cifras del tablero de gestion (FR-080, FR-086). Lee con un solo corte en una transaccion de solo lectura
 * {@code REPEATABLE READ}, con la misma consulta y el mismo calculo que el PDF y el Excel, asi que las tres vistas
 * coinciden. No muestra cifras de disponibilidad: eso es de Uptime e Intermitencias (P-13).
 */
@Service
@PreAuthorize(IncidentPermissions.READERS)
public class IncidentSummaryService {

	private final IncidentManagementQuery query;
	private final TransactionTemplate snapshot;
	private final Clock clock;

	IncidentSummaryService(IncidentManagementQuery query, PlatformTransactionManager transactionManager, Clock clock) {
		this.query = query;
		this.snapshot = new TransactionTemplate(transactionManager);
		this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		this.snapshot.setReadOnly(true);
		this.clock = clock;
	}

	/** El reporte: con la bitacora y las comunicaciones completas. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ManagementData summary(IncidentManagementQuery.Selection selection) {
		return read(selection, true);
	}

	/** El tablero: solo las cifras, que son las mismas que las del reporte del mismo corte. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ManagementData figures(IncidentManagementQuery.Selection selection) {
		return read(selection, false);
	}

	private ManagementData read(IncidentManagementQuery.Selection selection, boolean fullLog) {
		Instant cutOff = Instant.now(clock);
		return Objects.requireNonNull(snapshot.execute(status -> query.management(selection, cutOff, fullLog)));
	}
}
