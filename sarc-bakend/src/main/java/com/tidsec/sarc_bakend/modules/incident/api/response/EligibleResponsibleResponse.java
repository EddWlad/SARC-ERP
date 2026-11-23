package com.tidsec.sarc_bakend.modules.incident.api.response;

import java.util.UUID;

import com.tidsec.sarc_bakend.shared.security.StaffMember;

/** Un usuario elegible como responsable: solo identidad y nombre, nunca correo ni roles. */
public record EligibleResponsibleResponse(UUID id, String name) {

	public static EligibleResponsibleResponse from(StaffMember member) {
		return new EligibleResponsibleResponse(member.id(), member.fullName());
	}
}
