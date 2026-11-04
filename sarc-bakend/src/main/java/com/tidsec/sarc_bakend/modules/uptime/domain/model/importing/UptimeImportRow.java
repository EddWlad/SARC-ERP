package com.tidsec.sarc_bakend.modules.uptime.domain.model.importing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Observation;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.UptimeBasis;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.MeasurementRules;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Fila de un lote: identificadores tal como llegaron, lectura interpretada y decision. Equipo y
 * conector van sin clave foranea porque el destino es provisional hasta aplicar.
 */
@Entity
@Table(name = "upt_import_row")
public class UptimeImportRow {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID batchId;

	@Column(nullable = false, updatable = false)
	private int rowNumber;

	@Column(updatable = false, length = 150)
	private String sourceSite;

	@Column(updatable = false, length = 150)
	private String sourceCharger;

	@Column(updatable = false, length = 150)
	private String sourceConnector;

	private UUID equipmentId;
	private UUID connectorId;

	/** Solo en filas por vincular: conector que coincide por tipo; la persona confirma. */
	private UUID suggestedConnectorId;

	@Column(updatable = false)
	private Instant periodStart;

	@Column(updatable = false)
	private Instant periodEnd;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private PeriodType periodType;

	private Integer availableMinutes;
	private Integer occupiedMinutes;
	private Integer faultMinutes;
	private Integer offlineMinutes;

	@Column(precision = 12, scale = 4)
	private BigDecimal reportedPercent;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UptimeBasis uptimeBasis;

	@Column(updatable = false, length = 100)
	private String sourceSheet;

