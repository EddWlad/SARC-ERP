package com.tidsec.sarc_bakend.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto publico de Identidad para Incidencias (feature 010). Un Analista elige responsable y relevo, y
 * {@code /api/v1/users} es solo del Administrador. Nunca expone correo, contrasena, bloqueo ni roles.
 */
public interface IncidentStaffDirectory {

	/** Usuarios activos con rol Administrador o Analista, ordenados por nombre. */
	List<StaffMember> activeEditors();

	/** El mismo filtro para un usuario; vacio si no existe, esta inactivo o no tiene rol de edicion. */
	Optional<StaffMember> findActiveEditor(UUID id);

	/** Nombres de cualquier usuario, tambien inactivos, para mostrar autores y responsables anteriores. */
	Map<UUID, String> namesOf(Collection<UUID> ids);
}
