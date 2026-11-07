package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.support.Feature006Fixtures;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/** Ensayo de backup/restore solo dentro del contenedor desechable, nunca Compose/dev. */
@SpringBootTest(properties="sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class Feature006RolloutTest {
    @Autowired PostgreSQLContainer postgres;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean FileStorage storage;
    @Test void logicalBackupRestoresNullableReferencesAndAllRevisionPointers() throws Exception {
        Feature006Fixtures.seedInventory(jdbc);Feature006Fixtures.seedVisualMeasurements(jdbc);
        String query="SELECT md5(string_agg((to_jsonb(m)||jsonb_build_object('period_start',extract(epoch FROM m.period_start),'period_end',extract(epoch FROM m.period_end),'created_at',extract(epoch FROM m.created_at),'updated_at',extract(epoch FROM m.updated_at)))::text,',' ORDER BY m.id)) FROM upt_measurement m";
        String before=jdbc.queryForObject(query,String.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement WHERE period_start IS NULL",Integer.class)).isPositive();
        var backup=postgres.execInContainer("pg_dump","-U",postgres.getUsername(),"-d",postgres.getDatabaseName(),"-Fc","--file=/tmp/sarc006-qa.dump");
        assertThat(backup.getExitCode()).as("Backup íntegro de la BD sintética").isZero();
        assertThat(postgres.execInContainer("createdb","-U",postgres.getUsername(),"sarc006_restore").getExitCode()).isZero();
        assertThat(postgres.execInContainer("pg_restore","-U",postgres.getUsername(),"-d","sarc006_restore","/tmp/sarc006-qa.dump").getExitCode()).isZero();
        var restored=postgres.execInContainer("psql","-U",postgres.getUsername(),"-d","sarc006_restore","-At","-c",query);
        assertThat(restored.getExitCode()).isZero();assertThat(restored.getStdout().strip()).isEqualTo(before);
        String revisions="SELECT md5(string_agg((to_jsonb(r)||jsonb_build_object('entered_at',extract(epoch FROM r.entered_at),'validated_at',extract(epoch FROM r.validated_at),'exact_confirmed_at',extract(epoch FROM r.exact_confirmed_at),'superseded_at',extract(epoch FROM r.superseded_at)))::text,',' ORDER BY r.id)) FROM upt_measurement_revision r";
        var restoredRevisions=postgres.execInContainer("psql","-U",postgres.getUsername(),"-d","sarc006_restore","-At","-c",revisions);
        assertThat(restoredRevisions.getExitCode()).isZero();
        assertThat(restoredRevisions.getStdout().strip()).isEqualTo(jdbc.queryForObject(revisions,String.class));
    }
}
