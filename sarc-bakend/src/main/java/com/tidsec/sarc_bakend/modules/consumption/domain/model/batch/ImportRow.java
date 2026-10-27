package com.tidsec.sarc_bakend.modules.consumption.domain.model.batch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la vista previa. Guarda el valor interpretado y su clasificacion para que el analista
 * revise antes de confirmar, y despues registra que paso con ella (trazabilidad del lote).
 */
@Entity
@Table(name = "energy_import_row")
public class ImportRow {

	/** Tamano de la columna del ID de sesion de la plataforma. */
	public static final int MAX_SESSION_ID = 100;

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID batchId;

	@Column(nullable = false, updatable = false)
	private int rowNumber;

	@Column(updatable = false, length = 100)
	private String externalSessionId;

	@Column(updatable = false)
	private Instant startedAt;

	@Column(updatable = false)
	private Instant endedAt;

	@Column(updatable = false, precision = 12, scale = SessionReading.ENERGY_SCALE)
	private BigDecimal energyKwh;

	@Column(updatable = false, length = 150)
	private String sourceSite;

	@Column(updatable = false, length = 150)
	private String sourceCharger;

	@Column(updatable = false, length = 80)
	private String sourceSerial;

	@Column(updatable = false, length = 150)
	private String sourceConnector;

