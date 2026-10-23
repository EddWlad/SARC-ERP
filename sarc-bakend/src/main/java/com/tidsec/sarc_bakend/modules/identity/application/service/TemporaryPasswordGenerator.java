package com.tidsec.sarc_bakend.modules.identity.application.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** Genera contrasenas temporales aleatorias sin caracteres ambiguos (0/O, 1/l/I). */
@Component
class TemporaryPasswordGenerator {

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
	private static final int LENGTH = 16;

	private final SecureRandom random = new SecureRandom();

	String generate() {
		StringBuilder password = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return password.toString();
	}
}
