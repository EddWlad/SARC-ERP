package com.tidsec.sarc_bakend.modules.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.security.IncidentStaffDirectory;
import com.tidsec.sarc_bakend.shared.security.StaffMember;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/** Puerto de personal para Incidencias: solo editores activos, y nunca correo ni roles (feature 010, research R8). */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class IncidentStaffDirectoryServiceTest {

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	IncidentStaffDirectory directory;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private UserAccount admin;
	private UserAccount analyst;
	private UserAccount secondAnalyst;
	private UserAccount manager;
	private UserAccount supervisor;
	private UserAccount assistant;
	private UserAccount inactiveAnalyst;
	private UserAccount analystAndManager;

	@BeforeEach
	void seed() {
		jdbc.execute("TRUNCATE auth_user, spring_session, audit_event CASCADE");
		admin = user("admin@qa010.example.test", "Zoila Administradora", Role.ADMINISTRADOR);
		analyst = user("analista@qa010.example.test", "Ana Analista", Role.ANALISTA);
		secondAnalyst = user("analista-2@qa010.example.test", "Bruno Analista", Role.ANALISTA);
		manager = user("gerente@qa010.example.test", "Gloria Gerente", Role.GERENTE);
		supervisor = user("supervisor@qa010.example.test", "Sara Supervisora", Role.SUPERVISOR);
		assistant = user("asistente@qa010.example.test", "Asis Asistente", Role.ASISTENTE);
		inactiveAnalyst = user("inactivo@qa010.example.test", "Ines Inactiva", Role.ANALISTA);
		inactiveAnalyst.deactivate();
		users.save(inactiveAnalyst);
		analystAndManager = user("mixto@qa010.example.test", "Mario Mixto", Role.ANALISTA, Role.GERENTE);
	}

	@Test
	void listsOnlyActiveAdministratorsAndAnalystsSortedByName() {
		assertThat(directory.activeEditors()).extracting(StaffMember::fullName).containsExactly("Ana Analista",
				"Bruno Analista", "Mario Mixto", "Zoila Administradora");
	}

	@Test
	void managersSupervisorsAssistantsAndInactiveUsersAreNotEditors() {
		for (UserAccount account : List.of(manager, supervisor, assistant, inactiveAnalyst)) {
			assertThat(directory.findActiveEditor(account.getId())).as(account.getFullName()).isEmpty();
		}
		assertThat(directory.findActiveEditor(UUID.randomUUID())).isEmpty();
	}

	@Test
	void findsAnActiveEditorByIdentity() {
		assertThat(directory.findActiveEditor(analyst.getId())).contains(new StaffMember(analyst.getId(), "Ana Analista"));
		assertThat(directory.findActiveEditor(admin.getId())).isPresent();
		assertThat(directory.findActiveEditor(analystAndManager.getId())).isPresent();
	}

	@Test
	void namesOfIncludesInactiveUsersSoOldAuthorsStillShowAName() {
		Map<UUID, String> names = directory.namesOf(Set.of(analyst.getId(), inactiveAnalyst.getId(), manager.getId(),
				UUID.randomUUID()));
		assertThat(names).containsOnly(Map.entry(analyst.getId(), "Ana Analista"),
				Map.entry(inactiveAnalyst.getId(), "Ines Inactiva"), Map.entry(manager.getId(), "Gloria Gerente"));
		assertThat(directory.namesOf(Set.of())).isEmpty();
	}

	@Test
	void thePublicRecordCarriesOnlyIdentityAndName() {
		assertThat(StaffMember.class.getRecordComponents()).extracting(component -> component.getName())
				.containsExactly("id", "fullName");
	}

	private UserAccount user(String email, String name, Role... roles) {
		return users.save(UserAccount.create(new Email(email), name, Set.of(roles), "hash"));
	}
}
