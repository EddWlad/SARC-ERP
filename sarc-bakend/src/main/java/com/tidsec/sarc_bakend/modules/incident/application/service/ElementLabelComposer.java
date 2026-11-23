package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentLabels;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketConnector;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.catalog.IncidentConnector;

/**
 * Arma la etiqueta del elemento afectado con el nombre <b>actual</b> del equipo (D12, FR-075), no con el de T0, y los
 * conectores por su posicion actual (D06): «Electrolinera X / Conectores 1 y 2». Si estan elegidos todos los conectores
 * activos del equipo, se muestra como equipo completo; la seleccion se conserva en el detalle. El chatbot es
 * «Servicio de chatbot» y un elemento del sitio es «Sitio / Elemento», sin inventar ningun cargador.
 */
final class ElementLabelComposer {

	private ElementLabelComposer() {
	}

	/** @param assets los equipos por identidad, con su nombre y posiciones actuales; puede faltar alguno. */
	static String compose(Ticket ticket, Map<UUID, IncidentAsset> assets) {
		return switch (ticket.getElementClass()) {
			case EQUIPMENT -> equipment(ticket, assets);
			case CHATBOT_SERVICE -> IncidentLabels.CHATBOT_SERVICE;
			case SITE_ELEMENT -> siteElement(ticket, assets);
		};
	}

	private static String equipment(Ticket ticket, Map<UUID, IncidentAsset> assets) {
		IncidentAsset asset = assets.get(ticket.getEquipmentId());
		String name = asset != null ? asset.name() : ticket.getEquipmentLabelAtT0();
		List<TicketConnector> chosen = ticket.getConnectors();
		if (chosen.isEmpty() || coversAllActiveConnectors(chosen, asset)) {
			return name;
		}
		List<Integer> positions = chosen.stream().map(connector -> currentPosition(connector, asset)).sorted().toList();
		return name + " / " + connectorsText(positions);
	}

	/** Todos los conectores activos del equipo estan elegidos: es una falla del equipo completo. */
	private static boolean coversAllActiveConnectors(List<TicketConnector> chosen, IncidentAsset asset) {
		if (asset == null) {
			return false;
		}
		List<UUID> active = asset.connectors().stream().filter(IncidentConnector::active).map(IncidentConnector::id)
				.toList();
		return !active.isEmpty()
				&& chosen.stream().map(TicketConnector::getConnectorId).collect(Collectors.toSet()).containsAll(active);
	}

	private static int currentPosition(TicketConnector connector, IncidentAsset asset) {
		if (asset == null) {
			return connector.getPositionAtT0();
		}
		return asset.connectors().stream().filter(current -> current.id().equals(connector.getConnectorId()))
				.map(IncidentConnector::position).findFirst().orElse(connector.getPositionAtT0());
	}

	static String connectorsText(List<Integer> positions) {
		List<Integer> sorted = positions.stream().sorted(Comparator.naturalOrder()).toList();
		if (sorted.size() == 1) {
			return "Conector " + sorted.get(0);
		}
		String head = sorted.subList(0, sorted.size() - 1).stream().map(String::valueOf)
				.collect(Collectors.joining(", "));
		return "Conectores " + head + " y " + sorted.get(sorted.size() - 1);
	}

	private static String siteElement(Ticket ticket, Map<UUID, IncidentAsset> assets) {
		String site;
		if (ticket.getReferenceEquipmentId() != null) {
			IncidentAsset reference = assets.get(ticket.getReferenceEquipmentId());
			site = reference != null ? reference.name() : ticket.getSiteLabelAtT0();
		}
		else {
			site = ticket.getManualSiteName();
		}
		String element = ticket.getElementName() != null && !ticket.getElementName().isBlank()
				? ticket.getElementName() : IncidentLabels.siteElementKind(ticket.getSiteElementKind());
		return site + " / " + element;
	}
}
