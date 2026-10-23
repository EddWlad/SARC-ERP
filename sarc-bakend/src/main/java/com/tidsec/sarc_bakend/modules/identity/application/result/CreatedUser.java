package com.tidsec.sarc_bakend.modules.identity.application.result;

import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;

/** Cuenta creada y su contrasena temporal en claro, visible solo en esta respuesta. */
public record CreatedUser(UserAccount user, String temporaryPassword) {
}
