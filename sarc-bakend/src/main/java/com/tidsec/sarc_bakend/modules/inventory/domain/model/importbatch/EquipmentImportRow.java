package com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch;

import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la vista previa de una carga masiva de equipos: celdas originales, clasificacion,
 * observaciones y, si toca un equipo existente, cual y en que version se reviso. Despues registra
 * que paso con ella al confirmar.
 */
@Entity
@Table(name = "asset_import_row")
public class EquipmentImportRow {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID batchId;

	@Column(nullable = false, updatable = false)
	private int rowNumber;

	@Embedded
	private EquipmentLine line;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private EquipmentRowClassification classification;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	private List<RowIssue> issues;

	@Column(updatable = false)
	private UUID equipmentId;

	@Column(updatable = false)
	private Long equipmentVersion;

	@Column(nullable = false)
	private boolean approved;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private EquipmentRowOutcome outcome;

	@Column(length = 500)
	private String outcomeDetail;

	protected EquipmentImportRow() {
	}

	private EquipmentImportRow(UUID batchId, int rowNumber, EquipmentLine line,
			EquipmentRowClassification classification, List<RowIssue> issues) {
		this.id = UUID.randomUUID();
		this.batchId = batchId;
		this.rowNumber = rowNumber;
		this.line = line;
		this.classification = classification;
		this.issues = List.copyOf(issues);
	}

	/** Fila que no toca un equipo existente (nueva, invalida o duplicada). */
	public static EquipmentImportRow classified(UUID batchId, int rowNumber, EquipmentLine line,
			EquipmentRowClassification classification, List<RowIssue> issues) {
		return new EquipmentImportRow(batchId, rowNumber, line, classification, issues);
	}

	/** Fila que corresponde a un equipo existente, revisado en la version {@code equipmentVersion}. */
	public static EquipmentImportRow forExisting(UUID batchId, int rowNumber, EquipmentLine line,
			EquipmentRowClassification classification, List<RowIssue> issues, UUID equipmentId,
			long equipmentVersion) {
		EquipmentImportRow row = new EquipmentImportRow(batchId, rowNumber, line, classification, issues);
		row.equipmentId = equipmentId;
		row.equipmentVersion = equipmentVersion;
		return row;
	}

	/** Solo las actualizaciones de equipos existentes requieren aprobacion. */
	public boolean requiresApproval() {
		return classification == EquipmentRowClassification.MODIFIED;
	}

	public void decide(boolean approve) {
		if (!requiresApproval()) {
			throw new BusinessRuleException("ROW_APPROVAL_NOT_REQUIRED",
					"La fila " + rowNumber + " no requiere aprobacion");
		}
		this.approved = approve;
	}

	/** Se aplica al confirmar si escribe en el inventario y, cuando hace falta, fue aprobada. */
	public boolean isApplicable() {
		return classification.writesEquipment() && (!requiresApproval() || approved);
	}

	public void recordOutcome(EquipmentRowOutcome outcome, String detail) {
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

	public EquipmentLine getLine() {
		return line;
	}

	public EquipmentRowClassification getClassification() {
		return classification;
	}

	public List<RowIssue> getIssues() {
		return issues;
	}

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public Long getEquipmentVersion() {
		return equipmentVersion;
	}

	public boolean isApproved() {
		return approved;
	}

	public EquipmentRowOutcome getOutcome() {
		return outcome;
	}

	public String getOutcomeDetail() {
		return outcomeDetail;
	}
}
