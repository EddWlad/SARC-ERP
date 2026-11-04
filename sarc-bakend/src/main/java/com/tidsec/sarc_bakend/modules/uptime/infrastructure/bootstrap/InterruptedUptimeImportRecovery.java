package com.tidsec.sarc_bakend.modules.uptime.infrastructure.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportBatch;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.UptimeImportRepository;

/** Al arrancar no hay trabajos vivos: un lote en curso se interrumpio y queda listo para reintentar. */
@Component
class InterruptedUptimeImportRecovery {

	private static final Logger log = LoggerFactory.getLogger(InterruptedUptimeImportRecovery.class);

	private final UptimeImportRepository imports;

	InterruptedUptimeImportRecovery(UptimeImportRepository imports) {
		this.imports = imports;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	void markInterrupted() {
		for (UptimeImportBatch batch : imports.findBatchesInProgress()) {
			batch.failBecauseInterrupted();
			imports.saveBatch(batch);
			log.warn("Lote de uptime {} interrumpido por reinicio", batch.getId());
		}
	}
}
