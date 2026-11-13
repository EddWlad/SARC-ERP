package com.tidsec.sarc_bakend.modules.reporting.api.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.tidsec.sarc_bakend.modules.reporting.domain.model.IssuedReport;
import com.tidsec.sarc_bakend.modules.reporting.domain.model.IssuedReportKinds;
import com.tidsec.sarc_bakend.shared.report.ReportContentProfile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** Reporte emitido: alcance, quien y cuando, huella y si hay copia para descargar. */
public record IssuedReportResponse(
		UUID id,
		String kind,
		ReportFormat format,
		String fileName,
		String scope,
		String checksum,
		long sizeBytes,
		boolean archived,
		Instant issuedAt,
		@Schema(nullable = true) UUID issuedBy,
		boolean canDownload,
		@Schema(nullable = true) String accessReason,
		boolean canIssueManagerial,
		@Schema(nullable = true) Instant cutOff,
		@Schema(nullable = true) Map<String, String> criteria) {

	public static IssuedReportResponse from(IssuedReport report, boolean operational) {
		boolean restrictable = IssuedReportKinds.restrictable(report.getKind());
		boolean restricted = restrictable && report.getContentProfile() != ReportContentProfile.OFFICIAL_ONLY
				&& !operational;
		String accessReason = restricted ? "REPORT_OPERATIONAL_CONTENT_RESTRICTED"
				: report.isArchived() ? null : "ISSUED_REPORT_NOT_ARCHIVED";
		return new IssuedReportResponse(report.getId(), report.getKind(), report.getFormat(), report.getFileName(),
				report.getScope(), report.getChecksum(), report.getSizeBytes(), report.isArchived(),
				report.getCreatedAt(), report.getCreatedBy(), report.isArchived() && !restricted, accessReason,
				restrictable && !operational, report.getCutOff(), report.getCriteria());
	}
}
