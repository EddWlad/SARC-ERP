package com.tidsec.sarc_bakend.modules.inventory.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nuevo tipo de conector; el codigo se normaliza a mayusculas (por ejemplo "ccs 2" a "CCS_2"). */
public record CreateConnectorTypeRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 100) String name) {
}
