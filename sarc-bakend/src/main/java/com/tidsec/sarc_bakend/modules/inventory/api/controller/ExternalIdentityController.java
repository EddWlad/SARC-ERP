package com.tidsec.sarc_bakend.modules.inventory.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.inventory.api.request.VersionRequest;
import com.tidsec.sarc_bakend.modules.inventory.api.response.ExternalIdentityResponse;
import com.tidsec.sarc_bakend.modules.inventory.application.service.ExternalIdentityService;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ExternalPlatform;

@RestController
@RequestMapping("/api/v1/external-identities")
class ExternalIdentityController {

	private final ExternalIdentityService externalIdentities;

	ExternalIdentityController(ExternalIdentityService externalIdentities) {
		this.externalIdentities = externalIdentities;
	}

	/** Equipo que corresponde a un identificador externo (conciliacion de importaciones). */
	@GetMapping("/resolve")
	ExternalIdentityResponse resolve(@RequestParam ExternalPlatform platform,
			@RequestParam(defaultValue = "") @Size(max = 100) String scope,
			@RequestParam @NotBlank @Size(max = 150) String externalId) {
		return ExternalIdentityResponse.from(externalIdentities.resolve(platform, scope, externalId));
	}

	@PostMapping("/{id}/verify")
	ExternalIdentityResponse verify(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return ExternalIdentityResponse.from(externalIdentities.verify(id, body.version()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@PathVariable UUID id) {
		externalIdentities.remove(id);
	}
}
