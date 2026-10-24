package com.tidsec.sarc_bakend.modules.identity.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.identity.application.port.LoginThrottle;
import com.tidsec.sarc_bakend.modules.identity.domain.policy.LoginThrottlePolicy;
import com.tidsec.sarc_bakend.shared.error.TooManyRequestsException;

/**
 * Ventana fija por origen, en memoria: el backend es una sola instancia (TRD 17). Si se escala a
 * varias, este adaptador se reemplaza por uno compartido (base de datos o cache).
 */
@Component
class InMemoryLoginThrottle implements LoginThrottle {

	/** Al superar este numero de origenes se descartan las ventanas vencidas. */
	private static final int PRUNE_THRESHOLD = 10_000;

	private final Map<String, Window> windows = new ConcurrentHashMap<>();
	private final LoginThrottlePolicy policy;
	private final Clock clock;

	InMemoryLoginThrottle(LoginThrottlePolicy policy, Clock clock) {
		this.policy = policy;
		this.clock = clock;
	}

	@Override
	public void requireAllowed(String client) {
		Instant now = clock.instant();
		Window window = windows.get(client);
		if (window != null && !window.expired(now, policy.window()) && window.failures() >= policy.maxFailures()) {
			throw new TooManyRequestsException("TOO_MANY_LOGIN_ATTEMPTS",
					"Demasiados intentos de ingreso; espere unos minutos e intente de nuevo",
					Duration.between(now, window.start().plus(policy.window())));
		}
	}

	@Override
	public void registerFailure(String client) {
		Instant now = clock.instant();
		windows.merge(client, new Window(now, 1), (current, added) -> current.expired(now, policy.window())
				? added
				: new Window(current.start(), current.failures() + 1));
		if (windows.size() > PRUNE_THRESHOLD) {
			windows.values().removeIf(window -> window.expired(now, policy.window()));
		}
	}

	private record Window(Instant start, int failures) {

		boolean expired(Instant now, Duration length) {
			return !now.isBefore(start.plus(length));
		}
	}
}
