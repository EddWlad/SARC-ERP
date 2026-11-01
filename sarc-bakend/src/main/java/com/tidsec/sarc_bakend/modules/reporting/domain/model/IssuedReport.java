package com.tidsec.sarc_bakend.modules.reporting.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;
import com.tidsec.sarc_bakend.shared.report.ReportContentProfile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;
import com.tidsec.sarc_bakend.shared.report.ReportPublication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Reporte emitido (PRD RF-RPT-08): que se genero, con que alcance, quien y cuando (auditoria de la
 * entidad), la huella SHA-256 del archivo y la referencia a su copia privada. Sin copia, la huella
 * solo permite comprobar un archivo conservado por otra via.
 */
@Entity
@Table(name = "report_issued")
public class IssuedReport extends BaseAuditableEntity {

	public static final String AUDIT_ENTITY_TYPE = "ISSUED_REPORT";

	@Column(nullable = false, updatable = false, length = 30)
	private String kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private ReportFormat format;

	@Column(nullable = false, updatable = false)
	private String fileName;

	@Column(nullable = false, updatable = false, length = 500)
	private String scope;

	@Column(nullable = false, updatable = false, length = 64)
	private String checksum;

	@Column(nullable = false, updatable = false)
	private long sizeBytes;

	@Column(updatable = false)
	private String fileRef;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 24)
	private ReportContentProfile contentProfile;

	@Column(updatable = false)
	private Instant cutOff;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false, columnDefinition = "jsonb")
	private Map<String, String> criteria;

	protected IssuedReport() {
	}

	private IssuedReport(UUID id) {
		super(id);
	}

	public static IssuedReport issued(UUID id, String kind, ReportFormat format, String fileName, String scope,
			String checksum, long sizeBytes, String fileRef, ReportPublication publication) {
		IssuedReport report = new IssuedReport(id);
		if (publication != null) {
			report.contentProfile = publication.profile();
			report.cutOff = publication.cutOff();
			report.criteria = publication.criteria();
		}
		report.kind = kind;
		report.format = format;
		report.fileName = fileName;
		report.scope = scope.length() > 500 ? scope.substring(0, 500) : scope;
		report.checksum = checksum;
		report.sizeBytes = sizeBytes;
		report.fileRef = fileRef;
		return report;
	}

	public ReportContentProfile getContentProfile() {
		return contentProfile;
	}

	public Instant getCutOff() {
		return cutOff;
	}

	public Map<String, String> getCriteria() {
		return criteria == null ? null : Map.copyOf(criteria);
	}

	/** Hay copia del archivo tal como se entrego. */
	public boolean isArchived() {
		return fileRef != null;
	}

	public String getKind() {
		return kind;
	}

	public ReportFormat getFormat() {
		return format;
	}

	public String getFileName() {
		return fileName;
	}

	public String getScope() {
		return scope;
	}

	public String getChecksum() {
		return checksum;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getFileRef() {
		return fileRef;
	}
}
