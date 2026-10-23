package com.tidsec.sarc_bakend.modules.consumption.domain.model.batch;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Lote de importacion (PRD 3.1): conserva la referencia al original y su huella SHA-256, quien lo
 * cargo, la clasificacion de sus filas y en que etapa esta. Las transiciones invalidas se rechazan.
 */
@Entity
@Table(name = "energy_import_batch")
public class ImportBatch extends BaseAuditableEntity {

	public static final String AUDIT_ENTITY_TYPE = "ENERGY_IMPORT_BATCH";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private SessionSource source;

	@Column(nullable = false, updatable = false, length = 100)
	private String sourceScope;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private ImportOrigin origin;

	@Column(updatable = false)
	private String fileName;

	@Column(nullable = false, updatable = false, length = 64)
	private String checksum;

	@Column(nullable = false, updatable = false)
	private String originalRef;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ImportStatus status;

	@Column(length = 60)
	private String failureCode;

	@Column(length = 500)
	private String failureMessage;

	private int totalRows;
	private int newRows;
	private int identicalRows;
	private int modifiedRows;
	private int invalidRows;
	private int duplicateRows;
	private int pendingLinkRows;
	private int appliedRows;

	private Instant confirmedAt;

	private UUID confirmedBy;

	protected ImportBatch() {
	}

	private ImportBatch(UUID id) {
		super(id);
	}

	public static ImportBatch open(UUID id, SessionSource source, String sourceScope, ImportOrigin origin,
			String fileName, String checksum, String originalRef) {
		ImportBatch batch = new ImportBatch(id);
		batch.source = source;
		batch.sourceScope = sourceScope == null ? "" : sourceScope.strip();
		batch.origin = origin;
		batch.fileName = fileName;
		batch.checksum = checksum;
		batch.originalRef = originalRef;
		batch.status = ImportStatus.PARSING;
		return batch;
	}

	public void previewReady(RowCounts counts) {
		requireStatus(ImportStatus.PARSING);
		this.totalRows = counts.total();
		this.newRows = counts.newRows();
		this.identicalRows = counts.identical();
		this.modifiedRows = counts.modified();
		this.invalidRows = counts.invalid();
		this.duplicateRows = counts.duplicate();
		this.pendingLinkRows = counts.pendingLink();
		this.status = ImportStatus.READY;
	}

	/** Las decisiones sobre las filas solo se toman mientras la vista previa esta abierta. */
	public void requireReviewable() {
		requireStatus(ImportStatus.READY);
	}

	public void confirm(UUID actorId, Instant at) {
		requireStatus(ImportStatus.READY);
		this.status = ImportStatus.CONFIRMING;
		this.confirmedAt = at;
		this.confirmedBy = actorId;
	}

	/** Reanuda una confirmacion interrumpida; las filas ya incorporadas no se repiten. */
	public void retry() {
		if (status != ImportStatus.FAILED || confirmedAt == null) {
			throw new BusinessRuleException("IMPORT_NOT_RETRYABLE",
					"Solo se reintenta un lote que fallo al confirmarse; si fallo al leerse, vuelva a cargarlo");
		}
		this.status = ImportStatus.CONFIRMING;
		this.failureCode = null;
		this.failureMessage = null;
	}

	public void complete(int appliedRows) {
		requireStatus(ImportStatus.CONFIRMING);
		this.appliedRows = appliedRows;
		this.status = ImportStatus.COMPLETED;
	}

	public void fail(String code, String message) {
		if (status != ImportStatus.PARSING && status != ImportStatus.CONFIRMING) {
			throw new BusinessRuleException("IMPORT_INVALID_TRANSITION", "El lote no esta en proceso");
		}
		this.status = ImportStatus.FAILED;
		this.failureCode = code;
		this.failureMessage = message.length() > 500 ? message.substring(0, 500) : message;
	}

	public void discard() {
		if (status != ImportStatus.READY && !(status == ImportStatus.FAILED && confirmedAt == null)) {
			throw new BusinessRuleException("IMPORT_NOT_DISCARDABLE",
					"Solo se descarta un lote en revision o que fallo antes de confirmarse");
		}
		this.status = ImportStatus.DISCARDED;
	}

	public boolean isInProgress() {
		return status == ImportStatus.PARSING || status == ImportStatus.CONFIRMING;
	}

	private void requireStatus(ImportStatus expected) {
		if (status != expected) {
			throw new BusinessRuleException("IMPORT_INVALID_TRANSITION",
					"El lote esta en estado " + status + " y no admite esta operacion");
		}
	}

	public SessionSource getSource() {
		return source;
	}

	public String getSourceScope() {
		return sourceScope;
	}

	public ImportOrigin getOrigin() {
		return origin;
	}

	public String getFileName() {
		return fileName;
	}

	public String getChecksum() {
		return checksum;
	}

	public ImportStatus getStatus() {
		return status;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public String getFailureMessage() {
		return failureMessage;
	}

	public RowCounts getCounts() {
		return new RowCounts(totalRows, newRows, identicalRows, modifiedRows, invalidRows, duplicateRows,
				pendingLinkRows);
	}

	public int getAppliedRows() {
		return appliedRows;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public UUID getConfirmedBy() {
		return confirmedBy;
	}
}