	@Column(updatable = false)
	private UUID equipmentId;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 20)
	private ChargingNetwork network;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 20)
	private InfrastructureType infrastructureType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private RowClassification classification;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	private List<RowIssue> issues;

	@Column(nullable = false, updatable = false)
	private boolean nonPreferredSource;

	@Column(nullable = false)
	private boolean test;

	@Column(nullable = false)
	private boolean approved;

	@Column(updatable = false)
	private UUID existingSessionId;

	/** Revision de la sesion existente que se reviso; si cambia antes de confirmar, la fila se omite. */
	@Column(updatable = false)
	private Integer existingSessionRevision;

	/** Conciliacion explicita de una fila {@link RowClassification#EQUIPMENT_CHANGED}. */
	@Column(nullable = false)
	private boolean reassigned;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private RowOutcome outcome;

	@Column(length = 500)
	private String outcomeDetail;

	protected ImportRow() {
	}

	/**
	 * Fila que no se pudo interpretar: solo se guardan sus observaciones y lo que se pudo leer. Un ID
	 * mas largo que la columna se guarda recortado para mostrarlo; el original sigue en el archivo.
	 */
	public static ImportRow invalid(UUID batchId, int rowNumber, String externalSessionId, List<RowIssue> issues) {
		String shown = externalSessionId == null || externalSessionId.length() <= MAX_SESSION_ID
				? externalSessionId
				: externalSessionId.substring(0, MAX_SESSION_ID - 1) + "…";
		return new ImportRow(batchId, rowNumber, shown, RowClassification.INVALID, issues);
	}

	public static ImportRow classified(UUID batchId, int rowNumber, String externalSessionId, SessionReading reading,
			EquipmentReference equipment, RowClassification classification, boolean nonPreferredSource,
			UUID existingSessionId, Integer existingSessionRevision, List<RowIssue> issues) {
		ImportRow row = new ImportRow(batchId, rowNumber, externalSessionId, classification, issues);
		row.startedAt = reading.startedAt();
		row.endedAt = reading.endedAt();
		row.energyKwh = reading.energyKwh();
		row.sourceSite = reading.site();
		row.sourceCharger = reading.charger();
		row.sourceSerial = reading.serial();
		row.sourceConnector = reading.connector();
		if (equipment != null) {
			row.equipmentId = equipment.id();
			row.network = equipment.network();
			row.infrastructureType = equipment.type();
		}
		row.nonPreferredSource = nonPreferredSource;
		row.existingSessionId = existingSessionId;
		row.existingSessionRevision = existingSessionRevision;
		return row;
	}

	private ImportRow(UUID batchId, int rowNumber, String externalSessionId, RowClassification classification,
			List<RowIssue> issues) {
		this.id = UUID.randomUUID();
		this.batchId = batchId;
		this.rowNumber = rowNumber;
		this.externalSessionId = externalSessionId;
		this.classification = classification;
		this.issues = List.copyOf(issues);
	}

	/**
	 * Decision del analista en la vista previa. Prueba: solo en filas que se incorporan. Aprobacion:
	 * solo en filas que la requieren (correcciones o fuente no preferente). Reasignacion: solo en
	 * filas que cambian de cargador.
	 */
	public void decide(Boolean markAsTest, Boolean approve, Boolean reassign) {
		if (reassign != null) {
			if (classification != RowClassification.EQUIPMENT_CHANGED) {
				throw new BusinessRuleException("ROW_REASSIGNMENT_NOT_APPLICABLE",
						"La fila " + rowNumber + " no cambia de cargador; no se reasigna");
			}
			this.reassigned = reassign;
		}
		if (markAsTest != null) {
			if (!classification.writesSession()) {
				throw new BusinessRuleException("ROW_NOT_IMPORTABLE",
						"La fila " + rowNumber + " no se incorpora; no se puede marcar como prueba");
			}
			this.test = markAsTest;
		}
		if (approve != null) {
			if (!requiresApproval()) {
				throw new BusinessRuleException("ROW_APPROVAL_NOT_REQUIRED",
						"La fila " + rowNumber + " no requiere aprobacion");
			}
			this.approved = approve;
		}
	}

	public boolean requiresApproval() {
		return classification == RowClassification.MODIFIED
				|| (classification.writesSession() && nonPreferredSource);
	}

	/**
	 * Se incorpora al confirmar si escribe una sesion, fue aprobada cuando hace falta y, si cambia de
	 * cargador, fue reasignada.
	 */
	public boolean isApplicable() {
		return classification.writesSession() && (!requiresApproval() || approved)
				&& (classification != RowClassification.EQUIPMENT_CHANGED || reassigned);
	}

	public SessionReading reading() {
		return new SessionReading(startedAt, endedAt, energyKwh, sourceSite, sourceCharger, sourceSerial,
				sourceConnector);
	}

	/**
	 * Equipo propuesto en la vista previa (solo identidad y clasificacion, que es lo que la sesion
	 * guarda), o null si quedo pendiente de vincular.
	 */
	public EquipmentReference equipment() {
		return equipmentId == null ? null : new EquipmentReference(equipmentId, network, infrastructureType, null, null);
	}

	public void recordOutcome(RowOutcome outcome, String detail) {
		this.outcome = outcome;
		this.outcomeDetail = detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500);
	}

	public UUID getId() {
		return id;
	}

	public UUID getBatchId() {
		return batchId;
	}

	public int getRowNumber() {
		return rowNumber;
	}

	public String getExternalSessionId() {
		return externalSessionId;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public BigDecimal getEnergyKwh() {
		return energyKwh;
	}

	public String getSourceSite() {
		return sourceSite;
	}

	public String getSourceCharger() {
		return sourceCharger;
	}

	public String getSourceSerial() {
		return sourceSerial;
	}

	public String getSourceConnector() {
		return sourceConnector;
	}

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public ChargingNetwork getNetwork() {
		return network;
	}

	public RowClassification getClassification() {
		return classification;
	}

	public List<RowIssue> getIssues() {
		return issues;
	}

	public boolean isNonPreferredSource() {
		return nonPreferredSource;
	}

	public boolean isTest() {
		return test;
	}

	public boolean isApproved() {
		return approved;
	}

	public UUID getExistingSessionId() {
		return existingSessionId;
	}

	public Integer getExistingSessionRevision() {
		return existingSessionRevision;
	}

	public boolean isReassigned() {
		return reassigned;
	}

	public RowOutcome getOutcome() {
		return outcome;
	}

	public String getOutcomeDetail() {
		return outcomeDetail;
	}
}
