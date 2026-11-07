package com.tidsec.sarc_bakend.modules.uptime.api.request;
import java.time.*;
import jakarta.validation.constraints.*;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
public record TemporalClassificationRequest(@NotNull @PositiveOrZero Long version,@NotNull TemporalPrecision temporalPrecision,
        LocalDate referenceWeek,OffsetDateTime periodStart,OffsetDateTime periodEnd,@NotBlank String justification,
        @NotBlank String evidence) {}
