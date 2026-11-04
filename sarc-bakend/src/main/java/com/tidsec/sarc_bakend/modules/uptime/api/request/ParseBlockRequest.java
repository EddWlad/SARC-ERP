package com.tidsec.sarc_bakend.modules.uptime.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParseBlockRequest(@NotBlank @Size(max = 4000) String text) {
}
