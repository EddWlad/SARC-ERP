package com.tidsec.sarc_bakend.modules.uptime.api.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Maximo 500 por peticion, tamano tecnico del bloque de ADR 0005. */
public record BatchValidationRequest(@NotEmpty @Size(max = 500) List<@Valid Item> items) {

	public record Item(@NotNull UUID id, @NotNull Long version) {
	}
}
