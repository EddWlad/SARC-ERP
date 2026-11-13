package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

import java.time.LocalDate;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.importing.batch.BaseImportBatch;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Lote de intermitencias: el original se guarda con su huella SHA-256 y las filas se revisan antes de confirmar. */
@Entity
@Table(name = "itm_import_batch")
public class IntermittencyImportBatch extends BaseImportBatch {

	public static final String AUDIT_ENTITY_TYPE = "INTERMITTENCY_IMPORT_BATCH";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private ImportFormat format;

	private int totalRows;
	private int totalWindows;
	private int newCount;
	private int identicalCount;
	private int conflictCount;
	private int invalidCount;
	private int pendingLinkCount;
	private int outOfScopeCount;
	private int reviewCount;
	private int skippedCount;
	private LocalDate earliestDay;
	private LocalDate latestDay;

	protected IntermittencyImportBatch() {
	}

	private IntermittencyImportBatch(UUID id, ImportFormat format, ImportOrigin origin, String fileName,
			String checksum, String originalRef) {
		super(id, origin, fileName, checksum, originalRef);
		this.format = format;
	}

	public static IntermittencyImportBatch open(UUID id, ImportFormat format, String fileName, String checksum,
			String originalRef) {
		return new IntermittencyImportBatch(id, format,
				format == ImportFormat.PASTE ? ImportOrigin.PASTE : ImportOrigin.FILE, fileName, checksum,
				originalRef);
	}

	/** La vista previa quedo lista. */
	public void previewReady(RowCounts counts, LocalDate earliest, LocalDate latest) {
		markReady();
		recount(counts, earliest, latest);
	}

	/** Los conteos se recalculan despues de cada edicion o decision. */
	public void recount(RowCounts counts, LocalDate earliest, LocalDate latest) {
		this.totalRows = counts.total();
		this.totalWindows = counts.windows();
		this.newCount = counts.newRows();
		this.identicalCount = counts.identical();
		this.conflictCount = counts.conflict();
		this.invalidCount = counts.invalid();
		this.pendingLinkCount = counts.pendingLink();
		this.outOfScopeCount = counts.outOfScope();
		this.reviewCount = counts.review();
		this.skippedCount = counts.skipped();
		this.earliestDay = earliest;
		this.latestDay = latest;
	}

	public RowCounts getCounts() {
		return new RowCounts(totalRows, totalWindows, newCount, identicalCount, conflictCount, invalidCount,
				pendingLinkCount, outOfScopeCount, reviewCount, skippedCount);
	}

	public ImportFormat getFormat() {
		return format;
	}

	public LocalDate getEarliestDay() {
		return earliestDay;
	}

	public LocalDate getLatestDay() {
		return latestDay;
	}
}
