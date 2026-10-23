package com.tidsec.sarc_bakend.modules.identity.api.response;

import com.tidsec.sarc_bakend.modules.identity.application.result.CreatedUser;

/** La clave temporal solo se muestra aqui; el sistema no la vuelve a exponer ni la registra. */
public record CreatedUserResponse(UserResponse user, String temporaryPassword) {

	public static CreatedUserResponse from(CreatedUser created) {
		return new CreatedUserResponse(UserResponse.from(created.user()), created.temporaryPassword());
	}
}
