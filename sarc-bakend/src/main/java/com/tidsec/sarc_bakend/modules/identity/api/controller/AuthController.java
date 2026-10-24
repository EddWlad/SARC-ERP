package com.tidsec.sarc_bakend.modules.identity.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.identity.api.request.ChangePasswordRequest;
import com.tidsec.sarc_bakend.modules.identity.api.request.LoginRequest;
import com.tidsec.sarc_bakend.modules.identity.api.response.CurrentUserResponse;
import com.tidsec.sarc_bakend.modules.identity.application.service.AccountService;
import com.tidsec.sarc_bakend.modules.identity.application.service.LoginService;
import com.tidsec.sarc_bakend.shared.security.AuthenticatedPrincipal;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	private final LoginService loginService;
	private final AccountService accountService;
	private final SecurityContextRepository securityContextRepository;
	private final SessionAuthenticationStrategy sessionStrategy;

	AuthController(LoginService loginService, AccountService accountService,
			SecurityContextRepository securityContextRepository, SessionAuthenticationStrategy sessionStrategy) {
		this.loginService = loginService;
		this.accountService = accountService;
		this.securityContextRepository = securityContextRepository;
		this.sessionStrategy = sessionStrategy;
	}

	@PostMapping("/login")
	CurrentUserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		Authentication authentication = loginService.login(body.email(), body.password(), request.getRemoteAddr());
		sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		return currentUser((AuthenticatedPrincipal) authentication.getPrincipal());
	}

	@GetMapping("/me")
	CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
		return currentUser(principal);
	}

	@PostMapping("/password/change")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@AuthenticationPrincipal AuthenticatedPrincipal principal,
			@Valid @RequestBody ChangePasswordRequest body) {
		accountService.changePassword(principal.id(), body.currentPassword(), body.newPassword());
	}

	private CurrentUserResponse currentUser(AuthenticatedPrincipal principal) {
		return CurrentUserResponse.from(accountService.findAccount(principal.id()));
	}
}
