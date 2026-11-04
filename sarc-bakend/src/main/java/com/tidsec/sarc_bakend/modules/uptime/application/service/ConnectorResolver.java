package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.ConnectorAlias;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.UptimeImportRepository;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorDirectory;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorQuery;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;
import com.tidsec.sarc_bakend.shared.catalog.IdentityMatch;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;
import com.tidsec.sarc_bakend.shared.importing.reading.HeaderLayout;

/**
 * Atribuye una fila solo con dato inequivoco (FR-028): equipo por identidad verificada (activo y
 * cargador o, si la fuente no trae activo, un cargador que use un solo equipo); conector por
 * alias aprobado o por numero inicial, con o sin la palabra "Conector" como lo escribe Enerlink, que
 * coincide con una posicion activa sin que el tipo la contradiga. Lo demas queda pendiente de vinculacion. Nunca se atribuye por aproximacion.
 */
@Component
class ConnectorResolver {

	private static final Pattern INITIAL_NUMBER = Pattern.compile("^(?:conector\\s*)?(\\d+)\\b(.*)$",
			Pattern.CASE_INSENSITIVE);

	/** Lo que la fuente dice de un conector. */
	record Request(String site, String charger, String connector) {
	}

	/**
	 * {@code connector} solo llega resuelto; si no, {@code issue} explica por que y {@code suggested}
	 * puede proponer el unico conector activo cuyo tipo coincide con la etiqueta, para que la persona
	 * lo confirme.
	 */
	record Resolution(EquipmentReference equipment, MeasurableConnector connector, RowIssue issue,
			MeasurableConnector suggested) {

		Resolution(EquipmentReference equipment, MeasurableConnector connector, RowIssue issue) {
			this(equipment, connector, issue, null);
		}
	}

	private final EquipmentDirectory equipment;
	private final ConnectorDirectory connectors;
	private final UptimeImportRepository imports;

	ConnectorResolver(EquipmentDirectory equipment, ConnectorDirectory connectors, UptimeImportRepository imports) {
		this.equipment = equipment;
		this.connectors = connectors;
		this.imports = imports;
	}

	Map<Request, Resolution> resolve(MeasurementSource source, Collection<Request> requests) {
		List<ExternalKey> keys = requests.stream().map(ConnectorResolver::key).filter(ExternalKey::isComplete).distinct()
				.toList();
		Map<ExternalKey, IdentityMatch> matches = new HashMap<>(equipment.matchIdentities(ExternalPlatform.ENERLINK, keys));
		// El informe semanal no trae Activo: el cargador basta si un solo equipo verificado lo usa.
		List<String> chargersOnly = requests.stream().map(ConnectorResolver::key)
				.filter(key -> key.scope().isEmpty() && !key.externalId().isEmpty()).map(ExternalKey::externalId)
				.distinct().toList();
		equipment.matchExternalIds(ExternalPlatform.ENERLINK, chargersOnly)
				.forEach((charger, match) -> matches.put(new ExternalKey("", charger), match));
		List<UUID> equipmentIds = matches.values().stream().filter(match -> match.equipment() != null)
				.map(match -> match.equipment().id()).distinct().toList();
		Map<String, ConnectorAlias> aliases = imports.findAliases(source, equipmentIds).stream()
				.filter(ConnectorAlias::isActive)
				.collect(Collectors.toMap(alias -> aliasKey(alias.getEquipmentId(), alias.getLabelKey()),
						Function.identity()));
		Map<UUID, List<MeasurableConnector>> byEquipment = new HashMap<>();
		Map<Request, Resolution> resolved = new HashMap<>();
		for (Request request : requests) {
			IdentityMatch match = matches.get(key(request));
			if (match == null || match.equipment() == null) {
				resolved.put(request, new Resolution(null, null, identityIssue(match)));
				continue;
			}
			EquipmentReference reference = match.equipment();
			List<MeasurableConnector> candidates = byEquipment.computeIfAbsent(reference.id(),
					id -> connectors.search(new ConnectorQuery(null, null, null, null, id)));
			resolved.put(request, connector(reference, request.connector(), candidates, aliases));
		}
		return resolved;
	}

