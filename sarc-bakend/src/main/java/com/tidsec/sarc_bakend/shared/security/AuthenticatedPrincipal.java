package com.tidsec.sarc_bakend.shared.security;

import java.util.UUID;

/** Contrato minimo que expone el principal autenticado al resto de modulos (auditoria, autoria). */
public interface AuthenticatedPrincipal {

	UUID id();
}
