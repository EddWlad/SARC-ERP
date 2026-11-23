package com.tidsec.sarc_bakend.shared.security;

import java.util.UUID;

/** Persona que puede aparecer como responsable de un ticket. Solo identidad y nombre: ni correo ni roles. */
public record StaffMember(UUID id, String fullName) {
}
