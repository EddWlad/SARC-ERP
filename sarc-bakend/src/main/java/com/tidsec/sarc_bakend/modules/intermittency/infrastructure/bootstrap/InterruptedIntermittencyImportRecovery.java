package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportBatch;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.IntermittencyImportRepository;

/** Al arrancar no hay trabajos vivos: un lote en curso se interrumpio y queda listo para reintentar. */
@Component
class InterruptedIntermittencyImportRecovery {

	private static final Logger log = LoggerFactory.getLogger(InterruptedIntermittencyImportRecovery.class);

	private final IntermittencyImportRepository imports;

	InterruptedIntermittencyImportRecovery(IntermittencyImportRepository imports) {
		this.imports = imports;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	void markInterrupted() {
		for (IntermittencyImportBatch batch : imports.findBatchesInProgress()) {
			batch.failBecauseInterrupted();
			imports.saveBatch(batch);
			log.warn("Lote de intermitencias {} interrumpido por reinicio", batch.getId());
		}
	}
}
