package com.tidsec.sarc_bakend.modules.identity.application.command;

import java.util.Set;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserStatus;

/** Actualizacion parcial: los campos {@code null} no se modifican. {@code version} es la que leyo el cliente. */
public record UpdateUserCommand(UUID id, long version, String fullName, Set<Role> roles, UserStatus status) {
}
