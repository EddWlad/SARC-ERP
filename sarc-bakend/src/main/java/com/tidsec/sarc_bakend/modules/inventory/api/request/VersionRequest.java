package com.tidsec.sarc_bakend.modules.inventory.api.request;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de las acciones que solo necesitan la version leida por el cliente. */
public record VersionRequest(@NotNull Long version) {
}
