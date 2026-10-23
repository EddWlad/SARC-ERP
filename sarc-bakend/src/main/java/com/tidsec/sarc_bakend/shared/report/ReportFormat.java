package com.tidsec.sarc_bakend.shared.report;

/** Formatos de exportacion de reportes (PRD 11: PDF corporativo y Excel con el detalle). */
public enum ReportFormat {

	PDF("application/pdf", "pdf"),
	XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

	private final String contentType;
	private final String extension;

	ReportFormat(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	public String contentType() {
		return contentType;
	}

	public String extension() {
		return extension;
	}
}
