package com.tidsec.sarc_bakend.modules.reporting.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.reporting.domain.model.IssuedReport;
import com.tidsec.sarc_bakend.modules.reporting.domain.model.IssuedReportKinds;
import com.tidsec.sarc_bakend.modules.reporting.domain.repository.IssuedReportRepository;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.error.OperationalAccessDeniedException;
import com.tidsec.sarc_bakend.shared.error.ServiceUnavailableException;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.report.ReportContentProfile;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.security.ConsumptionReadAccess;
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
	private final ConsumptionReadAccess access;

	IssuedReportService(IssuedReportRepository reports, FileStorage fileStorage, ConsumptionReadAccess access) {
		this.reports = reports;
		this.fileStorage = fileStorage;
		this.access = access;
	}

	public PageResult<IssuedReport> search(String kind, PageQuery pageQuery) {
		return reports.search(kind, pageQuery);
	}

	public boolean canReadOperational() {
		return access.canReadOperational();
	}

	/**
	 * Comprueba permiso vigente antes de abrir la copia y su huella antes de entregarla; no mantiene
	 * una transacción durante I/O.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ReportFile download(UUID id) {
		IssuedReport report = reports.findById(id)
				.orElseThrow(() -> new NotFoundException("ISSUED_REPORT_NOT_FOUND", "El reporte no existe"));
		if (IssuedReportKinds.restrictable(report.getKind()) && report.getContentProfile() != ReportContentProfile.OFFICIAL_ONLY
				&& !access.canReadOperational()) {
			throw new OperationalAccessDeniedException("REPORT_OPERATIONAL_CONTENT_RESTRICTED",
					"Este informe contiene información operativa restringida para tus roles. Puedes emitir un nuevo "
							+ "informe gerencial con su propia fecha de corte");
		}
		if (!report.isArchived()) {
			throw new NotFoundException("ISSUED_REPORT_NOT_ARCHIVED",
					"Este reporte se emitió sin copia archivada; solo se conserva su huella");
		}
		byte[] content = fileStorage.readDocument(report.getFileRef());
		if (!ContentFingerprint.sha256(content).equals(report.getChecksum())) {
			throw new ServiceUnavailableException("ISSUED_REPORT_COPY_MISMATCH",
					"La copia archivada no coincide con su huella; no se entrega");
		}
		return new ReportFile(report.getFileName(), report.getFormat(), content);
	}
}
