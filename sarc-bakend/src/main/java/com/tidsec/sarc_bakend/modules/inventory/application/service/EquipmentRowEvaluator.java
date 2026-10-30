package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Connector;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.externalidentity.ExternalIdentity;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentLine;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ExternalIdentityRepository;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

/**
 * Evalua una fila de la plantilla contra el inventario actual: la interpreta, valida el equipo con
 * las reglas del alta y la edicion (PRD 6.2), comprueba la unicidad global de sus identificadores e
 * identidades externas (PRD 6.3) y decide si es nueva, igual, modificada o no aplicable. La vista
 * previa y la confirmacion usan la misma evaluacion, asi que al confirmar se detecta si el
 * inventario cambio entre medio.
 */
@Component
class EquipmentRowEvaluator {

	/**
	 * Resultado. {@code proposal} y las identidades solo vienen si la fila escribe en el inventario
	 * (nueva o modificada); {@code existing} es el equipo con esa serie, si lo hay.
	 */
	record Evaluation(EquipmentRowClassification classification, List<RowIssue> issues, Equipment existing,
			ProposedEquipment proposal, List<ProposedEquipment.Identity> identitiesToRegister,
			List<ExternalIdentity> identitiesToVerify) {

		private static Evaluation of(EquipmentRowClassification classification, Equipment existing, RowIssue issue) {
			return new Evaluation(classification, List.of(issue), existing, null, List.of(), List.of());
		}
	}

	private final EquipmentRepository equipment;
	private final ExternalIdentityRepository identities;
	private final EquipmentIdentifiers identifiers;

	EquipmentRowEvaluator(EquipmentRepository equipment, ExternalIdentityRepository identities,
			EquipmentIdentifiers identifiers) {
		this.equipment = equipment;
		this.identities = identities;
		this.identifiers = identifiers;
	}

	/** Identidades Enerlink actuales, para pasarlas a {@link #evaluate} sin consultarlas por fila. */
	EnerlinkIdentityIndex enerlinkIdentities() {
		return EnerlinkIdentityIndex.of(identities.findByPlatform(ExternalPlatform.ENERLINK));
	}