	private static Resolution connector(EquipmentReference reference, String label,
			List<MeasurableConnector> candidates, Map<String, ConnectorAlias> aliases) {
		ConnectorAlias alias = aliases.get(aliasKey(reference.id(), ConnectorAlias.keyOf(label)));
		if (alias != null) {
			return candidates.stream()
					.filter(item -> item.connectorId().equals(alias.getConnectorId()) && item.connectorActive()).findFirst()
					.map(item -> new Resolution(reference, item, null))
					.orElse(pending(reference, "UPTIME_ALIAS_INACTIVE", "El alias apunta a un conector inactivo."));
		}
		Matcher initial = INITIAL_NUMBER.matcher(label.strip());
		boolean numbered = initial.matches();
		if (numbered) {
			int position;
			try {
				position = Integer.parseInt(initial.group(1));
			}
			catch (NumberFormatException outOfRange) {
				return pending(reference, "UPTIME_CONNECTOR_UNRESOLVED",
						"El número de la etiqueta no corresponde a una posición válida.");
			}
			List<MeasurableConnector> found = candidates.stream()
					.filter(item -> item.ordinal() == position && item.connectorActive())
					.filter(item -> typeAgrees(initial.group(2), item)).toList();
			if (found.size() == 1) {
				return new Resolution(reference, found.getFirst(), null);
			}
		}
		return new Resolution(reference, null,
				new RowIssue("UPTIME_CONNECTOR_UNRESOLVED", "La etiqueta del conector no se resuelve sin duda."),
				byType(numbered ? initial.group(2) : label, candidates));
	}

	/** Solo propone si la etiqueta nombra un tipo y un unico conector activo del equipo lo tiene. */
	private static MeasurableConnector byType(String text, List<MeasurableConnector> candidates) {
		if (compact(text).isEmpty()) {
			return null;
		}
		List<MeasurableConnector> found = candidates.stream().filter(MeasurableConnector::connectorActive)
				.filter(item -> typeAgrees(text, item)).toList();
		return found.size() == 1 ? found.getFirst() : null;
	}

	/** Sin texto no hay contradiccion; con texto debe coincidir con el tipo registrado. */
	private static boolean typeAgrees(String text, MeasurableConnector connector) {
		String rest = compact(text);
		String code = compact(connector.connectorTypeCode());
		String name = compact(connector.connectorTypeName());
		return rest.isEmpty() || rest.contains(code) || code.contains(rest) || rest.contains(name) || name.contains(rest);
	}

	private static String compact(String text) {
		return HeaderLayout.normalize(text).replaceAll("[^A-Z0-9]", "");
	}

	private static Resolution pending(EquipmentReference reference, String code, String message) {
		return new Resolution(reference, null, new RowIssue(code, message));
	}

	private static RowIssue identityIssue(IdentityMatch match) {
		if (match == null) {
			return new RowIssue("UPTIME_IDENTITY_INCOMPLETE", "Faltan activo o cargador.");
		}
		return switch (match.status()) {
			case AMBIGUOUS -> new RowIssue("UPTIME_IDENTITY_AMBIGUOUS", "La identidad corresponde a varios equipos.");
			case NOT_VERIFIED -> new RowIssue("UPTIME_IDENTITY_NOT_VERIFIED", "La identidad no está verificada.");
			default -> new RowIssue("UPTIME_IDENTITY_UNKNOWN", "La identidad no está registrada en el inventario.");
		};
	}

	private static ExternalKey key(Request request) {
		return new ExternalKey(request.site(), request.charger());
	}

	private static String aliasKey(UUID equipmentId, String labelKey) {
		return equipmentId + "|" + labelKey;
	}
}
