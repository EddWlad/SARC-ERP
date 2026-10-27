package com.tidsec.sarc_bakend.modules.reporting.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.reporting.domain.model.IssuedReport;
import com.tidsec.sarc_bakend.modules.reporting.domain.repository.IssuedReportRepository;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.error.ServiceUnavailableException;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/**
 * Consulta de reportes emitidos y descarga de su copia tal como se entrego. Los mismos roles que
 * pueden generar un reporte pueden volver a verlo.
 */
@Service
@Transactional(readOnly = true)
public class IssuedReportService {

	private final IssuedReportRepository reports;
	private final FileStorage fileStorage;

	IssuedReportService(IssuedReportRepository reports, FileStorage fileStorage) {
		this.reports = reports;
		this.fileStorage = fileStorage;
	}

	public PageResult<IssuedReport> search(String kind, PageQuery pageQuery) {
		return reports.search(kind, pageQuery);
	}

	/** URL firmada y temporal de la copia archivada. */
	public String downloadUrl(UUID id) {
		IssuedReport report = reports.findById(id)
				.orElseThrow(() -> new NotFoundException("ISSUED_REPORT_NOT_FOUND", "El reporte no existe"));
		if (!report.isArchived()) {
			throw new NotFoundException("ISSUED_REPORT_NOT_ARCHIVED",
					"Este reporte se emitio sin copia archivada; solo se conserva su huella");
		}
		return fileStorage.documentUrl(report.getFileRef())
				.orElseThrow(() -> new ServiceUnavailableException(FileStorage.NOT_CONFIGURED,
						"El almacenamiento de archivos no esta configurado en este entorno"));
	}
}
