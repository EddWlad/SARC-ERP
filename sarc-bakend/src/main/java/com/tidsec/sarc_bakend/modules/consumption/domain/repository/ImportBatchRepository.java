package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportBatch;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

public interface ImportBatchRepository {

	ImportBatch save(ImportBatch batch);

	Optional<ImportBatch> findById(UUID id);

	/** Historial, el mas reciente primero. */
	PageResult<ImportBatch> search(PageQuery pageQuery);

	/** Lotes que quedaron interpretandose o confirmandose (p. ej. si la aplicacion se detuvo). */
	List<ImportBatch> findInProgress();
}