	/** {@code catalog}: tipos de conector por codigo. */
	Evaluation evaluate(EquipmentLine line, Map<String, ConnectorType> catalog, EnerlinkIdentityIndex enerlink) {
		EquipmentLineInterpreter.Interpretation interpretation = EquipmentLineInterpreter.interpret(line, catalog);
		if (!interpretation.isValid()) {
			return new Evaluation(EquipmentRowClassification.INVALID, interpretation.issues(), null, null, List.of(),
					List.of());
		}
		ProposedEquipment proposal = interpretation.proposal();
		Equipment existing = equipment.findBySerials(List.of(Equipment.normalizeIdentifier(proposal.serial())))
				.stream().findFirst().orElse(null);
		if (existing != null && existing.isDeleted()) {
			return Evaluation.of(EquipmentRowClassification.DELETED_EXISTS, existing, new RowIssue(
					"EQUIPMENT_DELETED_EXISTS",
					"La serie es de un equipo eliminado (" + existing.getName() + "); recuperelo desde el inventario"));
		}
		if (existing != null && (existing.getNetwork() != proposal.network() || existing.getType() != proposal.type())) {
			return Evaluation.of(EquipmentRowClassification.CONFLICT, existing, new RowIssue("CLASSIFICATION_IMMUTABLE",
					"La serie es de un equipo de otra red o tipo; la red y el tipo no se cambian, registre otro equipo"));
		}

		Equipment draft;
		try {
			draft = existing == null
					? Equipment.draft(proposal.network(), proposal.type(), proposal.details(), proposal.connectorTypes(),
							null)
					: Equipment.draft(proposal.network(), proposal.type(), proposal.detailsOver(existing),
							proposal.connectorTypesOver(existing), existing);
			proposal.identities().forEach(identity -> ExternalIdentity.register(draft.getId(), identity.platform(),
					identity.scope(), identity.externalId(), null));
		}
		catch (BusinessRuleException ex) {
			return Evaluation.of(EquipmentRowClassification.INVALID, existing, new RowIssue(ex.code(), ex.getMessage()));
		}

		try {
			identifiers.requireUnique(draft, existing == null ? null : existing.getId());
		}
		catch (ConflictException ex) {
			return Evaluation.of(ex.code().equals("EQUIPMENT_DELETED_EXISTS") ? EquipmentRowClassification.DELETED_EXISTS
					: EquipmentRowClassification.CONFLICT, existing, new RowIssue(ex.code(), ex.getMessage()));
		}

		List<ProposedEquipment.Identity> toRegister = new ArrayList<>();
		List<ExternalIdentity> toVerify = new ArrayList<>();
		for (ProposedEquipment.Identity identity : proposal.identities()) {
			List<ExternalIdentity> holders = identity.platform() == ExternalPlatform.ENERLINK ? enerlink.holders(identity)
					: identities.findByKey(identity.platform(), identity.scope(), identity.externalId()).stream().toList();
			if (holders.stream().anyMatch(holder -> existing == null || !holder.getEquipmentId().equals(existing.getId()))) {
				return Evaluation.of(EquipmentRowClassification.CONFLICT, existing, new RowIssue(
						"EXTERNAL_IDENTITY_IN_USE", "La identidad " + identity.describe() + " ya es de otro equipo"));
			}
			if (holders.isEmpty() && identity.platform() == ExternalPlatform.ENERLINK && existing != null
					&& enerlink.hasOtherVerifiedPair(existing.getId(), identity)) {
				return Evaluation.of(EquipmentRowClassification.CONFLICT, existing, new RowIssue(
						"ENERLINK_IDENTITY_DIFFERS", "El equipo ya tiene otra identidad Enerlink verificada; agregue "
								+ identity.describe() + " de forma explicita en Identidades externas"));
			}
			if (holders.isEmpty()) {
				toRegister.add(identity);
			}
			else if (holders.stream().noneMatch(ExternalIdentity::isVerified)) {
				toVerify.add(holders.getFirst());
			}
		}

		if (existing == null) {
			return new Evaluation(EquipmentRowClassification.NEW, List.of(), null, proposal, toRegister, toVerify);
		}
		List<String> equipmentChanges = changes(existing, draft);
		List<String> changes = new ArrayList<>(equipmentChanges);
		toRegister.forEach(identity -> changes.add("nueva identidad " + identity.describe()));
		toVerify.forEach(identity -> changes.add("verifica identidad " + new ProposedEquipment.Identity(
				identity.getPlatform(), identity.getScope(), identity.getExternalId()).describe()));
		if (changes.isEmpty()) {
			return new Evaluation(EquipmentRowClassification.IDENTICAL, List.of(), existing, null, List.of(),
					List.of());
		}
		if (equipmentChanges.isEmpty() && onlyEnerlink(toRegister, toVerify)) {
			return new Evaluation(EquipmentRowClassification.IDENTITY_COMPLETED, List.of(new RowIssue(
					"IDENTITY_COMPLETED", "Completa: " + String.join(", ", changes) + ". Se aplica al confirmar")),
					existing, proposal, toRegister, toVerify);
		}
		return new Evaluation(EquipmentRowClassification.MODIFIED, List.of(new RowIssue("EQUIPMENT_CHANGED",
				"Cambia: " + String.join(", ", changes) + ". Apruebe para actualizar el equipo")), existing, proposal,
				toRegister, toVerify);
	}

	/** Solo Enerlink se completa sin aprobacion por fila; las demas plataformas no cambian (FR-019). */
	private static boolean onlyEnerlink(List<ProposedEquipment.Identity> toRegister, List<ExternalIdentity> toVerify) {
		return toRegister.stream().allMatch(identity -> identity.platform() == ExternalPlatform.ENERLINK)
				&& toVerify.stream().allMatch(identity -> identity.getPlatform() == ExternalPlatform.ENERLINK);
	}

	/** Campos del equipo que cambiarian, con su nombre para el analista. */
	private static List<String> changes(Equipment current, Equipment draft) {
		List<String> changes = new ArrayList<>();
		compare(changes, "marca", current, draft, Equipment::getBrand);
		compare(changes, "nombre", current, draft, Equipment::getName);
		compare(changes, "codigo asignado", current, draft, Equipment::getAssignedCode);
		compare(changes, "UID/PUK", current, draft, Equipment::getUidPuk);
		compare(changes, "ubicacion", current, draft, Equipment::getLocation);
		compare(changes, "enlace de mapas", current, draft, Equipment::getMapsUrl);
		compare(changes, "URL OCPP", current, draft, Equipment::getOcppUrl);
		compare(changes, "estado de uso", current, draft, Equipment::getUsageStatus);
		compare(changes, "conectores", current, draft, EquipmentRowEvaluator::connectorTypeIds);
		return changes;
	}

	private static void compare(List<String> changes, String label, Equipment current, Equipment draft,
			Function<Equipment, ?> field) {
		if (!Objects.equals(field.apply(current), field.apply(draft))) {
			changes.add(label);
		}
	}

	private static List<UUID> connectorTypeIds(Equipment equipment) {
		return equipment.activeConnectors().stream().map(Connector::getConnectorType).map(ConnectorType::getId)
				.toList();
	}
}
