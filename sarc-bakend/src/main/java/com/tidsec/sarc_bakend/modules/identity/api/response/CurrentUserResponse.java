package com.tidsec.sarc_bakend.modules.identity.api.response;

import java.util.Set;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;

/** Sesion actual. Si {@code mustChangePassword} es true, el cliente solo puede cambiar la clave o salir. */
public record CurrentUserResponse(UUID id, String email, String fullName, Set<Role> roles, boolean mustChangePassword) {

	public static CurrentUserResponse from(UserAccount user) {
		return new CurrentUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRoles(),
				user.isMustChangePassword());
	}
}
