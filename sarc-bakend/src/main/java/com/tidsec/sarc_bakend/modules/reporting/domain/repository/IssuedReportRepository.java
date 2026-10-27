package com.tidsec.sarc_bakend.modules.reporting.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.reporting.domain.model.IssuedReport;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Puerto de persistencia de los reportes emitidos. */
public interface IssuedReportRepository {

	IssuedReport save(IssuedReport report);

	Optional<IssuedReport> findById(UUID id);

	/** Historial, el mas reciente primero; {@code kind} nulo = todos. */
	PageResult<IssuedReport> search(String kind, PageQuery pageQuery);
}
