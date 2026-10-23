package com.tidsec.sarc_bakend.modules.identity.api.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.identity.api.request.CreateUserRequest;
import com.tidsec.sarc_bakend.modules.identity.api.request.UpdateUserRequest;
import com.tidsec.sarc_bakend.modules.identity.api.response.CreatedUserResponse;
import com.tidsec.sarc_bakend.modules.identity.api.response.TemporaryPasswordResponse;
import com.tidsec.sarc_bakend.modules.identity.api.response.UserResponse;
import com.tidsec.sarc_bakend.modules.identity.application.result.CreatedUser;
import com.tidsec.sarc_bakend.modules.identity.application.service.UserManagementService;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserStatus;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserSearchCriteria;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

	private final UserManagementService userService;

	UserController(UserManagementService userService) {
		this.userService = userService;
	}

	@GetMapping
	PageResult<UserResponse> list(
			@RequestParam(required = false) @Size(max = 100) String text,
			@RequestParam(required = false) Role role,
			@RequestParam(required = false) UserStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return userService.search(new UserSearchCriteria(text, role, status), new PageQuery(page, size))
				.map(UserResponse::from);
	}

	@PostMapping
	ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest body) {
		CreatedUser created = userService.create(body.toCommand());
		URI location = URI.create("/api/v1/users/" + created.user().getId());
		return ResponseEntity.created(location).body(CreatedUserResponse.from(created));
	}

	@PatchMapping("/{id}")
	UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest body) {
		return UserResponse.from(userService.update(body.toCommand(id)));
	}

	@PostMapping("/{id}/reset-password")
	TemporaryPasswordResponse resetPassword(@PathVariable UUID id) {
		return new TemporaryPasswordResponse(userService.resetPassword(id));
	}
}
