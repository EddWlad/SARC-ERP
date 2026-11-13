package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Version leida de la marca y justificacion obligatoria para retirarla (FR-044). */
public record WithdrawReviewedDayRequest(@NotNull Long version, @Size(max = 1000) String justification) {
}
