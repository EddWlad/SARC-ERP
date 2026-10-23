package com.tidsec.sarc_bakend.modules.consumption.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportRow;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowClassification;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionRevision;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.modules.consumption.domain.policy.PreferredSourcePolicy;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

class EnergySessionTest {

	private static final OperatingZone ECUADOR = new OperatingZone(ZoneId.of("America/Guayaquil"));
	private static final EquipmentReference CONECTZ_STATION = new EquipmentReference(UUID.randomUUID(),
			ChargingNetwork.CONECTZ, InfrastructureType.CHARGING_STATION, "Estacion 4", "ST-4");
	private static final UUID BATCH = UUID.randomUUID();

	private static SessionReading reading(String start, String end, String kwh) {
		return new SessionReading(Instant.parse(start), Instant.parse(end), new BigDecimal(kwh), "Conectz",
				"ESTACION 4", null, "A - CCS1");
	}

	@Test
	void countsOnTheLocalDayItStartedEvenIfItEndsTheNextDay() {
		// CA-003-03: inicia el 20/oct 23:30 hora de Ecuador y termina el 21/oct.
		EnergySession session = EnergySession.record(SessionSource.ENERLINK, "", "1",
				reading("2026-10-21T04:30:00Z", "2026-10-21T06:00:00Z", "12.5"), CONECTZ_STATION, false, BATCH,
				ECUADOR);

		assertThat(session.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(session.getState()).isEqualTo(SessionState.VALIDATED);
		assertThat(session.getNetwork()).isEqualTo(ChargingNetwork.CONECTZ);
	}

	@Test
	void unknownEquipmentStaysPendingAndTestSessionsAreExcluded() {
		EnergySession pending = EnergySession.record(SessionSource.ENERLINK, "", "2",
				reading("2026-10-21T15:00:00Z", "2026-10-21T15:30:00Z", "0"), null, false, BATCH, ECUADOR);
		assertThat(pending.getState()).isEqualTo(SessionState.PENDING_LINK);
		assertThat(pending.getNetwork()).isNull();

		pending.markTest(true);
		assertThat(pending.getState()).isEqualTo(SessionState.TEST);
		pending.markTest(false);
		assertThat(pending.getState()).isEqualTo(SessionState.PENDING_LINK);

		pending.link(CONECTZ_STATION);
		assertThat(pending.getState()).isEqualTo(SessionState.VALIDATED);
		assertThatThrownBy(() -> pending.link(CONECTZ_STATION)).extracting("code").isEqualTo("SESSION_ALREADY_LINKED");
	}

	@Test
	void correctionKeepsThePreviousVersion() {
		EnergySession session = EnergySession.record(SessionSource.ENERLINK, "", "3",
				reading("2026-10-21T15:00:00Z", "2026-10-21T15:30:00Z", "10.000"), CONECTZ_STATION, false, BATCH,
				ECUADOR);
		assertThat(session.hasSameReading(reading("2026-10-21T15:00:00Z", "2026-10-21T15:30:00Z", "10"))).isTrue();

		UUID secondBatch = UUID.randomUUID();
		SessionRevision previous = session.correct(reading("2026-10-21T15:00:00Z", "2026-10-21T15:40:00Z", "11"),
				secondBatch, ECUADOR, null, Instant.parse("2026-10-22T00:00:00Z"));

		assertThat(previous.getRevision()).isEqualTo(1);
		assertThat(previous.getEnergyKwh()).isEqualByComparingTo("10");
		assertThat(session.getRevision()).isEqualTo(2);
		assertThat(session.getEnergyKwh()).isEqualByComparingTo("11");
		assertThat(session.getImportBatchId()).isEqualTo(secondBatch);
	}

	@Test
	void rejectsSessionsThatEndBeforeTheyStart() {
		assertThatThrownBy(() -> EnergySession.record(SessionSource.WALLBOX, "", "4",
				reading("2026-10-21T15:00:00Z", "2026-10-21T14:00:00Z", "1"), null, false, BATCH, ECUADOR))
				.extracting("code").isEqualTo("INVALID_SESSION");
	}

	@Test
	void previewRowsNeedApprovalOnlyForCorrectionsAndNonPreferredSources() {
		SessionReading value = reading("2026-10-21T15:00:00Z", "2026-10-21T15:30:00Z", "1");
		ImportRow modified = ImportRow.classified(BATCH, 2, "5", value, CONECTZ_STATION, RowClassification.MODIFIED,
				false, UUID.randomUUID(), List.of());
		ImportRow fresh = ImportRow.classified(BATCH, 3, "6", value, CONECTZ_STATION, RowClassification.NEW, false,
				null, List.of());
		ImportRow identical = ImportRow.classified(BATCH, 4, "7", value, CONECTZ_STATION,
				RowClassification.IDENTICAL, false, UUID.randomUUID(), List.of());

		assertThat(modified.isApplicable()).isFalse();
		modified.decide(null, true);
		assertThat(modified.isApplicable()).isTrue();
		assertThat(fresh.isApplicable()).isTrue();
		assertThatThrownBy(() -> fresh.decide(null, true)).extracting("code").isEqualTo("ROW_APPROVAL_NOT_REQUIRED");
		assertThatThrownBy(() -> identical.decide(true, null)).extracting("code").isEqualTo("ROW_NOT_IMPORTABLE");
	}

	@Test
	void preferredSourcesFollowTheOperationalTable() {
		assertThat(PreferredSourcePolicy.preferredFor(ChargingNetwork.KIA, InfrastructureType.CHARGING_POINT))
				.isEqualTo(SessionSource.WALLBOX);
		assertThat(PreferredSourcePolicy.preferredFor(ChargingNetwork.KIA, InfrastructureType.CHARGING_STATION))
				.isEqualTo(SessionSource.ENERLINK);
		assertThat(PreferredSourcePolicy.preferredFor(ChargingNetwork.JAC, InfrastructureType.CHARGING_POINT))
				.isEqualTo(SessionSource.ENERLINK);
	}
}
