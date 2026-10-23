package com.tidsec.sarc_bakend.modules.identity.api.request;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.tidsec.sarc_bakend.modules.identity.application.command.UpdateUserCommand;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserStatus;

/** Los campos omitidos no se modifican. {@code version} es la que el cliente leyo del recurso. */
public record UpdateUserRequest(
		@NotNull Long version,
		@Size(min = 1, max = 150) String fullName,
		Set<Role> roles,
		UserStatus status) {

	public UpdateUserCommand toCommand(UUID id) {
		return new UpdateUserCommand(id, version, fullName, roles, status);
	}
}
