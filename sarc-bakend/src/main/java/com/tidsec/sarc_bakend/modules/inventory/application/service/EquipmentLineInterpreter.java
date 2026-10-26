package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.externalidentity.ExternalIdentity;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentLine;
import com.tidsec.sarc_bakend.modules.inventory.domain.policy.EquipmentSheetVocabulary;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

/**
 * Interpreta las celdas de una fila de la plantilla: etiquetas de red, tipo, marca y estado de uso,
 * tipos de conector del catalogo e identidades externas. Reporta todos los problemas de formato de
 * la fila a la vez; las reglas del equipo (PRD 6.2) las aplica el dominio despues. No consulta el
 * inventario.
 */
final class EquipmentLineInterpreter {

	/** Resultado: la propuesta, o null si la fila tiene problemas de formato. */
	record Interpretation(ProposedEquipment proposal, List<RowIssue> issues) {

		boolean isValid() {
			return issues.isEmpty();
		}
	}

	private EquipmentLineInterpreter() {
	}

	/** {@code catalog}: tipos de conector por su codigo (ya normalizado en el catalogo). */
	static Interpretation interpret(EquipmentLine line, Map<String, ConnectorType> catalog) {
		List<RowIssue> issues = new ArrayList<>();
		ChargingNetwork network = required(line.network(), EquipmentSheetVocabulary.NETWORKS, "Red", issues);
		InfrastructureType type = required(line.infrastructure(), EquipmentSheetVocabulary.TYPES, "Tipo", issues);
		EquipmentBrand brand = optional(line.brand(), EquipmentSheetVocabulary.BRANDS, "Marca", issues);
		UsageStatus usage = optional(line.usageStatus(), EquipmentSheetVocabulary.USAGE_STATUSES, "Estado de uso",
				issues);
		if (line.serial() == null) {
			issues.add(new RowIssue("SERIAL_REQUIRED", "La serie es obligatoria: identifica al equipo en el inventario"));
		}
		List<ConnectorType> connectors = connectors(line, catalog, issues);
		List<ProposedEquipment.Identity> identities = identities(line, issues);
		if (!issues.isEmpty()) {
			return new Interpretation(null, List.copyOf(issues));
		}
		return new Interpretation(new ProposedEquipment(network, type, brand, line.name(), line.serial(),
				line.assignedCode(), line.uidPuk(), line.location(), line.mapsUrl(), line.ocppUrl(), usage, connectors,
				identities), List.of());
	}

	private static <E extends Enum<E>> E required(String text, Map<E, String> labels, String column,
			List<RowIssue> issues) {
		if (text == null) {
			issues.add(new RowIssue("VALUE_REQUIRED",
					column + " es obligatorio (" + EquipmentSheetVocabulary.accepted(labels) + ")"));
			return null;
		}
		return optional(text, labels, column, issues);
	}

	private static <E extends Enum<E>> E optional(String text, Map<E, String> labels, String column,
			List<RowIssue> issues) {
		if (text == null) {
			return null;
		}
		Optional<E> value = EquipmentSheetVocabulary.parse(text, labels);
		if (value.isEmpty()) {
			issues.add(new RowIssue("UNKNOWN_VALUE", column + " \"" + text + "\" no es valido; use "
					+ EquipmentSheetVocabulary.accepted(labels)));
		}
		return value.orElse(null);
	}

	/** Conectores en orden de posicion; deben ir seguidos desde el primero. */
	private static List<ConnectorType> connectors(EquipmentLine line, Map<String, ConnectorType> catalog,
			List<RowIssue> issues) {
		List<String> codes = new ArrayList<>(List.of(text(line.connector1()), text(line.connector2()),
				text(line.connector3())));
		while (!codes.isEmpty() && codes.getLast().isEmpty()) {
			codes.removeLast();
		}
		if (codes.contains("")) {
			issues.add(new RowIssue("CONNECTORS_NOT_IN_ORDER",
					"Complete los conectores en orden: Conector 1, luego 2 y 3"));
			return List.of();
		}
		List<ConnectorType> types = new ArrayList<>();
		for (String code : codes) {
			ConnectorType type = lookup(catalog, code);
			if (type == null) {
				issues.add(new RowIssue("CONNECTOR_TYPE_NOT_FOUND",
						"El tipo de conector \"" + code + "\" no existe en el catalogo"));
			}
			else {
				types.add(type);
			}
		}
		return types;
	}

	private static List<ProposedEquipment.Identity> identities(EquipmentLine line, List<RowIssue> issues) {
		List<ProposedEquipment.Identity> identities = new ArrayList<>();
		if (line.enerlinkCharger() != null) {
			identities.add(identity(ExternalPlatform.ENERLINK, line.enerlinkScope(), line.enerlinkCharger()));
		}
		else if (line.enerlinkScope() != null) {
			issues.add(new RowIssue("ENERLINK_CHARGER_REQUIRED",
					"Indique tambien el CARGADOR de Enerlink del activo \"" + line.enerlinkScope() + "\""));
		}
		if (line.wallboxId() != null) {
			identities.add(identity(ExternalPlatform.WALLBOX, null, line.wallboxId()));
		}
		if (line.ocppId() != null) {
			identities.add(identity(ExternalPlatform.OCPP, null, line.ocppId()));
		}
		return identities;
	}

	private static ProposedEquipment.Identity identity(ExternalPlatform platform, String scope, String externalId) {
		return new ProposedEquipment.Identity(platform, scope == null ? "" : scope.strip(),
				ExternalIdentity.normalizeExternalId(externalId));
	}

	/** Busca con el mismo criterio del catalogo ("ccs 2" es CCS_2); un codigo mal formado no existe. */
	private static ConnectorType lookup(Map<String, ConnectorType> catalog, String code) {
		try {
			return catalog.get(ConnectorType.normalizeCode(code));
		}
		catch (BusinessRuleException ex) {
			return null;
		}
	}

	private static String text(String value) {
		return value == null ? "" : value.strip();
	}
}
