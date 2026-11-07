package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportJobs;
import com.tidsec.sarc_bakend.support.WebSessions;

class UptimeTemporalTransitionApiTest extends UptimeApiSupport {
    private static final String ROOT="/api/v1/uptime/measurements/temporal-transition";
    @MockitoBean ImportJobs jobs;
    @org.springframework.beans.factory.annotation.Autowired com.tidsec.sarc_bakend.modules.uptime.application.port.UptimeRowParser parser;
    @org.springframework.beans.factory.annotation.Autowired com.tidsec.sarc_bakend.shared.importing.reading.TabularReader reader;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.tidsec.sarc_bakend.shared.audit.AuditTrail audit;
    private static final tools.jackson.databind.ObjectMapper JSON=tools.jackson.databind.json.JsonMapper.builder().build();
    private String request(UUID key,String items) throws Exception {
        return mvc.perform(post(ROOT).cookie(analyst).with(WebSessions.xsrf())
            .header("Idempotency-Key",key.toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":"+items+"}")).andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
    }
    @Test void storesQueuedWorkBeforeExecutionAndReturnsSameRequest() throws Exception {
        UUID key=UUID.randomUUID();String items="[{\"id\":\""+JAC_1+"\",\"version\":0}]";
        String first=request(key,items);String work=com.jayway.jsonpath.JsonPath.read(first,"$.workId");
        assertThat(first).contains("QUEUED").contains("\"processed\":0");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_temporal_transition_work WHERE id=?",Integer.class,UUID.fromString(work))).isEqualTo(1);
        String second=request(key,items);assertThat(com.jayway.jsonpath.JsonPath.<String>read(second,"$.workId")).isEqualTo(work);
        mvc.perform(get(ROOT+"/"+work).cookie(analyst)).andExpect(status().isOk())
            .andExpect(jsonPath("$.processed").value(0));
        mvc.perform(get(ROOT+"/"+work).cookie(manager)).andExpect(status().isForbidden());
    }
    @Test void requiresCsrfAndEditors() throws Exception {
        mvc.perform(post(ROOT).cookie(analyst).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(ROOT).cookie(manager).with(WebSessions.xsrf())
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":[{\"id\":\""+JAC_1+"\",\"version\":0}]}")).andExpect(status().isForbidden());
    }
    @org.junit.jupiter.api.BeforeEach void cleanWork() {jdbc.execute("TRUNCATE upt_temporal_transition_work");}
    private void executeJobs(boolean reject) {
        org.mockito.Mockito.doAnswer(call->{
            com.tidsec.sarc_bakend.shared.persistence.AfterCommit.run(call.getArgument(reject?1:0));return null;
        }).when(jobs).submitAfterCommit(org.mockito.Mockito.any(),org.mockito.Mockito.any());
    }
    private String items(UUID id,long version) {return "[{\"id\":\""+id+"\",\"version\":"+version+"}]";}
    @Test void queueRejectionSurvivesAndRetryRetainsOriginalVersion() throws Exception {
        UUID id=UUID.randomUUID();executeJobs(true);
        String body=request(UUID.randomUUID(),items(id,7));String work=com.jayway.jsonpath.JsonPath.read(body,"$.workId");
        mvc.perform(get(ROOT+"/"+work).cookie(analyst)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.failureCode").value("UPTIME_TRANSITION_QUEUE_FULL"))
            .andExpect(jsonPath("$.processed").value(0));
        long version=jdbc.queryForObject("SELECT version FROM upt_temporal_transition_work WHERE id=?::uuid",Long.class,work);
        executeJobs(false);
        mvc.perform(post(ROOT+"/"+work+"/retry").cookie(analyst).with(WebSessions.xsrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":"+version+"}"))
            .andExpect(status().isAccepted());
        mvc.perform(get(ROOT+"/"+work).cookie(analyst)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.counts.STALE_VERSION").value(1))
            .andExpect(jsonPath("$.results.content[0].version").value(7));
    }
    @Test void unresolvedDecisionPreservesRevisionAndAdvancesHeaderVersion() throws Exception {
        UUID id=UUID.fromString(registered(JAC_1,"1401","39").get("id").toString());
        jdbc.update("UPDATE upt_measurement SET period_precision='REQUIRES_RECONCILIATION' WHERE id=?",id);
        String before=jdbc.queryForObject("SELECT to_jsonb(r)::text FROM upt_measurement_revision r WHERE measurement_id=?",String.class,id);
        long initial=version(id.toString());executeJobs(false);
        String body=request(UUID.randomUUID(),items(id,initial));String work=com.jayway.jsonpath.JsonPath.read(body,"$.workId");
        mvc.perform(get(ROOT+"/"+work).cookie(analyst)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.counts.REQUIRES_RECONCILIATION").value(1));
        assertThat(version(id.toString())).isGreaterThan(initial);
        assertThat(jdbc.queryForObject("SELECT to_jsonb(r)::text FROM upt_measurement_revision r WHERE measurement_id=?",String.class,id)).isEqualTo(before);
        long next=version(id.toString());request(UUID.randomUUID(),items(id,next));assertThat(version(id.toString())).isEqualTo(next);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_temporal_change WHERE measurement_id=?",Integer.class,id)).isEqualTo(1);
    }

    private UUID fileCandidate() throws Exception {
        byte[] bytes=com.tidsec.sarc_bakend.support.Feature006Fixtures.weeklyWorkbook();
        var row=parser.inspectSource(reader.readSheets(bytes)).getFirst();var raw=new java.util.HashMap<>(row.sourceValues());
        raw.remove("sourcePeriodStartPresent");raw.remove("sourcePeriodEndPresent");String source=JSON.writeValueAsString(raw);
        UUID id=UUID.fromString(registered(JAC_1,"1401","39").get("id").toString()),batch=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO upt_import_batch(id,source,origin,file_name,checksum,original_ref,status,declared_period_type,
                declared_period_start,declared_period_end,created_at,updated_at,version)
            VALUES(?,'ENERLINK','FILE','semanal.xlsx',?,?,'COMPLETED','ROLLING_7D','2026-10-26T05:00:00Z','2026-11-02T05:00:00Z',now(),now(),0)
            """,batch,com.tidsec.sarc_bakend.shared.audit.ContentFingerprint.sha256(bytes),"private/"+batch);
        jdbc.update("UPDATE upt_measurement SET period_precision='REQUIRES_RECONCILIATION',period_start='2026-10-26T05:00:00Z',period_end='2026-11-02T05:00:00Z',effective_revision=1 WHERE id=?",id);
        var values=row.values();
        jdbc.update("""
            UPDATE upt_measurement_revision SET origin='FILE',import_batch_id=?,source_values=?::jsonb,period_type='EXACT_WEEK',
                available_minutes=?,occupied_minutes=null,fault_minutes=?,offline_minutes=?,reported_percent=?,uptime_basis='REPORTED_PERCENT',
                review_state='VALIDATED',validated_at=now(),exact_confirmed_at=now(),observations='[]' WHERE measurement_id=?
            """,batch,source,values.available().intValueExact(),values.fault().intValueExact(),values.offline().intValueExact(),values.reportedPercent(),id);
        jdbc.update("""
            INSERT INTO upt_import_row(id,batch_id,row_number,source_sheet,source_row,source_site,source_charger,source_connector,
                equipment_id,connector_id,classification,issues,observations,source_values,available_minutes,fault_minutes,offline_minutes,
                reported_percent,uptime_basis,period_type,approved,outcome,applied_measurement_id,version)
            VALUES(?,?,?,?,?,?,?,?,?,?,'NEW','[]','[]',?::jsonb,?,?,?,?,'REPORTED_PERCENT','ROLLING_7D',true,'APPLIED',?,0)
            """,UUID.randomUUID(),batch,row.rowNumber(),row.sheet(),row.sheetRow(),row.site(),row.charger(),row.connector(),
            com.tidsec.sarc_bakend.support.Feature005Fixtures.JAC_STATION,JAC_1,source,values.available().intValueExact(),values.fault().intValueExact(),values.offline().intValueExact(),values.reportedPercent(),id);
        org.mockito.Mockito.when(fileStorage.readDocument("private/"+batch)).thenReturn(bytes);return id;
    }
    @Test void completeOriginalTransitionsWithoutChangingHistoricalRevision() throws Exception {
        UUID id=fileCandidate();String before=jdbc.queryForObject("SELECT to_jsonb(r)::text FROM upt_measurement_revision r WHERE measurement_id=?",String.class,id);
        executeJobs(false);request(UUID.randomUUID(),items(id,version(id.toString())));
        assertThat(jdbc.queryForObject("SELECT period_precision FROM upt_measurement WHERE id=?",String.class,id)).isEqualTo("REFERENCE_WEEK");
        assertThat(jdbc.queryForObject("SELECT to_jsonb(r)::text FROM upt_measurement_revision r WHERE measurement_id=?",String.class,id)).isEqualTo(before);
        mvc.perform(get("/api/v1/uptime/measurements/"+id+"/revisions").cookie(analyst)).andExpect(status().isOk())
            .andExpect(jsonPath("$.temporalChanges[0].actorKind").value("SYSTEM"));
    }
    @Test void simultaneousDestinationCandidatesNeverSelectFirstAsWinner() throws Exception {
        UUID a=fileCandidate(),b=fileCandidate();executeJobs(false);
        String combined="[{\"id\":\""+a+"\",\"version\":"+version(a.toString())+"},{\"id\":\""+b+"\",\"version\":"+version(b.toString())+"}]";
        String body=request(UUID.randomUUID(),combined);String work=com.jayway.jsonpath.JsonPath.read(body,"$.workId");
        mvc.perform(get(ROOT+"/"+work).cookie(analyst)).andExpect(jsonPath("$.counts.REQUIRES_RECONCILIATION").value(2))
            .andExpect(jsonPath("$.counts.APPLIED").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement WHERE period_precision='REFERENCE_WEEK'",Integer.class)).isZero();
    }
    @Test void changeDuringOriginalReadIsStaleAndDoesNotApply() throws Exception {
        UUID id=fileCandidate();
        org.mockito.Mockito.when(fileStorage.readDocument(org.mockito.Mockito.anyString())).thenAnswer(call->{
            jdbc.update("UPDATE upt_measurement SET version=version+1 WHERE id=?",id);return com.tidsec.sarc_bakend.support.Feature006Fixtures.weeklyWorkbook();
        });executeJobs(false);
        String body=request(UUID.randomUUID(),items(id,version(id.toString())));String work=com.jayway.jsonpath.JsonPath.read(body,"$.workId");
        mvc.perform(get(ROOT+"/"+work).cookie(analyst)).andExpect(jsonPath("$.counts.STALE_VERSION").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_temporal_change WHERE measurement_id=?",Integer.class,id)).isZero();
    }
    @Test void failureOfAuditRollsBackHeaderEventAndResultTogether() throws Exception {
        UUID id=fileCandidate();long original=version(id.toString());
        org.mockito.Mockito.doThrow(new IllegalStateException("test-rollback")).when(org.springframework.test.util.AopTestUtils.<com.tidsec.sarc_bakend.shared.audit.AuditTrail>getUltimateTargetObject(audit)).record(org.mockito.Mockito.eq("UPTIME_TEMPORAL_CLASSIFIED"),org.mockito.Mockito.anyString(),org.mockito.Mockito.any(),org.mockito.Mockito.anyMap());
        executeJobs(false);String body=request(UUID.randomUUID(),items(id,original));String work=com.jayway.jsonpath.JsonPath.read(body,"$.workId");
        mvc.perform(get(ROOT+"/"+work).cookie(analyst)).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.processed").value(0));
        assertThat(version(id.toString())).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_temporal_change WHERE measurement_id=?",Integer.class,id)).isZero();
    }
    @Test void keyConflictAndTerminalRepeatUseDurableRequest() throws Exception {
        UUID key=UUID.randomUUID(),id=UUID.randomUUID();executeJobs(false);
        String first=request(key,items(id,0));String work=com.jayway.jsonpath.JsonPath.read(first,"$.workId");
        mvc.perform(post(ROOT).cookie(analyst).with(WebSessions.xsrf()).header("Idempotency-Key",key.toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":"+items(id,0)+"}")).andExpect(status().isOk()).andExpect(jsonPath("$.workId").value(work));
        mvc.perform(post(ROOT).cookie(analyst).with(WebSessions.xsrf()).header("Idempotency-Key",key.toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":"+items(id,1)+"}")).andExpect(status().isConflict());
    }

    @Test void reorderedItemsReuseRequestAndCrashAfterLastResultOnlyFinalizesOnRetry() throws Exception {
        UUID key=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();executeJobs(false);
        String first="[{\"id\":\""+a+"\",\"version\":3},{\"id\":\""+b+"\",\"version\":7}]";
        String response=request(key,first);String work=com.jayway.jsonpath.JsonPath.read(response,"$.workId");
        String reverse="[{\"id\":\""+b+"\",\"version\":7},{\"id\":\""+a+"\",\"version\":3}]";
        mvc.perform(post(ROOT).cookie(analyst).with(WebSessions.xsrf()).header("Idempotency-Key",key.toString()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"items\":"+reverse+"}")).andExpect(status().isOk()).andExpect(jsonPath("$.workId").value(work));
        String results=jdbc.queryForObject("SELECT results::text FROM upt_temporal_transition_work WHERE id=?::uuid",String.class,work);
        jdbc.update("UPDATE upt_temporal_transition_work SET status='INTERRUPTED' WHERE id=?::uuid",work);
        long version=jdbc.queryForObject("SELECT version FROM upt_temporal_transition_work WHERE id=?::uuid",Long.class,work);
        mvc.perform(post(ROOT+"/"+work+"/retry").cookie(analyst).with(WebSessions.xsrf()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":"+version+"}"))
            .andExpect(status().isAccepted());
        mvc.perform(get(ROOT+"/"+work).cookie(analyst).param("size","1")).andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.processed").value(2)).andExpect(jsonPath("$.results.totalPages").value(2));
        assertThat(jdbc.queryForObject("SELECT results::text FROM upt_temporal_transition_work WHERE id=?::uuid",String.class,work)).isEqualTo(results);
    }
    @Test void concurrentRetriesAcceptOnlyOneAttempt() throws Exception {
        String response=request(UUID.randomUUID(),items(UUID.randomUUID(),0));String work=com.jayway.jsonpath.JsonPath.read(response,"$.workId");
        jdbc.update("UPDATE upt_temporal_transition_work SET status='FAILED' WHERE id=?::uuid",work);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);var futures=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for(int n=0;n<2;n++)futures.add(pool.submit(()->{gate.await();return mvc.perform(post(ROOT+"/"+work+"/retry").cookie(analyst)
                .with(WebSessions.xsrf()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andReturn().getResponse().getStatus();}));
            gate.countDown();var statuses=new java.util.ArrayList<Integer>();for(var future:futures)statuses.add(future.get());
            assertThat(statuses).containsExactlyInAnyOrder(202,409);
        }
    }

    private String manual(UUID id,String reason,String proof) {
        return "{\"version\":"+version(id.toString())+",\"temporalPrecision\":\"REFERENCE_WEEK\",\"referenceWeek\":\"2026-10-26\",\"justification\":\""+reason+"\",\"evidence\":\""+proof+"\"}";
    }
    @Test void manualClassificationRequiresEvidenceAndNeverBypassesCanonicalIdentityWhenExcluded() throws Exception {
        UUID a=fileCandidate(),b=fileCandidate();
        send(analyst,"/measurements/"+a+"/temporal-classification",manual(a,"",""))
            .andExpect(status().isUnprocessableContent());
        send(analyst,"/measurements/"+a+"/temporal-classification",manual(a,"Decisión explícita","Original semanal cotejado"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.temporalPrecision").value("REFERENCE_WEEK"));
        send(analyst,"/measurements/"+b+"/temporal-classification",manual(b,"Conciliar","Original revisado"))
            .andExpect(status().isConflict());
        send(analyst,"/measurements/"+a+"/exclude","{\"version\":"+version(a.toString())+",\"justification\":\"Apartar explícitamente\"}")
            .andExpect(status().isOk());
        send(analyst,"/measurements/"+b+"/temporal-classification",manual(b,"Conciliar","Original revisado"))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT period_precision FROM upt_measurement WHERE id=?",String.class,b)).isEqualTo("REQUIRES_RECONCILIATION");
    }

}
