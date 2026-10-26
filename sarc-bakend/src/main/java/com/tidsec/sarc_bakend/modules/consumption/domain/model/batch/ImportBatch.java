package com.tidsec.sarc_bakend.modules.consumption.domain.model.batch;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.importing.batch.BaseImportBatch;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Lote de importacion de sesiones (PRD 3.1): la plataforma de origen, su ambito y cuantas filas hay
 * de cada clasificacion. El ciclo de vida es el comun a todas las importaciones.
 */
@Entity
@Table(name = "energy_import_batch")
public class ImportBatch extends BaseImportBatch {

	public static final String AUDIT_ENTITY_TYPE = "ENERGY_IMPORT_BATCH";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private SessionSource source;

	@Column(nullable = false, updatable = false, length = 100)
	private String sourceScope;

	private int totalRows;
	private int newRows;
	private int identicalRows;
	private int modifiedRows;
	private int invalidRows;
	private int duplicateRows;
	private int pendingLinkRows;

	protected ImportBatch() {
	}

	private ImportBatch(UUID id, ImportOrigin origin, String fileName, String checksum, String originalRef) {
		super(id, origin, fileName, checksum, originalRef);
	}

	public static ImportBatch open(UUID id, SessionSource source, String sourceScope, ImportOrigin origin,
			String fileName, String checksum, String originalRef) {
		ImportBatch batch = new ImportBatch(id, origin, fileName, checksum, originalRef);
		batch.source = source;
		batch.sourceScope = sourceScope == null ? "" : sourceScope.strip();
		return batch;
	}

	public void previewReady(RowCounts counts) {
		markReady();
		this.totalRows = counts.total();
		this.newRows = counts.newRows();
		this.identicalRows = counts.identical();
		this.modifiedRows = counts.modified();
		this.invalidRows = counts.invalid();
		this.duplicateRows = counts.duplicate();
		this.pendingLinkRows = counts.pendingLink();
	}

	public SessionSource getSource() {
		return source;
	}

	public String getSourceScope() {
		return sourceScope;
	}

	public RowCounts getCounts() {
		return new RowCounts(totalRows, newRows, identicalRows, modifiedRows, invalidRows, duplicateRows,
				pendingLinkRows);
	}
}
