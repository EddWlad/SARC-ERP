package com.tidsec.sarc_bakend.modules.consumption.infrastructure.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportBatch;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.ImportBatchRepository;

/**
 * Al arrancar no hay trabajos de importacion vivos: un lote que quedo interpretandose o
 * confirmandose se interrumpio con la aplicacion. Se marca fallido para que el analista lo vuelva a
 * cargar o lo reintente (TRD 09.3: reanudar sin duplicar).
 */
@Component
class InterruptedImportRecovery {

	private static final Logger log = LoggerFactory.getLogger(InterruptedImportRecovery.class);

	private final ImportBatchRepository batches;

	InterruptedImportRecovery(ImportBatchRepository batches) {
		this.batches = batches;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	void markInterrupted() {
		for (ImportBatch batch : batches.findInProgress()) {
			batch.failBecauseInterrupted();
			batches.save(batch);
			log.warn("Lote de importacion {} interrumpido por reinicio", batch.getId());
		}
	}
}