	@Column(updatable = false)
	private Integer sourceRow;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false, columnDefinition = "jsonb")
	private Map<String, Object> sourceValues;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UptimeRowClassification classification;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<RowIssue> issues;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<Observation> observations;

	@Column(nullable = false)
	private boolean approved;

	@Column(length = 500)
	private String justification;

	private UUID existingMeasurementId;
	private Integer existingRevision;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private UptimeRowOutcome outcome;

	private UUID appliedMeasurementId;

	@Version
	private long version;

	protected UptimeImportRow() {
	}

	private UptimeImportRow(UUID batchId, int rowNumber, String sheet, Integer sheetRow, String site, String charger,
			String connector, Instant start, Instant end, Map<String, Object> sourceValues) {
		this.id = UUID.randomUUID();
		this.batchId = batchId;
		this.rowNumber = rowNumber;
		this.sourceSheet = sheet;
		this.sourceRow = sheetRow;
		this.uptimeBasis = UptimeBasis.STATES;
		this.sourceSite = site;
		this.sourceCharger = charger;
		this.sourceConnector = connector;
		this.periodStart = start;
		this.periodEnd = end;
		this.sourceValues = sourceValues;
		this.issues = List.of();
		this.observations = List.of();
	}

	/** {@code sheet} y {@code sheetRow} solo vienen de un libro de varias hojas (D09, informe semanal). */
	public static UptimeImportRow read(UUID batchId, int rowNumber, String sheet, Integer sheetRow, String site,
			String charger, String connector, Instant start, Instant end, Map<String, Object> sourceValues) {
		return new UptimeImportRow(batchId, rowNumber, sheet, sheetRow, site, charger, connector, start, end,
				sourceValues);
	}

	public void assess(PeriodType type, MeasurementRules.Assessment assessment, BigDecimal percent) {
		periodType = type;
		availableMinutes = assessment.available();
		occupiedMinutes = assessment.occupied();
		faultMinutes = assessment.fault();
		offlineMinutes = assessment.offline();
		reportedPercent = percent;
		uptimeBasis = assessment.basis();
		observations = assessment.observations();
	}

	/** Una fila nueva entra por defecto; un conflicto solo con decision expresa. */
	public void classify(UptimeRowClassification value, UUID equipment, UUID connector, UUID existingId,
			Integer revision, List<RowIssue> rowIssues) {
		classification = value;
		equipmentId = equipment;
		connectorId = connector;
		existingMeasurementId = existingId;
		existingRevision = revision;
		issues = List.copyOf(rowIssues);
		approved = value == UptimeRowClassification.NEW;
		suggestedConnectorId = null;
	}

	/** Propone un conector a una fila por vincular sin atribuirla. */
	public void suggest(UUID connector) {
		if (classification != UptimeRowClassification.PENDING_LINK) {
			throw new BusinessRuleException("UPTIME_ROW_NOT_PENDING", "Solo se propone conector a filas por vincular.");
		}
		suggestedConnectorId = connector;
	}

	public void decide(boolean include, String reason) {
		if (classification != UptimeRowClassification.NEW && classification != UptimeRowClassification.CONFLICT) {
			throw new BusinessRuleException("UPTIME_ROW_NOT_DECIDABLE", "Solo se deciden filas nuevas o en conflicto.");
		}
		String text = reason == null ? "" : reason.strip();
		if (include && classification == UptimeRowClassification.CONFLICT && text.isEmpty()) {
			throw new BusinessRuleException("UPTIME_JUSTIFICATION_REQUIRED",
					"Justifica por qué esta fila reemplaza la medición existente.");
		}
		approved = include;
		justification = text.isEmpty() ? null : text;
	}

	public void recordOutcome(UptimeRowOutcome value, UUID measurementId) {
		outcome = value;
		appliedMeasurementId = measurementId;
	}

	/** Lo registrado cambio despues de la vista previa: la decision de la persona ya no aplica. */
	public void reject(RowIssue issue) {
		List<RowIssue> updated = new ArrayList<>(issues);
		updated.add(issue);
		issues = List.copyOf(updated);
		recordOutcome(UptimeRowOutcome.FAILED, null);
	}

	public void requireVersion(long expected) {
		if (version != expected) {
			throw new ConflictException("CONCURRENT_MODIFICATION", "La fila cambio; vuelve a cargarla.");
		}
	}

	public void requirePendingLink() {
		if (outcome != UptimeRowOutcome.PENDING_LINK) {
			throw new ConflictException("UPTIME_ROW_NOT_PENDING", "La fila ya no espera vinculacion.");
		}
	}

	public UUID getId() { return id; }
	public UUID getBatchId() { return batchId; }
	public int getRowNumber() { return rowNumber; }
	public String getSourceSite() { return sourceSite; }
	public String getSourceSheet() { return sourceSheet; }
	public Integer getSourceRow() { return sourceRow; }
	public UptimeBasis getUptimeBasis() { return uptimeBasis; }
	public String getSourceCharger() { return sourceCharger; }
	public String getSourceConnector() { return sourceConnector; }
	public UUID getEquipmentId() { return equipmentId; }
	public UUID getConnectorId() { return connectorId; }
	public UUID getSuggestedConnectorId() { return suggestedConnectorId; }
	public Instant getPeriodStart() { return periodStart; }
	public Instant getPeriodEnd() { return periodEnd; }
	public PeriodType getPeriodType() { return periodType; }
	public Integer getAvailableMinutes() { return availableMinutes; }
	public Integer getOccupiedMinutes() { return occupiedMinutes; }
	public Integer getFaultMinutes() { return faultMinutes; }
	public Integer getOfflineMinutes() { return offlineMinutes; }
	public BigDecimal getReportedPercent() { return reportedPercent; }
	public Map<String, Object> getSourceValues() { return sourceValues; }
	public UptimeRowClassification getClassification() { return classification; }
	public List<RowIssue> getIssues() { return issues; }
	public List<Observation> getObservations() { return observations; }
	public boolean isApproved() { return approved; }
	public String getJustification() { return justification; }
	public UUID getExistingMeasurementId() { return existingMeasurementId; }
	public Integer getExistingRevision() { return existingRevision; }
	public UptimeRowOutcome getOutcome() { return outcome; }
	public UUID getAppliedMeasurementId() { return appliedMeasurementId; }
	public long getVersion() { return version; }
}
