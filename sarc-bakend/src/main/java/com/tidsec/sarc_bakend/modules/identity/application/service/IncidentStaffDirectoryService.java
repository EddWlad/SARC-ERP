package com.tidsec.sarc_bakend.modules.identity.application.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserStatus;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserSearchCriteria;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.security.IncidentStaffDirectory;
import com.tidsec.sarc_bakend.shared.security.StaffMember;

/**
 * Implementacion del puerto {@link IncidentStaffDirectory} con las cuentas de Identidad (feature 010). Solo
 * lectura y sin autorizacion propia: la exponen los casos de uso de Incidencias, que ya exigen un editor. Solo
 * entrega identidad y nombre, nunca correo, bloqueo ni roles.
 */
@Service
@Transactional(readOnly = true)
class IncidentStaffDirectoryService implements IncidentStaffDirectory {

	private static final Set<Role> EDITOR_ROLES = Set.of(Role.ADMINISTRADOR, Role.ANALISTA);
	private static final Comparator<StaffMember> BY_NAME = Comparator
			.comparing((StaffMember member) -> member.fullName().toLowerCase(java.util.Locale.ROOT))
			.thenComparing(StaffMember::id);

	private final UserRepository users;

	IncidentStaffDirectoryService(UserRepository users) {
		this.users = users;
	}

	@Override
	public List<StaffMember> activeEditors() {
		return EDITOR_ROLES.stream().flatMap(role -> allActive(role).stream())
				.collect(Collectors.toMap(StaffMember::id, Function.identity(), (first, second) -> first)).values()
				.stream().sorted(BY_NAME).toList();
	}

	@Override
	public Optional<StaffMember> findActiveEditor(UUID id) {
		return users.findById(id).filter(IncidentStaffDirectoryService::isActiveEditor)
				.map(IncidentStaffDirectoryService::member);
	}

	@Override
	public Map<UUID, String> namesOf(Collection<UUID> ids) {
		return users.findAllById(Set.copyOf(ids)).stream()
				.collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName));
	}

	private List<StaffMember> allActive(Role role) {
		List<StaffMember> found = new java.util.ArrayList<>();
		int page = 0;
		PageResult<UserAccount> result;
		do {
			result = users.search(new UserSearchCriteria(null, role, UserStatus.ACTIVE),
					new PageQuery(page, PageQuery.MAX_SIZE));
			result.content().stream().map(IncidentStaffDirectoryService::member).forEach(found::add);
			page++;
		}
		while (page < result.totalPages());
		return found;
	}

	private static boolean isActiveEditor(UserAccount user) {
		return user.getStatus() == UserStatus.ACTIVE && user.getRoles().stream().anyMatch(EDITOR_ROLES::contains);
	}

	private static StaffMember member(UserAccount user) {
		return new StaffMember(user.getId(), user.getFullName());
	}
}
