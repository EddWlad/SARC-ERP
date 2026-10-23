package com.tidsec.sarc_bakend.modules.inventory.api.response;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.externalidentity.ExternalIdentity;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;

public record ExternalIdentityResponse(
		UUID id,
		UUID equipmentId,
		ExternalPlatform platform,
		String scope,
		String externalId,
		@Schema(nullable = true) String alias,
		boolean verified,
		@Schema(nullable = true) Instant verifiedAt,
		long version) {

	public static ExternalIdentityResponse from(ExternalIdentity identity) {
		return new ExternalIdentityResponse(identity.getId(), identity.getEquipmentId(), identity.getPlatform(),
				identity.getScope(), identity.getExternalId(), identity.getAlias(), identity.isVerified(),
				identity.getVerifiedAt(), identity.getVersion());
	}
}
