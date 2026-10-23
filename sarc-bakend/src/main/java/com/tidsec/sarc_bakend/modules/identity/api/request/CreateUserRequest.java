package com.tidsec.sarc_bakend.modules.identity.api.request;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.tidsec.sarc_bakend.modules.identity.application.command.CreateUserCommand;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;

public record CreateUserRequest(
		@NotBlank @Size(max = 254) String email,
		@NotBlank @Size(max = 150) String fullName,
		@NotEmpty Set<Role> roles) {

	public CreateUserCommand toCommand() {
		return new CreateUserCommand(email, fullName, roles);
	}
}
