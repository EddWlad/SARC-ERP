package com.tidsec.sarc_bakend.modules.reporting.application.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.reporting.domain.model.IssuedReport;
import com.tidsec.sarc_bakend.modules.reporting.domain.repository.IssuedReportRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.catalog.ReportArchive;
import com.tidsec.sarc_bakend.shared.error.ServiceUnavailableException;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/**
 * Archiva cada reporte emitido: sube la copia al almacenamiento privado fuera de la transaccion y
 * luego registra la emision y su auditoria juntas.
 */
@Service
class ReportArchiveService implements ReportArchive {

	private static final Logger log = LoggerFactory.getLogger(ReportArchiveService.class);
	private static final String STORAGE_FOLDER = "reports/";

	private final IssuedReportRepository reports;
	private final FileStorage fileStorage;
	private final AuditTrail audit;
	private final TransactionTemplate transactions;

	ReportArchiveService(IssuedReportRepository reports, FileStorage fileStorage, AuditTrail audit,
			TransactionTemplate transactions) {
		this.reports = reports;
		this.fileStorage = fileStorage;
		this.audit = audit;
		this.transactions = transactions;
	}

	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public UUID archive(IssuedReportFile issued) {
		ReportFile file = issued.file();
		UUID id = UUID.randomUUID();
		String checksum = ContentFingerprint.sha256(file.content());
		String reference = store(id, issued);
		transactions.executeWithoutResult(status -> {
			reports.save(IssuedReport.issued(id, issued.kind(), file.format(), file.filename(), issued.scope(), checksum,
					file.content().length, reference));
			Map<String, String> details = new HashMap<>(issued.details());
			details.put("format", file.format().name());
			details.put("filters", issued.scope());
			details.put("sha256", checksum);
			details.put("archived", String.valueOf(reference != null));
			audit.record(issued.kind() + "_REPORT_GENERATED", IssuedReport.AUDIT_ENTITY_TYPE, id, details);
		});
		return id;
	}

	/** Sin almacenamiento, el reporte se entrega igual y la emision queda con su huella. */
	private String store(UUID id, IssuedReportFile issued) {
		try {
			return fileStorage.storeDocument(STORAGE_FOLDER + issued.kind().toLowerCase() + "/" + id,
					issued.file().filename(), issued.file().content());
		}
		catch (ServiceUnavailableException ex) {
			log.warn("No se pudo archivar el reporte {} ({}): queda registrada solo su huella", id, ex.code());
			return null;
		}
	}
}
