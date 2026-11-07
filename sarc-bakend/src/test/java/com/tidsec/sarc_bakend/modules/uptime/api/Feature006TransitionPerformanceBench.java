package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportJobs;
import com.tidsec.sarc_bakend.shared.persistence.AfterCommit;
import com.tidsec.sarc_bakend.support.WebSessions;

/** Bloques reales persistentes; no certifica latencia de un proveedor de documentos. */
@EnabledIfSystemProperty(named="sarc.qa.transitionPerformance006",matches="true")
class Feature006TransitionPerformanceBench extends UptimeApiSupport {
    @MockitoBean ImportJobs jobs;
    @Test void measureDurableBlocks() throws Exception {
        org.mockito.Mockito.doAnswer(call->{AfterCommit.run(call.getArgument(0));return null;})
            .when(jobs).submitAfterCommit(org.mockito.Mockito.any(),org.mockito.Mockito.any());
        List<Long> times=new ArrayList<>();
        for(int round=0;round<3;round++) {
            List<String> items=new ArrayList<>();
            for(int i=0;i<500;i++) {
                UUID id=UUID.randomUUID();
                jdbc.update("""
                    INSERT INTO upt_measurement(id,connector_id,equipment_id,source,period_zone,latest_revision,
                        period_precision,reference_week,reference_period_type,created_at,updated_at,version)
                    VALUES(?,?,?,'ENERLINK','America/Guayaquil',1,'REQUIRES_RECONCILIATION','2026-10-26','ROLLING_7D',now(),now(),0)
                    """,id,JAC_1,com.tidsec.sarc_bakend.support.Feature005Fixtures.JAC_STATION);
                jdbc.update("""
                    INSERT INTO upt_measurement_revision(id,measurement_id,revision,reading_class,period_type,
                        reported_percent,review_state,observations,origin,equipment_label,connector_type_label,entered_at,uptime_basis)
                    VALUES(?,?,1,'MEASURED','ROLLING_7D',97.6800,'PENDING','[]','MANUAL','QA006','Tipo2',now(),'REPORTED_PERCENT')
                    """,UUID.randomUUID(),id);
                items.add("{\"id\":\""+id+"\",\"version\":0}");
            }
            long start=System.nanoTime();
            String body=mvc.perform(post("/api/v1/uptime/measurements/temporal-transition").cookie(analyst).with(WebSessions.xsrf())
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"items\":["+String.join(",",items)+"]}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
            String work=com.jayway.jsonpath.JsonPath.read(body,"$.workId");
            mvc.perform(get("/api/v1/uptime/measurements/temporal-transition/"+work).cookie(analyst))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(500)).andExpect(jsonPath("$.status").value("COMPLETED"));
            times.add((System.nanoTime()-start)/1_000_000);
        }
        times.sort(Long::compare);
        Files.writeString(Path.of("target/uptime-transition-performance006.txt"),
            "3 bloques de 500 candidatos sin original acreditado, 1500 decisiones conservadoras reales. Ejecución síncrona QA de ImportJobs, BD18 desechable. Sin IO de proveedor.\n"+
            "Duraciones ms: "+times+"; p95 observado (3 muestras, máximo): "+times.get(2)+" ms\n");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_temporal_change",Integer.class)).isGreaterThanOrEqualTo(1500);
    }
}
