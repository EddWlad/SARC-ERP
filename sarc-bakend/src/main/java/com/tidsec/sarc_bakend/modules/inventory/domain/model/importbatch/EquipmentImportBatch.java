package com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch;

import java.util.UUID;

import com.tidsec.sarc_bakend.shared.importing.batch.BaseImportBatch;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Lote de carga masiva de equipos: el ciclo comun de importacion y los conteos de sus filas. */
@Entity
@Table(name = "asset_import_batch")
public class EquipmentImportBatch extends BaseImportBatch {

	public static final String AUDIT_ENTITY_TYPE = "EQUIPMENT_IMPORT_BATCH";

	private int totalRows;
	private int newRows;
	private int identicalRows;
	private int modifiedRows;
	private int invalidRows;
	private int duplicateRows;
	private int deletedExistsRows;
	private int conflictRows;

	protected EquipmentImportBatch() {
	}

	private EquipmentImportBatch(UUID id, ImportOrigin origin, String fileName, String checksum, String originalRef) {
		super(id, origin, fileName, checksum, originalRef);
	}

	public static EquipmentImportBatch open(UUID id, ImportOrigin origin, String fileName, String checksum,
			String originalRef) {
		return new EquipmentImportBatch(id, origin, fileName, checksum, originalRef);
	}

	public void previewReady(EquipmentRowCounts counts) {
		markReady();
		this.totalRows = counts.total();
		this.newRows = counts.newRows();
		this.identicalRows = counts.identical();
		this.modifiedRows = counts.modified();
		this.invalidRows = counts.invalid();
		this.duplicateRows = counts.duplicate();
		this.deletedExistsRows = counts.deletedExists();
		this.conflictRows = counts.conflict();
	}

	public EquipmentRowCounts getCounts() {
		return new EquipmentRowCounts(totalRows, newRows, identicalRows, modifiedRows, invalidRows, duplicateRows,
				deletedExistsRows, conflictRows);
	}
}
