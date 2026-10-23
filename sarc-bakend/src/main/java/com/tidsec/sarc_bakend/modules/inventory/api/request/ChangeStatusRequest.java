package com.tidsec.sarc_bakend.modules.inventory.api.request;

import jakarta.validation.constraints.NotNull;

import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/** Nuevo estado administrativo. DELETED es baja logica; volver a ACTIVE o INACTIVE la recupera. */
public record ChangeStatusRequest(@NotNull Long version, @NotNull AdministrativeStatus status) {
}
