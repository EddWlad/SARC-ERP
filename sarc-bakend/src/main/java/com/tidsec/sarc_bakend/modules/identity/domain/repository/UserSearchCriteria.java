package com.tidsec.sarc_bakend.modules.identity.domain.repository;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserStatus;

/** Filtros opcionales del listado administrativo; {@code text} busca en nombre y correo. */
public record UserSearchCriteria(String text, Role role, UserStatus status) {
}
