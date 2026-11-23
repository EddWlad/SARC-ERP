package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.shared.security.IncidentStaffDirectory;
import com.tidsec.sarc_bakend.shared.security.StaffMember;

/** Usuarios elegibles como responsable de un ticket: los editores activos. No expone correo ni roles. */
@Service
@PreAuthorize(IncidentPermissions.EDITORS)
@Transactional(readOnly = true)
public class IncidentResponsibleQueryService {

	private final IncidentStaffDirectory staff;

	IncidentResponsibleQueryService(IncidentStaffDirectory staff) {
		this.staff = staff;
	}

	public List<StaffMember> eligible() {
		return staff.activeEditors();
	}
}
