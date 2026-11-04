package com.tidsec.sarc_bakend.modules.uptime.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportBatch;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.UptimeImportRepository;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus;

class InterruptedUptimeImportRecoveryTest {

	@Test
	void batchInProgressAtStartupEndsFailed() {
		UptimeImportRepository imports = mock(UptimeImportRepository.class);
		UptimeImportBatch batch = UptimeImportBatch.open(UUID.randomUUID(), MeasurementSource.ENERLINK,
				PeriodType.ROLLING_24H, ImportOrigin.PASTE, "pegado.txt", "sha", "ref");
		when(imports.findBatchesInProgress()).thenReturn(List.of(batch));

		new InterruptedUptimeImportRecovery(imports).markInterrupted();

		assertThat(batch.getStatus()).isEqualTo(ImportStatus.FAILED);
		verify(imports).saveBatch(batch);
	}
}
