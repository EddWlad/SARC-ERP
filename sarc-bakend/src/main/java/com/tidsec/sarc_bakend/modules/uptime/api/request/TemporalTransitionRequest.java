package com.tidsec.sarc_bakend.modules.uptime.api.request;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalTransitionWork;
public record TemporalTransitionRequest(@NotEmpty @Size(max=500) List<@NotNull @Valid Item> items) {
    public record Item(@NotNull UUID id,@NotNull @PositiveOrZero Long version) {}
    public List<TemporalTransitionWork.Item> toItems() {
        return items.stream().map(i->new TemporalTransitionWork.Item(i.id(),i.version())).toList();
    }
}
