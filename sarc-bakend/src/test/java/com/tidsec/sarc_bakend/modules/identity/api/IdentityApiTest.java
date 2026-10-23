package com.tidsec.sarc_bakend.modules.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.tidsec.sarc_bakend.support.WebSessions.SESSION_COOKIE;
import static com.tidsec.sarc_bakend.support.WebSessions.XSRF_TOKEN;
import static com.tidsec.sarc_bakend.support.WebSessions.cookieFrom;
import static com.tidsec.sarc_bakend.support.WebSessions.credentials;
import static com.tidsec.sarc_bakend.support.WebSessions.login;
import static com.tidsec.sarc_bakend.support.WebSessions.loginRequest;
import static com.tidsec.sarc_bakend.support.WebSessions.xsrf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;

import com.jayway.jsonpath.JsonPath;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.identity.application.service.LoginService;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;

@SpringBootTest(properties = {
		"sarc.security.bcrypt-strength=4",
		"sarc.security.max-failed-login-attempts=3",
		"spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IdentityApiTest {

	private static final String PASSWORD = "clave-de-prueba-123";

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired PasswordEncoder encoder;
	@Autowired JdbcTemplate jdbc;
	@Autowired LoginService loginService;
	@Autowired EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE spring_session, spring_session_attributes, audit_event, auth_user_role, auth_user CASCADE");
	}

	// ---- autenticacion

	@Test
	void loginCreatesSessionAndMeReturnsTheUser() throws Exception {
		UserAccount admin = seedUser("admin@eos.com", Role.ADMINISTRADOR);

		Cookie session = login(mvc, "Admin@EOS.com", PASSWORD);

		mvc.perform(get("/api/v1/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(admin.getId().toString()))
				.andExpect(jsonPath("$.email").value("admin@eos.com"))
				.andExpect(jsonPath("$.roles[0]").value("ADMINISTRADOR"))
				.andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	@Test
	void sessionCookieIsHttpOnlyAndSameSiteStrict() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);

		MockCookie session = MockCookie.parse(mvc.perform(loginRequest("admin@eos.com", PASSWORD)).andReturn()
				.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
				.filter(header -> header.startsWith(SESSION_COOKIE + "=")).findFirst().orElseThrow());

		assertThat(session.isHttpOnly()).isTrue();
		assertThat(session.getSameSite()).isEqualTo("Strict");
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveTheSameGenericError() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);

		mvc.perform(loginRequest("admin@eos.com", "otra-clave-equivocada"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		mvc.perform(loginRequest("nadie@eos.com", PASSWORD))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void accountLocksAfterRepeatedFailuresEvenWithTheRightPassword() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);

		for (int i = 0; i < 3; i++) {
			mvc.perform(loginRequest("admin@eos.com", "otra-clave-equivocada")).andExpect(status().isUnauthorized());
		}

		mvc.perform(loginRequest("admin@eos.com", PASSWORD))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void parallelFailedLoginsAreAllCountedWithoutConflicts() throws Exception {
		UserAccount user = seedUser("admin@eos.com", Role.ADMINISTRADOR);
		List<Callable<String>> attempts = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			attempts.add(() -> {
				try {
					loginService.login("admin@eos.com", "otra-clave-equivocada");
					return "LOGGED_IN";
				}
				catch (AuthenticationException ex) {
					return "REJECTED";
				}
			});
		}

		List<String> outcomes = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
			for (Future<String> outcome : pool.invokeAll(attempts)) {
				outcomes.add(outcome.get());
			}
		}

		assertThat(outcomes).containsOnly("REJECTED");
		assertThat(users.findById(user.getId()).orElseThrow().getLockedUntil()).isNotNull();
		mvc.perform(loginRequest("admin@eos.com", PASSWORD)).andExpect(status().isUnauthorized());
	}

	@Test
	void loginAttemptsDoNotChangeTheAccountVersionSoAdminEditsKeepWorking() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		UserAccount target = seedUser("ana@eos.com", Role.ANALISTA);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);

		mvc.perform(loginRequest("ana@eos.com", "otra-clave-equivocada")).andExpect(status().isUnauthorized());
		login(mvc, "ana@eos.com", PASSWORD);

		mvc.perform(patchUser(adminSession, target.getId(), "{\"version\":%d,\"fullName\":\"Ana B\"}".formatted(target.getVersion())))
				.andExpect(status().isOk());
	}

	@Test
	void loginRotatesTheCsrfToken() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);

		List<String> csrfCookies = mvc.perform(loginRequest("admin@eos.com", PASSWORD))
				.andExpect(status().isOk())
				.andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
				.filter(header -> header.startsWith("XSRF-TOKEN="))
				.toList();

		assertThat(csrfCookies).isNotEmpty();
		String lastValue = MockCookie.parse(csrfCookies.getLast()).getValue();
		assertThat(lastValue).isNotBlank().isNotEqualTo(XSRF_TOKEN);
	}

	@Test
	void unauthenticatedRequestGets401ProblemWithTraceIdAndCsrfCookieButNoSession() throws Exception {
		mvc.perform(get("/api/v1/auth/me").header("X-Request-ID", "trace-1234-abcd"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("X-Request-ID", "trace-1234-abcd"))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("XSRF-TOKEN=")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString(SESSION_COOKIE))))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.traceId").value("trace-1234-abcd"))
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void postWithoutCsrfTokenIsForbiddenAndWithCookieTokenIsAccepted() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("admin@eos.com", PASSWORD)))
				.andExpect(status().isForbidden());

		Cookie xsrf = cookieFrom(mvc.perform(get("/api/v1/auth/me")).andReturn(), "XSRF-TOKEN");
		assertThat(xsrf).isNotNull();
		mvc.perform(post("/api/v1/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(credentials("admin@eos.com", PASSWORD)))
				.andExpect(status().isOk());
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		Cookie session = login(mvc, "admin@eos.com", PASSWORD);

		mvc.perform(post("/api/v1/auth/logout").with(xsrf()).cookie(session)).andExpect(status().isNoContent());

		mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
	}

	// ---- autorizacion

	@Test
	void nonAdministratorCannotUseUserManagement() throws Exception {
		seedUser("gerente@eos.com", Role.GERENTE);
		Cookie session = login(mvc, "gerente@eos.com", PASSWORD);

		mvc.perform(get("/api/v1/users").cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mvc.perform(post("/api/v1/users").with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content(createUserJson("x@eos.com", "X", "ANALISTA")))
				.andExpect(status().isForbidden());
	}

	@Test
	void userWithSeveralRolesGetsTheUnionOfPermissions() throws Exception {
		seedUser("mixto@eos.com", Role.ANALISTA, Role.ADMINISTRADOR);
		Cookie session = login(mvc, "mixto@eos.com", PASSWORD);

		mvc.perform(get("/api/v1/users").cookie(session)).andExpect(status().isOk());
	}

	// ---- gestion de usuarios

	@Test
	void adminCreatesUserWhoMustChangeTheTemporaryPasswordBeforeAnythingElse() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);

		MvcResult created = mvc.perform(post("/api/v1/users").with(xsrf()).cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON).content(createUserJson("Nueva@EOS.com", "Nueva Persona", "ANALISTA")))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.user.email").value("nueva@eos.com"))
				.andExpect(jsonPath("$.user.mustChangePassword").value(true))
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
				.andReturn();
		String temporary = jsonString(created, "$.temporaryPassword");

		Cookie session = login(mvc, "nueva@eos.com", temporary);
		mvc.perform(get("/api/v1/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mustChangePassword").value(true));
		mvc.perform(get("/api/v1/users").cookie(session)).andExpect(status().isForbidden());

		String chosen = "mi-nueva-clave-segura";
		mvc.perform(post("/api/v1/auth/password/change").with(xsrf()).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(temporary, chosen)))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
		Cookie fresh = login(mvc, "nueva@eos.com", chosen);
		mvc.perform(get("/api/v1/auth/me").cookie(fresh))
				.andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	@Test
	void duplicatedEmailIsAConflict() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);

		mvc.perform(post("/api/v1/users").with(xsrf()).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
				.content(createUserJson("ADMIN@eos.com", "Otro", "ANALISTA")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
	}

	@Test
	void invalidBodyReturnsFieldErrors() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);

		mvc.perform(post("/api/v1/users").with(xsrf()).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"\",\"fullName\":\"X\",\"roles\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field=='roles')]").exists());
	}

	@Test
	void nullRolesAndOversizedFiltersAreRejectedAsBadRequests() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);

		mvc.perform(post("/api/v1/users").with(xsrf()).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"x@eos.com\",\"fullName\":\"X\",\"roles\":[null]}"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/users").cookie(adminSession).param("text", "a".repeat(101)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void listSupportsFiltersAndPagination() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		seedUser("ana@eos.com", Role.ANALISTA);
		seedUser("beto@eos.com", Role.ANALISTA);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);

		mvc.perform(get("/api/v1/users").cookie(adminSession).param("role", "ANALISTA").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content.length()").value(1));
		mvc.perform(get("/api/v1/users").cookie(adminSession).param("text", "BET"))
				.andExpect(jsonPath("$.content[0].email").value("beto@eos.com"));
		mvc.perform(get("/api/v1/users").cookie(adminSession).param("size", "1000"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void listingUsersLoadsRolesInBatchesInsteadOfOneQueryPerUser() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		for (int i = 0; i < 6; i++) {
			seedUser("usuario" + i + "@eos.com", Role.ANALISTA, Role.SUPERVISOR);
		}
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		mvc.perform(get("/api/v1/users").cookie(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(7));

		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
	}

	@Test
	void updateWithStaleVersionIsRejected() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		UserAccount target = seedUser("ana@eos.com", Role.ANALISTA);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);

		mvc.perform(patchUser(adminSession, target.getId(), "{\"version\":99,\"fullName\":\"Ana B\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
		mvc.perform(patchUser(adminSession, target.getId(), "{\"version\":%d,\"fullName\":\"Ana B\"}".formatted(target.getVersion())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Ana B"))
				.andExpect(jsonPath("$.version").value(target.getVersion() + 1));
	}

	@Test
	void deactivatingUserRevokesTheirSessionAndBlocksNewLogins() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		UserAccount target = seedUser("ana@eos.com", Role.ANALISTA);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);
		Cookie anaSession = login(mvc, "ana@eos.com", PASSWORD);
		mvc.perform(get("/api/v1/auth/me").cookie(anaSession)).andExpect(status().isOk());

		mvc.perform(patchUser(adminSession, target.getId(),
				"{\"version\":%d,\"status\":\"INACTIVE\"}".formatted(target.getVersion())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INACTIVE"));

		mvc.perform(get("/api/v1/auth/me").cookie(anaSession)).andExpect(status().isUnauthorized());
		mvc.perform(loginRequest("ana@eos.com", PASSWORD)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/auth/me").cookie(adminSession)).andExpect(status().isOk());
		List<String> actions = jdbc.queryForList(
				"select action from audit_event where entity_type = 'USER' and entity_id = ? order by occurred_at",
				String.class, target.getId());
		assertThat(actions).containsExactly("USER_DEACTIVATED");
	}

	@Test
	void changingRolesRevokesSessionsSoNewPermissionsApplyOnNextLogin() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		UserAccount target = seedUser("ana@eos.com", Role.ANALISTA);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);
		Cookie anaSession = login(mvc, "ana@eos.com", PASSWORD);

		mvc.perform(patchUser(adminSession, target.getId(),
				"{\"version\":%d,\"roles\":[\"GERENTE\"]}".formatted(target.getVersion())))
				.andExpect(status().isOk());

		mvc.perform(get("/api/v1/auth/me").cookie(anaSession)).andExpect(status().isUnauthorized());
		Cookie fresh = login(mvc, "ana@eos.com", PASSWORD);
		mvc.perform(get("/api/v1/auth/me").cookie(fresh)).andExpect(jsonPath("$.roles[0]").value("GERENTE"));
	}

	@Test
	void administratorCannotDeactivateThemselves() throws Exception {
		UserAccount admin = seedUser("admin@eos.com", Role.ADMINISTRADOR);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);

		mvc.perform(patchUser(adminSession, admin.getId(),
				"{\"version\":%d,\"status\":\"INACTIVE\"}".formatted(admin.getVersion())))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
	}

	@Test
	void resetPasswordIssuesTemporaryKeyAndKillsExistingSessions() throws Exception {
		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		UserAccount target = seedUser("ana@eos.com", Role.ANALISTA);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);
		Cookie anaSession = login(mvc, "ana@eos.com", PASSWORD);

		MvcResult reset = mvc.perform(post("/api/v1/users/{id}/reset-password", target.getId()).with(xsrf())
				.cookie(adminSession))
				.andExpect(status().isOk())
				.andReturn();

		mvc.perform(get("/api/v1/auth/me").cookie(anaSession)).andExpect(status().isUnauthorized());
		mvc.perform(loginRequest("ana@eos.com", PASSWORD)).andExpect(status().isUnauthorized());
		Cookie fresh = login(mvc, "ana@eos.com", jsonString(reset, "$.temporaryPassword"));
		mvc.perform(get("/api/v1/auth/me").cookie(fresh)).andExpect(jsonPath("$.mustChangePassword").value(true));
		mvc.perform(post("/api/v1/users/{id}/reset-password", UUID.randomUUID()).with(xsrf()).cookie(adminSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void changePasswordRejectsWrongCurrentAndWeakNewPassword() throws Exception {
		seedUser("ana@eos.com", Role.ANALISTA);
		Cookie session = login(mvc, "ana@eos.com", PASSWORD);

		mvc.perform(changePassword(session, "no-es-la-actual-123", "nueva-clave-segura-1"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
		mvc.perform(changePassword(session, PASSWORD, "corta"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
		mvc.perform(changePassword(session, PASSWORD, PASSWORD))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
	}

	// ---- operaciones

	@Test
	void actuatorHealthIsPublicButOtherEndpointsNeedAdministrator() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
		mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());

		seedUser("admin@eos.com", Role.ADMINISTRADOR);
		Cookie adminSession = login(mvc, "admin@eos.com", PASSWORD);
		mvc.perform(get("/v3/api-docs").cookie(adminSession)).andExpect(status().isOk());
	}

	// ---- helpers

	private UserAccount seedUser(String email, Role... roles) {
		UserAccount user = UserAccount.create(new Email(email), "Usuario " + email, Set.of(roles), encoder.encode(PASSWORD));
		user.changePassword(encoder.encode(PASSWORD));
		return users.save(user);
	}

	private MockHttpServletRequestBuilder patchUser(Cookie session,
			UUID id, String body) {
		return patch("/api/v1/users/{id}", id).with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	private MockHttpServletRequestBuilder changePassword(Cookie session,
			String current, String next) {
		return post("/api/v1/auth/password/change").with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next));
	}

	private static String createUserJson(String email, String fullName, String role) {
		return "{\"email\":\"%s\",\"fullName\":\"%s\",\"roles\":[\"%s\"]}".formatted(email, fullName, role);
	}

	private static String jsonString(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}
}
