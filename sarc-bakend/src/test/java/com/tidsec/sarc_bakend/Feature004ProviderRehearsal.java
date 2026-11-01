package com.tidsec.sarc_bakend;

import static com.tidsec.sarc_bakend.support.WebSessions.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature004Fixtures;

/**
 * T066, opt-in con {@code -Dsarc.qa.provider=true}: ensayo contra Cloudinary real con datos sinteticos,
 * en una carpeta propia. Registra solo estados HTTP, nunca URLs firmadas ni credenciales.
 */
@EnabledIfSystemProperty(named = "sarc.qa.provider", matches = "true")
@SpringBootTest(properties = {
		"sarc.security.bcrypt-strength=4",
		"spring.docker.compose.enabled=false",
		"sarc.storage.cloudinary.root-folder=sarc-qa004-t066"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class Feature004ProviderRehearsal {

	private static final String PASSWORD = "Prueba-Feature004-2026";
	private static final Duration PAST_EXPIRY = Duration.ofMinutes(5).plusSeconds(30);

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired PasswordEncoder encoder;
	@Autowired JdbcTemplate jdbc;
	@Autowired FileStorage storage;

	private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	@Test
	void privateDeliveryAgainstTheRealProvider() throws Exception {
		assertThat(storage.getClass().getSimpleName()).contains("Cloudinary");
		String hash = encoder.encode(PASSWORD);
		UserAccount analyst = UserAccount.create(new Email("analista@qa004.example.test"), "QA004 analista",
				Set.of(Role.ANALISTA), hash);
		analyst.changePassword(hash);
		users.save(analyst);
		Feature004Fixtures.seed(jdbc, false);
		var cookie = login(mvc, "analista@qa004.example.test", PASSWORD);

		MockHttpServletResponse issued = mvc.perform(get("/api/v1/energy/report").param("format", "PDF")
				.param("from", "2026-10-01").param("to", "2026-11-01").cookie(cookie))
				.andExpect(status().isOk()).andReturn().getResponse();
		UUID id = UUID.fromString(issued.getHeader("X-Report-Id"));
		String reference = jdbc.queryForObject("select file_ref from report_issued where id = ?", String.class, id);
		assertThat(reference).startsWith("sarc-qa004-t066/");

		Map<String, Object> results = new LinkedHashMap<>();
		MockHttpServletResponse downloaded = download(id, cookie);
		results.put("sarcDownloadStatus", downloaded.getStatus());
		results.put("sarcBytesEqual", java.util.Arrays.equals(downloaded.getContentAsByteArray(), issued.getContentAsByteArray()));
		results.put("sarcLocationHeader", downloaded.getHeader("Location"));
		results.put("sarcHeadersMentionProvider", downloaded.getHeaderNames().stream()
				.anyMatch(name -> String.valueOf(downloaded.getHeader(name)).contains("cloudinary")));

		String cloud = System.getenv("CLOUDINARY_CLOUD_NAME");
		for (String type : new String[] { "authenticated", "upload", "private" }) {
			results.put("unsigned_" + type, statusOf(
					"https://res.cloudinary.com/" + cloud + "/raw/" + type + "/" + reference));
		}

		String signed = storage.documentUrl(reference).orElseThrow();
		HttpResponse<Void> fresh = fetch(signed);
		results.put("signedFreshStatus", fresh.statusCode());
		String redirect = fresh.headers().firstValue("location").orElse(null);
		results.put("signedFreshRedirects", redirect != null);
		if (redirect != null) {
			results.put("redirectFreshStatus", statusOf(redirect));
		}

		Thread.sleep(PAST_EXPIRY.toMillis());

		results.put("signedExpiredStatus", fetch(signed).statusCode());
		if (redirect != null) {
			results.put("redirectExpiredStatus", statusOf(redirect));
		}
		MockHttpServletResponse again = download(id, cookie);
		results.put("sarcDownloadAfterExpiryStatus", again.getStatus());
		results.put("sarcBytesEqualAfterExpiry", java.util.Arrays.equals(again.getContentAsByteArray(), issued.getContentAsByteArray()));

		Files.writeString(Path.of("target/feature004-provider.json"), results.toString());
		assertThat(results).containsEntry("sarcDownloadStatus", 200).containsEntry("sarcBytesEqual", true)
				.containsEntry("sarcHeadersMentionProvider", false).containsEntry("sarcDownloadAfterExpiryStatus", 200)
				.containsEntry("sarcBytesEqualAfterExpiry", true);
		assertThat(results.get("sarcLocationHeader")).isNull();
		assertThat((int) results.get("signedExpiredStatus")).isNotEqualTo(200);
	}

	private MockHttpServletResponse download(UUID id, jakarta.servlet.http.Cookie cookie) throws Exception {
		return mvc.perform(get("/api/v1/reports/issued/" + id + "/download").cookie(cookie)).andReturn().getResponse();
	}

	private int statusOf(String url) throws Exception {
		return fetch(url).statusCode();
	}

	private HttpResponse<Void> fetch(String url) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
				HttpResponse.BodyHandlers.discarding());
	}
}
