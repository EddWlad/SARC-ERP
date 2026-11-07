package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.support.Feature005Fixtures;

class UptimeSummaryApiTest extends UptimeApiSupport {

	private static final String SUMMARY = "/api/v1/uptime/summary";

	private String official(UUID connector, String monday, String nextMonday, String available, String offline)
			throws Exception {
		String body = measurement(connector, available, offline).replace(START, monday + "T00:00:00-05:00")
				.replace(END, nextMonday + "T00:00:00-05:00").replace("ROLLING_24H", "EXACT_WEEK")
				.replace("\"exactConfirmed\":false", "\"exactConfirmed\":true");
		String created = send(analyst, "/measurements", body).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
		validate(id);
		return id;
	}

	@Test
	void opensOnLatestOfficialWeekWithLabels() throws Exception {
		official(JAC_1, "2026-10-12", "2026-10-19", "10041", "39");

		mvc.perform(get(SUMMARY).cookie(manager)).andExpect(status().isOk())
				.andExpect(jsonPath("$.resolvedPeriod.kind").value("OFFICIAL"))
				.andExpect(jsonPath("$.resolvedPeriod.timeZone").value("America/Guayaquil"))
				.andExpect(jsonPath("$.official.uptimePercent").value("99.61"))
				.andExpect(jsonPath("$.provisional").doesNotExist())
				.andExpect(jsonPath("$.trend.length()").value(1))
				.andExpect(jsonPath("$.notices", Matchers.hasItem("LATEST_WEEK_MISSING")));
	}

	@Test
	void withoutOfficialOpensProvisionalAndWarns() throws Exception {
		validate(registered(JAC_1, "1401", "39").get("id").toString());

		mvc.perform(get(SUMMARY).cookie(manager)).andExpect(jsonPath("$.resolvedPeriod.kind").value("PROVISIONAL"))
				.andExpect(jsonPath("$.resolvedPeriod.periodType").value("ROLLING_24H"))
				.andExpect(jsonPath("$.official").doesNotExist())
				.andExpect(jsonPath("$.provisional.uptimePercent").value("97.29"))
				.andExpect(jsonPath("$.trend.length()").value(0))
				.andExpect(jsonPath("$.notices", Matchers.hasItem("NO_OFFICIAL_WEEK")));
	}

	@Test
	void emptyGivesNullsNeverZero() throws Exception {
		mvc.perform(get(SUMMARY).cookie(manager)).andExpect(jsonPath("$.resolvedPeriod.kind").value("NONE"))
				.andExpect(jsonPath("$.official").doesNotExist())
				.andExpect(jsonPath("$.provisional").doesNotExist());
	}

	@Test
	void noEvaluableConnectors() throws Exception {
		mvc.perform(get(SUMMARY).cookie(manager).param("equipmentId", Feature005Fixtures.KIA_POINT.toString()))
				.andExpect(jsonPath("$.coverage.denominator").value(0))
				.andExpect(jsonPath("$.notices", Matchers.hasItem("NO_EVALUABLE_CONNECTORS")));
	}

	@Test
	void pendingCountsWithoutValue() throws Exception {
		registered(JAC_1, "1401", "39");

		mvc.perform(get(SUMMARY).cookie(manager)).andExpect(jsonPath("$.coverage.byState.pendingReview").value(1))
				.andExpect(jsonPath("$.provisional").doesNotExist());
	}

	@Test
	void combinedFiltersNarrowBlocks() throws Exception {
		official(JAC_1, "2026-10-12", "2026-10-19", "10080", "0");
		mvc.perform(get(SUMMARY).cookie(manager).param("network", "KIA").param("periodKind", "OFFICIAL")
				.param("from", "2026-10-12").param("to", "2026-10-19"))
				.andExpect(jsonPath("$.official").doesNotExist());
		mvc.perform(get(SUMMARY).cookie(manager).param("network", "JAC").param("periodKind", "OFFICIAL")
				.param("from", "2026-10-12").param("to", "2026-10-19"))
				.andExpect(jsonPath("$.official.uptimePercent").value("100.00"));
	}

