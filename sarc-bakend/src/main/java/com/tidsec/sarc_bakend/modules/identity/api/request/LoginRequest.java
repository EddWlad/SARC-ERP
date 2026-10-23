package com.tidsec.sarc_bakend.modules.identity.api.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
