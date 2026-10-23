package com.tidsec.sarc_bakend.modules.identity.api.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserStatus;

public record UserResponse(UUID id, String email, String fullName, Set<Role> roles, UserStatus status,
		boolean mustChangePassword, Instant lockedUntil, Instant createdAt, Instant updatedAt, long version) {

	public static UserResponse from(UserAccount user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRoles(), user.getStatus(),
				user.isMustChangePassword(), user.getLockedUntil(), user.getCreatedAt(), user.getUpdatedAt(),
				user.getVersion());
	}
}
