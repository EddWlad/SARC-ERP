package com.tidsec.sarc_bakend.modules.uptime.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ValidateMeasurementRequest(@NotNull Long version, @Size(max = 500) String justification) {
}
