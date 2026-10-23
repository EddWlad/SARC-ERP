package com.tidsec.sarc_bakend.modules.identity.application.command;

import java.util.Set;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;

public record CreateUserCommand(String email, String fullName, Set<Role> roles) {
}
