package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature005Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UptimeMeasurementApiTest {

	private static final String START = "2026-10-30T14:00:00-05:00";
	private static final String END = "2026-10-31T14:00:00-05:00";
	private static final String PASSWORD = "Prueba-Uptime-2026";

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder encoder;

	Cookie analyst;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.execute("TRUNCATE upt_import_row, upt_measurement_exclusion, upt_measurement_revision, upt_measurement,"
				+ " upt_import_batch, upt_connector_alias, asset_equipment, auth_user, spring_session, audit_event CASCADE");
		Feature005Fixtures.seedInventory(jdbc);
		user("analista", Role.ANALISTA);
		user("gerente", Role.GERENTE);
		analyst = WebSessions.login(mvc, "analista@qa005.example.test", PASSWORD);
	}

	private void user(String name, Role role) {
		String hash = encoder.encode(PASSWORD);
		UserAccount account = UserAccount.create(new Email(name + "@qa005.example.test"), name, Set.of(role), hash);
		account.changePassword(hash);
		users.save(account);
	}

	private static String body(UUID connector, String minutes, String percent) {
		return """
				{"connectorId":"%s","source":"ENERLINK","periodStart":"%s","periodEnd":"%s",
				 "periodType":"ROLLING_24H","exactConfirmed":false,%s,"reportedPercent":%s,"origin":"MANUAL"}
				""".formatted(connector, START, END, minutes, percent);
	}

	private static String minutes(String available, String occupied, String fault, String offline) {
		return "\"availableMinutes\":%s,\"occupiedMinutes\":%s,\"faultMinutes\":%s,\"offlineMinutes\":%s"
				.formatted(available, occupied, fault, offline);
	}

	private ResultActions register(Cookie session, String json) throws Exception {
		return mvc.perform(post("/api/v1/uptime/measurements").cookie(session).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static UUID connector(UUID equipment, int ordinal) {
		return Feature005Fixtures.connectorId(equipment, ordinal);
	}

	private Integer count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	@Test
	void registersPendingMeasurementWithComputedUptime() throws Exception {
		register(analyst, body(connector(Feature005Fixtures.JAC_STATION, 1), minutes("1296", "105", "0", "39"), "97.29"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.result").value("CREATED"))
				.andExpect(jsonPath("$.reviewState").value("PENDING"))
				.andExpect(jsonPath("$.uptimePercent").value("97.29"))
				.andExpect(jsonPath("$.periodType").value("ROLLING_24H"))
				.andExpect(jsonPath("$.evaluatedMinutes").value(1440))
				.andExpect(jsonPath("$.availableMinutes").value(1296))
				.andExpect(jsonPath("$.observations").isEmpty());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'UPTIME_MEASUREMENT_CREATED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void twoConnectorsOfOneStationStayIndependent() throws Exception {
		register(analyst, body(connector(Feature005Fixtures.JAC_STATION, 1), minutes("1296", "105", "0", "39"), "null"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.uptimePercent").value("97.29"));
		register(analyst, body(connector(Feature005Fixtures.JAC_STATION, 2), minutes("1440", "0", "0", "0"), "null"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.uptimePercent").value("100.00"));
		assertThat(count("upt_measurement")).isEqualTo(2);
	}

	@Test
	void labelsRevisionWithCatalogAtFactDate() throws Exception {
		register(analyst, body(connector(Feature005Fixtures.JAC_STATION, 1), minutes("1440", "0", "0", "0"), "null"))
				.andExpect(status().isCreated());
		assertThat(jdbc.queryForMap("SELECT equipment_label, equipment_code, connector_type_label"
				+ " FROM upt_measurement_revision")).containsEntry("equipment_label", "QA005 JAC Estación")
				.containsEntry("equipment_code", "jac-1").containsEntry("connector_type_label", "Tipo 2 (Mennekes) AC");
	}

	@Test
	void kiaPointIsOutOfScope() throws Exception {
		register(analyst, body(connector(Feature005Fixtures.KIA_POINT, 1), minutes("1440", "0", "0", "0"), "null"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_OUT_OF_SCOPE"));
		assertThat(count("upt_measurement")).isZero();
	}

	@Test
	void unknownOrDeletedConnectorIsNotMeasurable() throws Exception {
		register(analyst, body(UUID.randomUUID(), minutes("1440", "0", "0", "0"), "null"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_CONNECTOR_NOT_MEASURABLE"));
		register(analyst, body(connector(Feature005Fixtures.DELETED_EQUIPMENT, 1), minutes("1440", "0", "0", "0"), "null"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_CONNECTOR_NOT_MEASURABLE"));
	}

	@Test
	void periodInTheFutureIsCaptureError() throws Exception {
		String json = body(connector(Feature005Fixtures.JAC_STATION, 1), minutes("1440", "0", "0", "0"), "null")
				.replace(START, "2099-01-01T00:00:00-05:00").replace(END, "2099-01-02T00:00:00-05:00");
		register(analyst, json).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_PERIOD_FUTURE"));
		assertThat(count("upt_measurement")).isZero();
	}

	@Test
	void textDurationIsCaptureErrorWithoutMeasurement() throws Exception {
		register(analyst, body(connector(Feature005Fixtures.JAC_STATION, 1), minutes("\"1h 20m\"", "0", "0", "0"), "null"))
				.andExpect(status().is4xxClientError());
		assertThat(count("upt_measurement")).isZero();
	}

	@Test
	void missingStateIsCaptureError() throws Exception {
		register(analyst, body(connector(Feature005Fixtures.JAC_STATION, 1), minutes("null", "0", "0", "0"), "null"))
				.andExpect(status().is4xxClientError());
		assertThat(count("upt_measurement")).isZero();
	}

	@Test
	void percentBeyondTechnicalRangeCreatesNothing() throws Exception {
		register(analyst, body(connector(Feature005Fixtures.JAC_STATION, 1), minutes("1440", "0", "0", "0"), "123456789"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_PERCENT_TOO_LARGE"));
		assertThat(count("upt_measurement")).isZero();
	}

	@Test
	void observedKeepsWhatWasTypedAndHasNoPercent() throws Exception {
		register(analyst, body(connector(Feature005Fixtures.JAC_STATION, 1), minutes("9.57", "0", "0", "0"), "9907"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reviewState").value("PENDING"))
				.andExpect(jsonPath("$.uptimePercent").doesNotExist())
				.andExpect(jsonPath("$.availableMinutes").doesNotExist())
				.andExpect(jsonPath("$.reportedPercent").value("9907"))
				.andExpect(jsonPath("$.observations[0]").value("DURATION_INVALID"));
		assertThat(jdbc.queryForObject("SELECT source_values ->> 'availableMinutes' FROM upt_measurement_revision",
				String.class)).isEqualTo("9.57");
	}

	@Test
	void consultationRoleCannotCapture() throws Exception {
		Cookie manager = WebSessions.login(mvc, "gerente@qa005.example.test", PASSWORD);
		register(manager, body(connector(Feature005Fixtures.JAC_STATION, 1), minutes("1440", "0", "0", "0"), "null"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/uptime/grid").cookie(manager).param("periodStart", START).param("periodEnd", END))
				.andExpect(status().isForbidden());
	}

	@Test
	void gridListsScopeInStableOrderWithExactIdentitySituation() throws Exception {
		UUID first = connector(Feature005Fixtures.JAC_STATION, 1);
		register(analyst, body(first, minutes("1440", "0", "0", "0"), "null")).andExpect(status().isCreated());

		mvc.perform(get("/api/v1/uptime/grid").cookie(analyst).param("periodStart", START).param("periodEnd", END)
				.param("equipmentId", Feature005Fixtures.JAC_STATION.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].connectorId").value(first.toString()))
				.andExpect(jsonPath("$[0].situation").value("PENDING"))
				.andExpect(jsonPath("$[0].measurementId").isNotEmpty())
				.andExpect(jsonPath("$[1].ordinal").value(2))
				.andExpect(jsonPath("$[1].situation").value("NONE"));

		mvc.perform(get("/api/v1/uptime/grid").cookie(analyst).param("periodStart", START)
				.param("periodEnd", "2026-10-31T14:30:00-05:00")
				.param("equipmentId", Feature005Fixtures.JAC_STATION.toString()))
				.andExpect(jsonPath("$[0].situation").value("NONE"));
	}

	@Test
	void gridHidesInactiveUnlessAskedAndNeverListsOutOfScope() throws Exception {
		mvc.perform(get("/api/v1/uptime/grid").cookie(analyst).param("periodStart", START).param("periodEnd", END)
				.param("equipmentId", Feature005Fixtures.JAC_STATION.toString()).param("showInactive", "true"))
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[2].connectorActive").value(false));
		mvc.perform(get("/api/v1/uptime/grid").cookie(analyst).param("periodStart", START).param("periodEnd", END)
				.param("equipmentId", Feature005Fixtures.KIA_POINT.toString()))
				.andExpect(jsonPath("$.length()").value(0));
	}
    private static String weeklyBody(UUID connector,String second) {
        return """
            {"connectorId":"%s","source":"ENERLINK","periodStart":"2026-10-28T08:06:%s-05:00",
             "periodEnd":"2026-11-04T08:06:%s-05:00","periodType":"ROLLING_7D","referenceWeek":"2026-10-26",
             "exactConfirmed":false,"availableMinutes":9815,"occupiedMinutes":31,"faultMinutes":0,
             "offlineMinutes":234,"origin":"BLOCK","sourceText":"bloque sintetico"}
            """.formatted(connector,second,second);
    }
    @Test void weeklyCapturePreservesReferenceSecondsAndIdempotence() throws Exception {
        UUID first=connector(Feature005Fixtures.JAC_STATION,1),second=connector(Feature005Fixtures.JAC_STATION,2);
        String a=weeklyBody(first,"20");
        register(analyst,a).andExpect(status().isCreated())
            .andExpect(jsonPath("$.referenceWeek").value("2026-10-26"))
            .andExpect(jsonPath("$.temporalPrecision").value("KNOWN_RANGE"))
            .andExpect(jsonPath("$.uptimePercent").value("97.68"))
            .andExpect(jsonPath("$.periodStart").value("2026-10-28T13:06:20Z"));
        register(analyst,weeklyBody(second,"45")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.periodStart").value("2026-10-28T13:06:45Z"));
        register(analyst,a).andExpect(status().isOk());
        register(analyst,a.replace("2026-10-26","2026-11-02"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPTIME_REFERENCE_DIFFERS"));
        assertThat(count("upt_measurement")).isEqualTo(2);
    }
    @Test void weeklyGridNeedsReferenceRatherThanCommonLimits() throws Exception {
        register(analyst,weeklyBody(connector(Feature005Fixtures.JAC_STATION,1),"20"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/uptime/grid").cookie(analyst).param("referenceWeek","2026-10-26"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.situation == 'PENDING')]").isNotEmpty());
        mvc.perform(get("/api/v1/uptime/grid").cookie(analyst).param("referenceWeek","2026-10-26")
            .param("periodStart",START).param("periodEnd",END)).andExpect(status().isUnprocessableContent());
    }
    @Test void weeklyGridDoesNotTreatAnotherWindowAsWeeklyCapture() throws Exception {
        UUID id=connector(Feature005Fixtures.JAC_STATION,1);
        register(analyst,weeklyBody(id,"20").replace("ROLLING_7D","ROLLING_24H")).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/uptime/grid").cookie(analyst).param("referenceWeek","2026-10-26"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.connectorId == '"+id+"' && @.situation == 'NONE')]").isNotEmpty());
    }

}
