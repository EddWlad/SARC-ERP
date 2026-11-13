package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Fila de un lote con su vista previa editable (FR-024 a FR-029). {@code raw} guarda los valores originales y no
 * cambia nunca; las correcciones van a {@code edits} y la interpretacion se recalcula con ellas. {@code equipmentId}
 * es el punto resuelto por codigo o por eleccion explicita. Una fila KEEP_PENDING de un lote ya confirmado se
 * resuelve despues y queda con {@code resolvedBy} y {@code resolvedAt}.
 */
@Entity
@Table(name = "itm_import_row")
public class IntermittencyImportRow {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID batchId;

	@Column(nullable = false, updatable = false)
	private int rowNumber;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	private RawRow raw;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<RowEdit> edits = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private RowInterpretation interpretation;

	private UUID equipmentId;
	private UUID linkedBy;
	private Instant linkedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RowClassification classification;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private RowDecision decision;

	@Column(length = 500)
	private String skipReason;

	private UUID resolvedBy;
	private Instant resolvedAt;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "date[]")
	private List<LocalDate> touchedDays = new ArrayList<>();

	@Version
	private long version;

	protected IntermittencyImportRow() {
	}

	private IntermittencyImportRow(UUID batchId, RawRow raw) {
		this.id = UUID.randomUUID();
		this.batchId = batchId;
		this.rowNumber = raw.rowNumber();
		this.raw = raw;
	}

	public static IntermittencyImportRow read(UUID batchId, RawRow raw) {
		return new IntermittencyImportRow(batchId, raw);
	}

	/** Guarda lo que se entendio de la fila y su clasificacion; se llama despues de interpretar o clasificar. */
	public void classified(RowInterpretation newInterpretation, RowClassification newClassification,
			UUID newEquipmentId, List<LocalDate> days) {
		this.interpretation = newInterpretation;
		this.classification = newClassification;
		this.equipmentId = newEquipmentId;
		this.touchedDays = new ArrayList<>(days);
	}

	public void addEdits(List<RowEdit> newEdits) {
		List<RowEdit> all = new ArrayList<>(edits);
		all.addAll(newEdits);
		this.edits = all;
	}

	/** Vincula la fila a un punto elegido de forma explicita (FR-003). */
	public void link(UUID point, UUID actor, Instant at) {
		this.equipmentId = point;
		this.linkedBy = actor;
		this.linkedAt = at;
	}

	/** Con SKIP la razon es obligatoria (FR-028). */
	public void decide(RowDecision newDecision, String reason) {
		if (newDecision == RowDecision.SKIP && (reason == null || reason.isBlank())) {
			throw new BusinessRuleException("SKIP_REASON_REQUIRED", "Omitir una fila exige indicar el motivo");
		}
		this.decision = newDecision;
		this.skipReason = newDecision == RowDecision.SKIP ? reason.strip() : null;
	}

	public void resolved(UUID actor, Instant at) {
		this.resolvedBy = actor;
		this.resolvedAt = at;
	}

	public void requireVersion(long expected) {
		if (version != expected) {
			throw new ConflictException("CONCURRENT_MODIFICATION",
					"La fila fue modificada por otro usuario; recargue e intente de nuevo");
		}
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

	public RawRow getRaw() {
		return raw;
	}

	public List<RowEdit> getEdits() {
		return List.copyOf(edits);
	}

	public RowInterpretation getInterpretation() {
		return interpretation;
	}

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public UUID getLinkedBy() {
		return linkedBy;
	}

	public Instant getLinkedAt() {
		return linkedAt;
	}

	public RowClassification getClassification() {
		return classification;
	}

	public RowDecision getDecision() {
		return decision;
	}

	public String getSkipReason() {
		return skipReason;
	}

	public UUID getResolvedBy() {
		return resolvedBy;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public List<LocalDate> getTouchedDays() {
		return List.copyOf(touchedDays);
	}

	public long getVersion() {
		return version;
	}
}
