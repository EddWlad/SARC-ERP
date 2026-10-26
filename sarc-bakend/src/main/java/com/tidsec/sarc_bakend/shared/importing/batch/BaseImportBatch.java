package com.tidsec.sarc_bakend.shared.importing.batch;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

/**
 * Lote de importacion de cualquier modulo (PRD 3.1): referencia al original y su huella SHA-256,
 * etapa del ciclo interpretar, revisar, confirmar, y quien lo confirmo. Cada modulo agrega lo propio
 * de su fuente y los conteos de sus clasificaciones. Las transiciones invalidas se rechazan.
 */
@MappedSuperclass
public abstract class BaseImportBatch extends BaseAuditableEntity {

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

	private int appliedRows;

	private Instant confirmedAt;

	private UUID confirmedBy;

	protected BaseImportBatch() {
	}

	/** Lote recien cargado: queda interpretandose. */
	protected BaseImportBatch(UUID id, ImportOrigin origin, String fileName, String checksum, String originalRef) {
		super(id);
		this.origin = origin;
		this.fileName = fileName;
		this.checksum = checksum;
		this.originalRef = originalRef;
		this.status = ImportStatus.PARSING;
	}

	/** La vista previa quedo lista; cada modulo guarda antes sus conteos. */
	protected void markReady() {
		requireStatus(ImportStatus.PARSING);
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
		if (!isInProgress()) {
			throw new BusinessRuleException("IMPORT_INVALID_TRANSITION", "El lote no esta en proceso");
		}
		this.status = ImportStatus.FAILED;
		this.failureCode = code;
		this.failureMessage = message.length() > 500 ? message.substring(0, 500) : message;
	}

	/** La aplicacion se reinicio con el lote en proceso: ningun trabajo lo va a terminar. */
	public void failBecauseInterrupted() {
		fail("IMPORT_INTERRUPTED", confirmedAt == null
				? "La lectura se interrumpio al reiniciar el sistema; vuelva a cargar el archivo"
				: "La confirmacion se interrumpio al reiniciar el sistema; reintentela para continuar");
	}

	/** La cola de trabajos estaba llena: el lote falla de inmediato para que se reintente luego. */
	public void failBecauseBusy() {
		fail("IMPORT_BUSY", confirmedAt == null
				? "El sistema esta procesando otras importaciones; vuelva a cargar el archivo en unos minutos"
				: "El sistema esta procesando otras importaciones; reintente la confirmacion en unos minutos");
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
