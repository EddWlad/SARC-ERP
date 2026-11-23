package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.catalog.IncidentConnector;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;

/**
 * Elemento afectado por un ticket, tal como se guarda en T0 (FR-001, FR-007): la identidad interna y la etiqueta, la
 * red y la ubicacion que tenia en ese momento. Cada clase usa solo sus columnas, como exige el {@code CHECK} de V021:
 * un equipo lleva equipo, etiqueta, red e infraestructura; el chatbot no lleva nada mas que su alcance; un elemento del
 * sitio lleva su clase y una de dos ubicaciones, sin red.
 *
 * <p>Los conectores solo existen en un equipo. Sin conectores, la afectacion es del equipo completo (FR-002).
 */
public record AffectedElement(ElementClass elementClass, UUID equipmentId, String equipmentLabelAtT0,
		String networkAtT0, String infrastructureTypeAtT0, List<ConnectorRef> connectors, ChatbotScope chatbotScope,
		SiteElementKind siteElementKind, String elementName, UUID referenceEquipmentId, String manualSiteName,
		String manualLocation, String manualMapsUrl, String siteLabelAtT0, String locationAtT0, String mapsUrlAtT0) {

	public AffectedElement {
		connectors = connectors == null ? List.of() : List.copyOf(connectors);
	}

	/**
	 * Un equipo del inventario con sus conectores elegidos (vacio es el equipo completo, FR-002), validado: el equipo
	 * debe existir y estar activo, y cada conector debe ser de ese equipo y estar activo (FR-003, FR-006). Guarda lo que
	 * el equipo era en T0: etiqueta, red e infraestructura (FR-007, D12, P-14).
	 */
	public static AffectedElement forEquipment(IncidentAsset asset, Collection<UUID> connectorIds) {
		if (asset == null) {
			throw IncidentErrors.elementInvalid("El equipo no existe");
		}
		if (!asset.active()) {
			throw IncidentErrors.elementInvalid("El equipo no está activo; solo se pueden elegir equipos activos");
		}
		Map<UUID, IncidentConnector> byId = asset.connectors().stream()
				.collect(Collectors.toMap(IncidentConnector::id, Function.identity()));
		List<ConnectorRef> refs = new LinkedHashSet<>(connectorIds == null ? List.<UUID>of() : connectorIds).stream()
				.map(id -> {
					IncidentConnector connector = byId.get(id);
					if (connector == null || !connector.active()) {
						throw IncidentErrors.elementInvalid("El conector no es de este equipo o no está activo");
					}
					return new ConnectorRef(connector.id(), connector.position());
				}).toList();
		return equipment(asset.id(), asset.name(), asset.network().name(), asset.infrastructureType().name(), refs);
	}

	/**
	 * Un elemento del sitio validado (FR-001a): su clase, el nombre (obligatorio para «Otro elemento del sitio»), y
	 * exactamente una de dos ubicaciones: un equipo de referencia activo del mismo sitio, del que se toman solo el
	 * nombre, la ubicacion y el enlace de Maps, o un sitio y una ubicacion escritos a mano con un enlace opcional.
	 * Nunca hereda conectores ni red, y no crea ningun equipo.
	 */
	public static AffectedElement forSiteElement(SiteElementKind kind, String elementName, IncidentAsset reference,
			String manualSiteName, String manualLocation, String manualMapsUrl) {
		if (kind == null) {
			throw IncidentErrors.elementInvalid("Indique la clase del elemento del sitio");
		}
		String name = blankToNull(elementName);
		if (kind == SiteElementKind.OTHER && name == null) {
			throw IncidentErrors.elementInvalid("«Otro elemento del sitio» exige el nombre del elemento");
		}
		if (name != null && name.length() > 120) {
			throw IncidentErrors.elementInvalid("El nombre del elemento admite hasta 120 caracteres");
		}
		String site = blankToNull(manualSiteName);
		String location = blankToNull(manualLocation);
		String maps = blankToNull(manualMapsUrl);
		if (reference != null) {
			if (site != null || location != null || maps != null) {
				throw IncidentErrors.elementInvalid(
						"Indique un equipo de referencia o una ubicación escrita a mano, no ambos");
			}
			if (!reference.active()) {
				throw IncidentErrors.elementInvalid("El equipo de referencia no está activo");
			}
			return siteWithReference(kind, name, reference.id(), reference.name(), reference.location(),
					reference.mapsUrl());
		}
		if (site == null || location == null) {
			throw IncidentErrors.elementInvalid(
					"Elija un equipo de referencia del sitio, o escriba el nombre del sitio y su ubicación");
		}
		if (site.length() > 150 || location.length() > 300) {
			throw IncidentErrors.elementInvalid("El sitio admite hasta 150 caracteres y la ubicación hasta 300");
		}
		if (maps != null) {
			requireHttps(maps);
		}
		return siteWithManualLocation(kind, name, site, location, maps);
	}

	/** El enlace de Maps debe empezar por https:// y caber en la columna (500). */
	public static void requireHttps(String url) {
		if (url.length() > 500 || !url.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
			throw IncidentErrors.mapsUrlInvalid();
		}
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	/** Un equipo del inventario, completo (sin conectores) o con conectores concretos. */
	public static AffectedElement equipment(UUID equipmentId, String label, String network, String infrastructureType,
			List<ConnectorRef> connectors) {
		return new AffectedElement(ElementClass.EQUIPMENT, equipmentId, label, network, infrastructureType, connectors,
				null, null, null, null, null, null, null, null, null, null);
	}

	/** El servicio de chatbot: sin equipo, conector, ubicacion ni enlace de Maps (FR-005). */
	public static AffectedElement chatbot(ChatbotScope scope) {
		return new AffectedElement(ElementClass.CHATBOT_SERVICE, null, null, null, null, List.of(), scope, null, null,
				null, null, null, null, null, null, null);
	}

	/** Un elemento del sitio ubicado con un equipo de referencia activo (FR-001a). */
	public static AffectedElement siteWithReference(SiteElementKind kind, String elementName, UUID referenceEquipmentId,
			String siteLabel, String location, String mapsUrl) {
		return new AffectedElement(ElementClass.SITE_ELEMENT, null, null, null, null, List.of(), null, kind, elementName,
				referenceEquipmentId, null, null, null, siteLabel, location, mapsUrl);
	}

	/** Un elemento del sitio sin equipos en el inventario: sitio y ubicacion escritos a mano, con Maps opcional. */
	public static AffectedElement siteWithManualLocation(SiteElementKind kind, String elementName, String siteName,
			String location, String mapsUrl) {
		return new AffectedElement(ElementClass.SITE_ELEMENT, null, null, null, null, List.of(), null, kind, elementName,
				null, siteName, location, mapsUrl, siteName, location, mapsUrl);
	}
}
