package com.tidsec.sarc_bakend.modules.reporting.api.response;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.reporting.domain.model.IssuedReport;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

import io.swagger.v3.oas.annotations.media.Schema;

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
		@Schema(nullable = true) UUID issuedBy) {

	public static IssuedReportResponse from(IssuedReport report) {
		return new IssuedReportResponse(report.getId(), report.getKind(), report.getFormat(), report.getFileName(),
				report.getScope(), report.getChecksum(), report.getSizeBytes(), report.isArchived(),
				report.getCreatedAt(), report.getCreatedBy());
	}
}
