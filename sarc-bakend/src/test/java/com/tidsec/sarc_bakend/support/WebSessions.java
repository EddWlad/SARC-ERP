package com.tidsec.sarc_bakend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Sesion y CSRF reales en pruebas de API, tal como los usa la SPA. */
public final class WebSessions {

	public static final String SESSION_COOKIE = "SARC_SESSION";
	public static final String XSRF_TOKEN = "token-de-prueba";

	private WebSessions() {
	}

	/**
	 * CSRF real por cookie. No se usa csrf() de spring-security-test porque reemplaza el
	 * repositorio de tokens del filtro durante el resto del contexto y ocultaria la configuracion real.
	 */
	public static RequestPostProcessor xsrf() {
		return request -> {
			List<Cookie> cookies = new ArrayList<>(request.getCookies() == null ? List.of() : List.of(request.getCookies()));
			cookies.add(new Cookie("XSRF-TOKEN", XSRF_TOKEN));
			request.setCookies(cookies.toArray(Cookie[]::new));
			request.addHeader("X-XSRF-TOKEN", XSRF_TOKEN);
			return request;
		};
	}

	public static MockHttpServletRequestBuilder loginRequest(String email, String password) {
		return post("/api/v1/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email, password));
	}

	public static String credentials(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	public static Cookie login(MockMvc mvc, String email, String password) throws Exception {
		Cookie cookie = cookieFrom(mvc.perform(loginRequest(email, password)).andExpect(status().isOk()).andReturn(),
				SESSION_COOKIE);
		assertThat(cookie).isNotNull();
		return cookie;
	}

	public static Cookie cookieFrom(MvcResult result, String name) {
		return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
				.filter(header -> header.startsWith(name + "="))
				.findFirst()
				.<Cookie>map(MockCookie::parse)
				.orElse(null);
	}
}