	@Test
	void rangeLimitIs366Days() throws Exception {
		mvc.perform(get(SUMMARY).cookie(manager).param("from", "2025-01-01").param("to", "2026-01-02"))
				.andExpect(status().isOk());
		mvc.perform(get(SUMMARY).cookie(manager).param("from", "2025-01-01").param("to", "2026-01-03"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_RANGE_TOO_LONG"));
	}

	@Test
	void officialRangeMustStartOnMonday() throws Exception {
		mvc.perform(get(SUMMARY).cookie(manager).param("periodKind", "OFFICIAL").param("from", "2026-10-13")
				.param("to", "2026-10-19")).andExpect(status().isUnprocessableContent());
	}

	@Test
	void provisionalNeedsPeriodType() throws Exception {
		mvc.perform(get(SUMMARY).cookie(manager).param("periodKind", "PROVISIONAL"))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void unattributedTotalIgnoresConnectorFilters() throws Exception {
		jdbc.execute("""
				INSERT INTO upt_import_batch (id, source, origin, file_name, checksum, original_ref, status, declared_period_type, created_at,
				 updated_at, version) VALUES ('00000000-0000-0000-0000-00000000ba01', 'ENERLINK', 'PASTE', 'x', 'c', 'r', 'COMPLETED',
				 'ROLLING_24H', now(), now(), 0)""");
		jdbc.execute("""
				INSERT INTO upt_import_row (id, batch_id, row_number, classification, issues, observations, approved, outcome,
				 version, period_end) VALUES ('00000000-0000-0000-0000-00000000ba02',
				 '00000000-0000-0000-0000-00000000ba01', 1, 'PENDING_LINK', '[]', '[]', false, 'PENDING_LINK', 0, timestamptz '2026-11-05T12:00:00Z')""");

		mvc.perform(get(SUMMARY).cookie(manager)).andExpect(jsonPath("$.coverage.pendingLink").value(0));
		mvc.perform(get(SUMMARY).cookie(manager).param("network", "KIA"))
                .andExpect(jsonPath("$.coverage.pendingLink").value(0));
        mvc.perform(get(SUMMARY).cookie(manager).param("network","KIA")
            .param("from","2026-11-04").param("to","2026-11-06"))
            .andExpect(jsonPath("$.coverage.pendingLink").value(1));
	}

	@Test
	void resultsListConnectorsEquipmentAndExclusions() throws Exception {
		official(JAC_1, "2026-10-12", "2026-10-19", "9072", "1008");

		mvc.perform(get("/api/v1/uptime/results/connectors").cookie(manager).param("periodKind", "OFFICIAL")
				.param("from", "2026-10-12").param("to", "2026-10-19"))
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].position").value(1))
				.andExpect(jsonPath("$.content[0].uptimePercent").value("90.00"))
				.andExpect(jsonPath("$.content[0].uidPuk").doesNotExist());
		mvc.perform(get("/api/v1/uptime/results/equipment").cookie(manager).param("equipmentId",
				Feature005Fixtures.JAC_STATION.toString()).param("periodKind", "OFFICIAL")
				.param("from", "2026-10-12").param("to", "2026-10-19"))
				.andExpect(jsonPath("$.content[0].connectorsIncluded").value(1))
				.andExpect(jsonPath("$.content[0].connectorsInScope").value(2));
		mvc.perform(get("/api/v1/uptime/results/exclusions").cookie(manager)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.state == 'RETIRED')]").isNotEmpty());
	}

	@Test
	void scopeSelectorHonoursFirmScope() throws Exception {
		mvc.perform(get("/api/v1/uptime/scope/connectors").cookie(manager).param("q", "QA005"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.equipmentId == '%s')]".formatted(Feature005Fixtures.KIA_POINT)).isEmpty())
				.andExpect(jsonPath("$[?(@.equipmentId == '%s')]".formatted(Feature005Fixtures.JAC_STATION)).isNotEmpty());
	}
    private void referenceReading(UUID connector,String week,String percent,boolean validated) {
        UUID id=UUID.randomUUID();
        UUID equipment=jdbc.queryForObject("SELECT equipment_id FROM asset_connector WHERE id=?",UUID.class,connector);
        jdbc.update("""
            INSERT INTO upt_measurement(id,connector_id,equipment_id,source,period_zone,latest_revision,
                effective_revision,period_precision,reference_week,reference_period_type,created_at,updated_at,version)
            VALUES(?,?,?,'ENERLINK','America/Guayaquil',1,?,'REFERENCE_WEEK',?::date,'ROLLING_7D',now(),now(),0)
            """,id,connector,equipment,validated?1:null,week);
        jdbc.update("""
            INSERT INTO upt_measurement_revision(id,measurement_id,revision,reading_class,period_type,
                available_minutes,occupied_minutes,fault_minutes,offline_minutes,reported_percent,
                review_state,observations,origin,equipment_label,connector_type_label,entered_at,validated_at,uptime_basis)
            VALUES(?,?,1,'MEASURED','ROLLING_7D',10,null,0,0,?::numeric,?,'[]','FILE','Nombre fuente','Tipo2',
                now(),CASE WHEN ? THEN now() END,'REPORTED_PERCENT')
            """,UUID.randomUUID(),id,percent,validated?"VALIDATED":"PENDING",validated);
    }
    @Test void referenceSummarySeparatesMethodsAndStaysOnGlobalLatestWhenFiltered() throws Exception {
        referenceReading(JAC_1,"2026-10-26","0",true);
        referenceReading(JAC_2,"2026-10-26","100",true);
        UUID point=Feature005Fixtures.connectorId(Feature005Fixtures.JAC_POINT,1);
        referenceReading(point,"2026-10-19","70",true);
        referenceReading(point,"2026-11-02","90",false);
        mvc.perform(get(SUMMARY).cookie(manager).param("periodKind","PROVISIONAL").param("periodType","ROLLING_7D"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.selection.referenceWeek").value("2026-10-26"))
            .andExpect(jsonPath("$.referenceReport.aggregationMethod").value("REPORT_MEAN"))
            .andExpect(jsonPath("$.referenceReport.uptimePercent").value("50.00"))
            .andExpect(jsonPath("$.referenceReport.evaluatedMinutes").doesNotExist())
            .andExpect(jsonPath("$.referenceReport.composition.occupiedMinutes").doesNotExist())
            .andExpect(jsonPath("$.operations").doesNotExist());
        mvc.perform(get(SUMMARY).cookie(manager).param("periodKind","PROVISIONAL").param("periodType","ROLLING_7D")
            .param("equipmentId",Feature005Fixtures.JAC_POINT.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.selection.referenceWeek").value("2026-10-26"))
            .andExpect(jsonPath("$.referenceReport.uptimePercent").doesNotExist());
        mvc.perform(get("/api/v1/uptime/results/connectors").cookie(manager).param("referenceWeek","2026-10-26")
            .param("temporalSection","REFERENCE_WEEK").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].evaluatedMinutes").doesNotExist());
    }
    @Test void referenceOpeningHasNoArbitraryHistoricalCutoff() throws Exception {
        referenceReading(JAC_1,"2020-10-26","20",true);
        mvc.perform(get(SUMMARY).cookie(manager).param("periodKind","PROVISIONAL").param("periodType","ROLLING_7D"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.selection.referenceWeek").value("2020-10-26"))
            .andExpect(jsonPath("$.referenceReport.uptimePercent").value("20.00"));
        mvc.perform(get(SUMMARY).cookie(manager).param("referenceWeek","2020-11-02"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.selection.referenceWeek").value("2020-11-02"))
            .andExpect(jsonPath("$.referenceReport.uptimePercent").doesNotExist())
            .andExpect(jsonPath("$.coverage.pendingLink").doesNotExist());
    }
    @Test void requiresSectionAndRejectsMixedSelections() throws Exception {
        referenceReading(JAC_1,"2026-10-26","20",true);
        mvc.perform(get("/api/v1/uptime/results/connectors").cookie(manager).param("referenceWeek","2026-10-26"))
            .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("UPTIME_SECTION_REQUIRED"));
        mvc.perform(get(SUMMARY).cookie(manager).param("referenceWeek","2026-10-26")
            .param("from","2026-10-26").param("to","2026-11-02")).andExpect(status().isUnprocessableContent());
    }

    private void candidate(UUID connector,String target) {
        UUID id=UUID.randomUUID();
        UUID equipment=jdbc.queryForObject("SELECT equipment_id FROM asset_connector WHERE id=?",UUID.class,connector);
        jdbc.update("""
            INSERT INTO upt_measurement(id,connector_id,equipment_id,source,period_zone,latest_revision,
                period_precision,reference_week,reference_period_type,created_at,updated_at,version)
            VALUES(?,?,?,'ENERLINK','America/Guayaquil',1,'REQUIRES_RECONCILIATION','2026-10-26','ROLLING_7D',now(),now(),0)
            """,id,connector,equipment);
        jdbc.update("""
            INSERT INTO upt_measurement_revision(id,measurement_id,revision,reading_class,period_type,
                review_state,observations,origin,equipment_label,connector_type_label,entered_at,uptime_basis)
            VALUES(?,?,1,'MEASURED','ROLLING_7D','PENDING','[]','FILE','Fuente','Tipo2',now(),'STATES')
            """,UUID.randomUUID(),id);
        jdbc.update("""
            INSERT INTO upt_measurement_temporal_change(id,measurement_id,policy_version,expected_measurement_version,
                outcome,previous_temporal,result_temporal,evidence,actor_kind,occurred_at,reason)
            VALUES(?,?,'006-1',0,'REQUIRES_RECONCILIATION','{}',?::jsonb,'{}','SYSTEM',now(),'Prueba de evidencia parcial')
            """,UUID.randomUUID(),id,target==null?"{}":"{\"targetPrecision\":\""+target+"\"}");
    }

    @Test void reconciliationNeverChoosesAWinnerOrDistributesUnknownSectionTwice() throws Exception {
        referenceReading(JAC_1,"2026-10-26","50",true);
        candidate(JAC_1,"REFERENCE_WEEK");candidate(JAC_2,null);
        mvc.perform(get(SUMMARY).cookie(manager).param("referenceWeek","2026-10-26"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.referenceReport.uptimePercent").doesNotExist())
            .andExpect(jsonPath("$.referenceReport.coverage.byState.pendingReview").value(1))
            .andExpect(jsonPath("$.provisionalCoverage.byState.pendingReview").value(0))
            .andExpect(jsonPath("$.temporalReconciliationCount").value(1))
            .andExpect(jsonPath("$.operations").doesNotExist());
    }

    @Test void pendingLinkUsesOnlyChosenReferenceAndSectionBeforeAttributionFilters() throws Exception {
        UUID batch=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO upt_import_batch(id,source,origin,file_name,checksum,original_ref,status,
                declared_period_type,created_at,updated_at,version)
            VALUES(?,'ENERLINK','FILE','prueba.xlsx','checksum','privado','COMPLETED','ROLLING_7D',now(),now(),0)
            """,batch);
        for(String week:java.util.List.of("2026-10-26","2026-10-19")) {
            jdbc.update("""
                INSERT INTO upt_import_row(id,batch_id,row_number,classification,issues,observations,approved,
                    outcome,version,period_precision,reference_week,reference_period_type)
                VALUES(?,?,?,'PENDING_LINK','[]','[]',false,'PENDING_LINK',0,'REFERENCE_WEEK',?::date,'ROLLING_7D')
                """,UUID.randomUUID(),batch,week.equals("2026-10-26")?1:2,week);
        }
        mvc.perform(get(SUMMARY).cookie(manager).param("referenceWeek","2026-10-26").param("network","KIA"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.referenceReport.coverage.pendingLink").value(1))
            .andExpect(jsonPath("$.provisionalCoverage.pendingLink").value(0));
        mvc.perform(get(SUMMARY).cookie(manager).param("referenceWeek","2026-11-02"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.referenceReport.coverage.pendingLink").value(0));
    }

}
