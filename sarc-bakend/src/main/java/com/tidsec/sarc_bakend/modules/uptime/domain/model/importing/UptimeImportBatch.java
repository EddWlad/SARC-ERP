package com.tidsec.sarc_bakend.modules.uptime.domain.model.importing;

import java.time.Instant;
import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementPeriod;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.shared.importing.batch.BaseImportBatch;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Lote de uptime. El tipo de ventana lo declara la persona; nunca se deduce del archivo. */
@Entity
@Table(name = "upt_import_batch")
public class UptimeImportBatch extends BaseImportBatch {

	public static final String AUDIT_ENTITY_TYPE = "UPTIME_IMPORT_BATCH";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private MeasurementSource source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private PeriodType declaredPeriodType;

	/** Semana elegida al cargar el informe semanal; vacia si el periodo viene en cada fila. */
	@Column(updatable = false)
	private Instant declaredPeriodStart;

	@Column(updatable = false)
	private Instant declaredPeriodEnd;

	private int totalRows;
	private int newRows;
	private int identicalRows;
	private int conflictRows;
	private int invalidRows;
	private int pendingLinkRows;
	private int observedRows;
	private UUID exactConfirmedBy;
	private Instant exactConfirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_precision", nullable = false, length = 32)
    private TemporalPrecision temporalPrecision = TemporalPrecision.KNOWN_RANGE;
    private LocalDate referenceWeek;

    public void useReference(LocalDate week) {
        MeasurementPeriod.reference(week,"America/Guayaquil");
        referenceWeek=week;temporalPrecision=TemporalPrecision.REFERENCE_WEEK;
    }
    public TemporalPrecision getTemporalPrecision() { return temporalPrecision; }
    public LocalDate getReferenceWeek() { return referenceWeek; }

	protected UptimeImportBatch() {
	}

	private UptimeImportBatch(UUID id, ImportOrigin origin, String fileName, String checksum, String originalRef) {
		super(id, origin, fileName, checksum, originalRef);
	}

	public static UptimeImportBatch open(UUID id, MeasurementSource source, PeriodType declaredPeriodType,
			ImportOrigin origin, String fileName, String checksum, String originalRef) {
		UptimeImportBatch batch = new UptimeImportBatch(id, origin, fileName, checksum, originalRef);
		batch.source = source;
		batch.declaredPeriodType = declaredPeriodType;
		return batch;
	}

	/** Informe semanal: todas las filas son de la semana elegida al cargar. */
	public static UptimeImportBatch openWeekly(UUID id, MeasurementSource source, Instant weekStart, Instant weekEnd,
			String fileName, String checksum, String originalRef) {
		UptimeImportBatch batch = open(id, source, PeriodType.ROLLING_7D, ImportOrigin.FILE, fileName, checksum,
				originalRef);
		batch.declaredPeriodStart = weekStart;
		batch.declaredPeriodEnd = weekEnd;
		return batch;
	}

    public static UptimeImportBatch openWeeklyReference(UUID id,MeasurementSource source,LocalDate week,
            String fileName,String checksum,String originalRef) {
        var batch=open(id,source,PeriodType.ROLLING_7D,ImportOrigin.FILE,fileName,checksum,originalRef);
        batch.useReference(week);
        return batch;
    }

	public void previewReady(UptimeRowCounts counts) {
		markReady();
		totalRows = counts.total();
		newRows = counts.newRows();
		identicalRows = counts.identical();
		conflictRows = counts.conflict();
		invalidRows = counts.invalid();
		pendingLinkRows = counts.pendingLink();
		observedRows = counts.observed();
	}

	/** Una persona afirma que el lote es una semana exacta; solo vale para filas alineadas. */
	public void confirmExact(UUID actor, Instant at) {
		exactConfirmedBy = actor;
		exactConfirmedAt = at;
	}

	public boolean isExactConfirmed() {
		return exactConfirmedAt != null;
	}

	public MeasurementSource getSource() {
		return source;
	}

	public PeriodType getDeclaredPeriodType() {
		return declaredPeriodType;
	}

	public Instant getDeclaredPeriodStart() {
		return declaredPeriodStart;
	}

	public Instant getDeclaredPeriodEnd() {
		return declaredPeriodEnd;
	}

	public UptimeRowCounts getCounts() {
		return new UptimeRowCounts(totalRows, newRows, identicalRows, conflictRows, invalidRows, pendingLinkRows,
				observedRows);
	}

	public UUID getExactConfirmedBy() {
		return exactConfirmedBy;
	}

	public Instant getExactConfirmedAt() {
		return exactConfirmedAt;
	}
}
