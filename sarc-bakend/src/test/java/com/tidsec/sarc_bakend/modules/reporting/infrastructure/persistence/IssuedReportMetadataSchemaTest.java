package com.tidsec.sarc_bakend.modules.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import com.tidsec.sarc_bakend.TestcontainersConfiguration;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
class IssuedReportMetadataSchemaTest {
	@Autowired JdbcTemplate jdbc;

	@Test
	void upgradeFromV010PreservesLegacyIdentityDatesAndHash() {
		String schema = "qa004_upgrade";
		var dataSource = java.util.Objects.requireNonNull(jdbc.getDataSource());
		org.flywaydb.core.Flyway.configure()
				.dataSource(dataSource)
				.schemas(schema)
				.defaultSchema(schema)
				.target("10")
				.load()
				.migrate();
		UUID id = UUID.randomUUID();
		jdbc.update(
				"insert into "
						+ schema
						+ ".report_issued(id,kind,format,file_name,scope,checksum,size_bytes,file_ref,created_at,updated_at,version)"
						+ " values(?,'CONSUMPTION','PDF','old.pdf','legacy','old-hash',42,'old/ref','2026-10-01','2026-10-01',0)",
				id);
		var before =
				jdbc.queryForMap(
						"select id,checksum,file_ref,created_at,updated_at,version from "
								+ schema
								+ ".report_issued where id=?",
						id);
		org.flywaydb.core.Flyway.configure()
				.dataSource(dataSource)
				.schemas(schema)
				.defaultSchema(schema)
				.load()
				.migrate();
		assertThat(
						jdbc.queryForMap(
								"select id,checksum,file_ref,created_at,updated_at,version from "
										+ schema
										+ ".report_issued where id=?",
								id))
				.isEqualTo(before);
		assertThat(
						jdbc.queryForMap(
										"select content_profile,cut_off,criteria from "
												+ schema
												+ ".report_issued where id=?",
										id)
								.values())
				.containsOnlyNulls();
	}

	@Test
	void legacyIsNullableButProfilesAndJsonAreConstrained() {
		assertThat(
						jdbc.queryForList(
								"select column_name from information_schema.columns where"
									+ " table_name='report_issued' and column_name"
									+ " in('content_profile','cut_off','criteria')",
								String.class))
				.hasSize(3);
		String sql =
				"insert into"
					+ " report_issued(id,kind,format,file_name,scope,checksum,size_bytes,created_at,updated_at,version,content_profile,cut_off,criteria)"
					+ " values(?,'CONSUMPTION','PDF','qa.pdf','legacy','hash',1,now(),now(),0,?,"
					+ " ?,?::jsonb)";
		UUID legacy = UUID.randomUUID();
		jdbc.update(sql, legacy, null, null, null);
		assertThat(
						jdbc.queryForMap(
										"select content_profile,cut_off,criteria from report_issued"
											+ " where id=?",
										legacy)
								.values())
				.containsOnlyNulls();
		assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "INVALID", null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), "OFFICIAL_ONLY", null, "{}"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), null, null, "[]"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
