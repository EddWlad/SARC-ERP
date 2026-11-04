package com.tidsec.sarc_bakend.modules.uptime.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JustifiedVersionRequest(@NotNull Long version, @NotBlank @Size(max = 500) String justification) {
}
