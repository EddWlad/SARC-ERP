package com.tidsec.sarc_bakend.modules.incident.domain.message;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

/**
 * Compone el texto del mensaje para WhatsApp de un ticket (FR-055 a FR-060, contracts/whatsapp-message.md). Es una
 * funcion pura: el resultado solo depende de lo que recibe, y solo recibe los datos que el mensaje puede llevar, de modo
 * que nunca incluye UID, PUK, URL OCPP, el responsable ni datos de clientes. SARC no envia nada: el analista copia el
 * texto y lo publica a mano.
 *
 * <p>Plantilla base: el codigo en negrita de WhatsApp, el elemento, la ubicacion, el enlace de Maps, la novedad y el
 * estado. Sin ubicacion o sin enlace, la linea queda con su rotulo y el valor vacio, y el campo se informa en
 * {@code missingFields}: copiar no se bloquea. El chatbot no tiene ubicacion y esas dos lineas se omiten.
 */
public final class TicketMessageComposer {

	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	/** Campo que falta en el mensaje: la interfaz lo avisa sin bloquear la copia. */
	public static final String MISSING_LOCATION = "location";
	public static final String MISSING_MAPS_URL = "mapsUrl";

	private TicketMessageComposer() {
	}

	/** Una accion realizada: un evento no anulado de accion correctiva o escalamiento del episodio actual. */
	public record Action(Instant occurredAt, String text) {
	}

	/**
	 * @param elementLabel el elemento ya armado con su nombre actual: el equipo con sus conectores, el servicio de
	 *        chatbot o «Sitio / Elemento»
	 * @param location la ubicacion actual del equipo, o la conservada en T0 de un elemento del sitio
	 * @param mapsUrl el enlace de Maps, de la misma fuente
	 * @param detectionAt T0 del episodio actual
	 * @param actions las acciones del episodio actual, no anuladas
	 */
	public record Input(String code, ElementClass elementClass, String elementLabel, ChatbotScope chatbotScope,
			String location, String mapsUrl, String description, TicketState state, Instant detectionAt,
			List<Action> actions) {

		public Input {
			actions = actions == null ? List.of() : List.copyOf(actions);
		}
	}

	/** Opciones adicionales, desactivadas cada vez que se abre el mensaje (FR-056a). */
	public record Options(boolean includeDetection, boolean includeActions) {

		public static Options none() {
			return new Options(false, false);
		}
	}

	public record Message(String text, List<String> missingFields) {

		public Message {
			missingFields = List.copyOf(missingFields);
		}
	}

	public static Message compose(Input input, Options options) {
		List<String> lines = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		lines.add("*" + input.code() + "*");
		lines.add(elementCaption(input.elementClass()) + ": " + element(input));
		if (input.elementClass() != ElementClass.CHATBOT_SERVICE) {
			lines.add("Ubicación: " + blank(input.location()));
			lines.add("Google Maps: " + blank(input.mapsUrl()));
			if (isBlank(input.location())) {
				missing.add(MISSING_LOCATION);
			}
			if (isBlank(input.mapsUrl())) {
				missing.add(MISSING_MAPS_URL);
			}
		}
		if (options.includeDetection() && input.detectionAt() != null) {
			lines.add("Detección: " + DATE_TIME.format(input.detectionAt().atZone(ZONE)));
		}
		lines.add("Novedad: " + singleSpaced(input.description()));
		if (options.includeActions()) {
			lines.addAll(actions(input));
		}
		lines.add("Estado: " + (input.state() == TicketState.CLOSED ? "Cerrado" : "Abierto"));
		return new Message(String.join("\n", lines.stream().map(TicketMessageComposer::stripEnd).toList()), missing);
	}

	private static String elementCaption(ElementClass elementClass) {
		return elementClass == ElementClass.EQUIPMENT ? "Cargador/Electrolinera" : "Elemento";
	}

	private static String element(Input input) {
		if (input.elementClass() == ElementClass.CHATBOT_SERVICE && input.chatbotScope() != null) {
			return input.elementLabel() + " (alcance "
					+ (input.chatbotScope() == ChatbotScope.TOTAL ? "total" : "parcial") + ")";
		}
		return input.elementLabel();
	}

	/** Las acciones por hora del hecho; si no hay ninguna, lo dice en vez de inventar una. */
	private static List<String> actions(Input input) {
		if (input.actions().isEmpty()) {
			return List.of("Acciones: sin acciones registradas");
		}
		java.time.LocalDate detectionDay = input.detectionAt() == null ? null
				: input.detectionAt().atZone(ZONE).toLocalDate();
		List<String> lines = new ArrayList<>();
		lines.add("Acciones:");
		input.actions().stream().sorted(Comparator.comparing(Action::occurredAt)).forEach(action -> {
			java.time.ZonedDateTime when = action.occurredAt().atZone(ZONE);
			boolean otherDay = detectionDay != null && !when.toLocalDate().equals(detectionDay);
			lines.add("- " + (otherDay ? DATE_TIME : TIME).format(when) + " " + singleSpaced(action.text()));
		});
		return lines;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	private static String blank(String text) {
		return text == null ? "" : text.strip();
	}

	/** Un texto de varias lineas ocuparia varias lineas del mensaje: se une en una sola. */
	private static String singleSpaced(String text) {
		return text == null ? "" : text.strip().replaceAll("\\s+", " ");
	}

	/** Sin espacios al final, salvo el rotulo vacio «Google Maps: », que el analista completa pegando el enlace. */
	private static String stripEnd(String line) {
		return line.endsWith(": ") ? line : line.stripTrailing();
	}
}
