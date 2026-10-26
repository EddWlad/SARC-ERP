package com.tidsec.sarc_bakend.modules.inventory.infrastructure.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportBatch;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentImportBatchRepository;

/**
 * Al arrancar no hay trabajos de importacion vivos: una carga de equipos que quedo interpretandose
 * o aplicandose se interrumpio con la aplicacion. Se marca fallida para volver a cargarla o
 * reintentarla (las filas ya aplicadas no se repiten).
 */
@Component
class InterruptedEquipmentImportRecovery {

	private static final Logger log = LoggerFactory.getLogger(InterruptedEquipmentImportRecovery.class);

	private final EquipmentImportBatchRepository batches;

	InterruptedEquipmentImportRecovery(EquipmentImportBatchRepository batches) {
		this.batches = batches;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	void markInterrupted() {
		for (EquipmentImportBatch batch : batches.findInProgress()) {
			batch.failBecauseInterrupted();
			batches.save(batch);
			log.warn("Carga de equipos {} interrumpida por reinicio", batch.getId());
		}
	}
}
