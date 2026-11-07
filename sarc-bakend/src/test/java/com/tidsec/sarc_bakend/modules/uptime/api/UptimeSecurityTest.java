package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.support.Feature005Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

/** Matriz de roles sobre las rutas de uptime, por acceso directo (FR-041, SC-007). */
class UptimeSecurityTest extends UptimeApiSupport {

	private static final List<String> READS = List.of("/summary", "/results/connectors", "/results/equipment",
			"/results/exclusions", "/measurements", "/scope/connectors");
	private static final List<String> OPERATIONAL_READS = List.of(
			"/grid?periodStart=2026-10-30T14:00:00-05:00&periodEnd=2026-10-31T14:00:00-05:00",
			"/measurements?queue=PENDING", "/measurements?queue=TEMPORAL_RECONCILIATION", "/measurements/%s/revisions");
	private static final List<String> WRITES = List.of("/measurements", "/measurements/classifications",
			"/measurements/validation", "/measurements/%s/corrections", "/measurements/%s/validate",
			"/measurements/%s/exclude", "/measurements/%s/restore");

	@org.springframework.test.context.bean.override.mockito.MockitoBean com.tidsec.sarc_bakend.shared.importing.batch.ImportJobs jobs;
    private String measurementId;

	@BeforeEach
	void measurement() throws Exception {
		measurementId = registered(JAC_1, "1401", "39").get("id").toString();
		validate(measurementId);
	}

	private Cookie session(Set<Role> roles) throws Exception {
		String name = "u" + UUID.randomUUID().toString().substring(0, 8);
		String hash = encoder.encode(PASSWORD);
		UserAccount account = UserAccount.create(new Email(name + "@qa005.example.test"), name, roles, hash);
		account.changePassword(hash);
		users.save(account);
		return WebSessions.login(mvc, name + "@qa005.example.test", PASSWORD);
	}

	private static boolean operational(Set<Role> roles) {
		return roles.contains(Role.ADMINISTRADOR) || roles.contains(Role.ANALISTA);
	}

	private int statusOf(RequestBuilder request) throws Exception {
		return mvc.perform(request).andReturn().getResponse().getStatus();
	}

	private static List<Set<Role>> roleSets() {
		return Feature005Fixtures.ROLE_SETS;
	}

	@ParameterizedTest
	@MethodSource("roleSets")
	void everyRoleReads(Set<Role> roles) throws Exception {
		Cookie session = session(roles);
		for (String path : READS) {
			assertThat(statusOf(get("/api/v1/uptime" + path).cookie(session))).as(path).isEqualTo(200);
		}
	}

	@ParameterizedTest
	@MethodSource("roleSets")
	void operationalReadsAreDenied(Set<Role> roles) throws Exception {
		Cookie session = session(roles);
		for (String path : OPERATIONAL_READS) {
			assertThat(statusOf(get("/api/v1/uptime" + path.formatted(measurementId)).cookie(session))).as(path)
					.isEqualTo(operational(roles) ? 200 : 403);
		}
	}

	@ParameterizedTest
	@MethodSource("roleSets")
	void writesAreDeniedToConsultation(Set<Role> roles) throws Exception {
		Cookie session = session(roles);
		for (String path : WRITES) {
			int status = statusOf(post("/api/v1/uptime" + path.formatted(measurementId)).cookie(session)
					.with(WebSessions.xsrf()).contentType(MediaType.APPLICATION_JSON)
					.content(path.endsWith("validation") ? "{\"items\":[{\"id\":\"%s\",\"version\":0}]}".formatted(measurementId)
							: "{}"));
			if (operational(roles)) {
				assertThat(status).as(path).isNotIn(401, 403);
			}
			else if (!path.endsWith("/measurements") && !path.endsWith("classifications")) {
				assertThat(status).as(path).isIn(400, 403);
			}
		}
	}

	@Test
	void validBodyStillForbiddenToConsultation() throws Exception {
		send(manager, "/measurements", UptimeApiSupport.measurement(JAC_2, "1440", "0")).andExpect(status().isForbidden());
		send(manager, "/measurements/%s/validate".formatted(measurementId), "{\"version\":0}")
				.andExpect(status().isForbidden());
		send(manager, "/measurements/%s/exclude".formatted(measurementId), "{\"version\":0,\"justification\":\"x\"}")
				.andExpect(status().isForbidden());
		send(manager, "/measurements/%s/restore".formatted(measurementId), "{\"version\":0,\"justification\":\"x\"}")
				.andExpect(status().isForbidden());
	}

	@Test
	void anonymousIsRejected() throws Exception {
		assertThat(statusOf(get("/api/v1/uptime/summary"))).isEqualTo(401);
	}
    @ParameterizedTest @MethodSource("roleSets")
    void temporalWorkActionsRespectRoleUnion(Set<Role> roles) throws Exception {
        Cookie session=session(roles);
        String body="{\"items\":[{\"id\":\""+measurementId+"\",\"version\":"+version(measurementId)+"}]}";
        String accepted=mvc.perform(post("/api/v1/uptime/measurements/temporal-transition").cookie(analyst).with(WebSessions.xsrf())
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String work=com.jayway.jsonpath.JsonPath.read(accepted,"$.workId");
        assertThat(statusOf(get("/api/v1/uptime/measurements/temporal-transition/"+work).cookie(session))).isEqualTo(operational(roles)?200:403);
        jdbc.update("UPDATE upt_temporal_transition_work SET status='FAILED' WHERE id=?::uuid",work);
        assertThat(statusOf(post("/api/v1/uptime/measurements/temporal-transition/"+work+"/retry").cookie(session).with(WebSessions.xsrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))).isEqualTo(operational(roles)?202:403);
        assertThat(statusOf(post("/api/v1/uptime/measurements/temporal-transition").cookie(session).with(WebSessions.xsrf())
            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body)))
            .isEqualTo(operational(roles)?202:403);
    }

}
