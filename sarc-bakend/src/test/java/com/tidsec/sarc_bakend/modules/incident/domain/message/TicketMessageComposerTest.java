package com.tidsec.sarc_bakend.modules.incident.domain.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.message.TicketMessageComposer.Action;
import com.tidsec.sarc_bakend.modules.incident.domain.message.TicketMessageComposer.Input;
import com.tidsec.sarc_bakend.modules.incident.domain.message.TicketMessageComposer.Message;
import com.tidsec.sarc_bakend.modules.incident.domain.message.TicketMessageComposer.Options;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

/** Contrato del mensaje para WhatsApp (contracts/whatsapp-message.md). Las horas se leen en America/Guayaquil, UTC-5. */
class TicketMessageComposerTest {

	/** 2026-11-21 10:30 en Ecuador. */
	private static final Instant T0 = Instant.parse("2026-11-21T15:30:00Z");

	private static Input equipment(String location, String mapsUrl, List<Action> actions) {
		return new Input("TICKET-9001", ElementClass.EQUIPMENT, "KIA Norte / Conectores 1 y 2", null, location, mapsUrl,
				"Sin comunicación con el equipo", TicketState.OPEN, T0, actions);
	}

	private static Message base(Input input) {
		return TicketMessageComposer.compose(input, Options.none());
	}

	@Test
	void theBaseTemplateOfAnEquipmentWithConnectorsIsExact() {
		Message message = base(equipment("Vía Prueba 2", "https://maps.example.test/kia", List.of()));

		assertThat(message.text()).isEqualTo("""
				*TICKET-9001*
				Cargador/Electrolinera: KIA Norte / Conectores 1 y 2
				Ubicación: Vía Prueba 2
				Google Maps: https://maps.example.test/kia
				Novedad: Sin comunicación con el equipo
				Estado: Abierto""");
		assertThat(message.missingFields()).isEmpty();
	}

	@Test
	void theChatbotHasNoLocationNorMapsAndShowsItsScopeWhenKnown() {
		Input partial = new Input("TICKET-9002", ElementClass.CHATBOT_SERVICE, "Servicio de chatbot",
				ChatbotScope.PARTIAL, "no debe salir", "https://no.debe.salir", "No responde", TicketState.OPEN, T0,
				List.of());
		Input unknown = new Input("TICKET-9002", ElementClass.CHATBOT_SERVICE, "Servicio de chatbot", null, null, null,
				"No responde", TicketState.OPEN, T0, List.of());

		assertThat(base(partial).text()).isEqualTo("""
				*TICKET-9002*
				Elemento: Servicio de chatbot (alcance parcial)
				Novedad: No responde
				Estado: Abierto""");
		assertThat(base(partial).missingFields()).isEmpty();
		assertThat(base(unknown).text()).contains("Elemento: Servicio de chatbot\n").doesNotContain("alcance")
				.doesNotContain("Ubicación").doesNotContain("Google Maps");
		assertThat(base(partial).text()).doesNotContain("no debe salir").doesNotContain("no.debe.salir");
	}

	@Test
	void aSiteElementKeepsTheLocationAndMapsFromT0() {
		Input barrier = new Input("TICKET-9003", ElementClass.SITE_ELEMENT, "CC Prueba Norte / Barrera vehicular", null,
				"CC Prueba Norte", "https://maps.example.test/cc", "La barrera no sube", TicketState.CLOSED, T0,
				List.of());

		assertThat(base(barrier).text()).isEqualTo("""
				*TICKET-9003*
				Elemento: CC Prueba Norte / Barrera vehicular
				Ubicación: CC Prueba Norte
				Google Maps: https://maps.example.test/cc
				Novedad: La barrera no sube
				Estado: Cerrado""");
	}

	@Test
	void withoutMapsTheLineKeepsItsCaptionEmptyAndTheFieldIsReportedWithoutBlockingTheCopy() {
		Message message = base(equipment("Vía Prueba 2", null, List.of()));

		assertThat(message.text().split("\n")).contains("Google Maps: ");
		assertThat(message.text()).contains("Ubicación: Vía Prueba 2\n");
		assertThat(message.missingFields()).containsExactly("mapsUrl");
		assertThat(base(equipment("Vía Prueba 2", "  ", List.of())).missingFields()).containsExactly("mapsUrl");
	}

	@Test
	void withoutLocationTheLineIsEmptyAndReported() {
		Message message = base(equipment(null, "https://maps.example.test/kia", List.of()));

		assertThat(message.text().split("\n")).contains("Ubicación: ");
		assertThat(message.missingFields()).containsExactly("location");
		assertThat(base(equipment(null, null, List.of())).missingFields()).containsExactly("location", "mapsUrl");
	}

	@Test
	void theDetectionOptionAddsT0BeforeTheNewsInEcuadorTime() {
		Message message = TicketMessageComposer.compose(equipment("Vía", "https://m.example.test/x", List.of()),
				new Options(true, false));

		assertThat(message.text().split("\n")).containsSubsequence("Google Maps: https://m.example.test/x",
				"Detección: 21/11/2026 10:30", "Novedad: Sin comunicación con el equipo");
	}

	@Test
	void theActionsOptionListsTheActionsByTimeAndAddsTheDateWhenItIsAnotherDay() {
		List<Action> actions = List.of(
				new Action(Instant.parse("2026-11-22T14:05:00Z"), "Se escaló a técnicos"),
				new Action(Instant.parse("2026-11-21T16:10:00Z"), "Se reinició el equipo\n desde la plataforma"));

		Message message = TicketMessageComposer.compose(equipment("Vía", "https://m.example.test/x", actions),
				new Options(false, true));

		assertThat(message.text().split("\n")).containsSubsequence("Novedad: Sin comunicación con el equipo",
				"Acciones:", "- 11:10 Se reinició el equipo desde la plataforma",
				"- 22/11/2026 09:05 Se escaló a técnicos", "Estado: Abierto");
	}

	@Test
	void withoutActionsTheOptionSaysSoInsteadOfInventingOne() {
		Message message = TicketMessageComposer.compose(equipment("Vía", "https://m.example.test/x", List.of()),
				new Options(false, true));

		assertThat(message.text()).contains("Acciones: sin acciones registradas\nEstado: Abierto");
	}

	@Test
	void bothOptionsKeepTheTemplateOrder() {
		Message message = TicketMessageComposer.compose(
				equipment("Vía", "https://m.example.test/x", List.of(new Action(T0.plusSeconds(600), "Llamada al sitio"))),
				new Options(true, true));

		assertThat(message.text()).isEqualTo("""
				*TICKET-9001*
				Cargador/Electrolinera: KIA Norte / Conectores 1 y 2
				Ubicación: Vía
				Google Maps: https://m.example.test/x
				Detección: 21/11/2026 10:30
				Novedad: Sin comunicación con el equipo
				Acciones:
				- 10:40 Llamada al sitio
				Estado: Abierto""");
	}

	@Test
	void theTextNeverCarriesTrailingSpacesExceptTheEmptyCaptionAndNeverSensitiveFields() {
		Message message = TicketMessageComposer.compose(
				new Input("TICKET-9001", ElementClass.EQUIPMENT, "KIA Norte", null, " Vía ", null, "  Falla  \n  grave  ",
						TicketState.OPEN, T0, List.of(new Action(T0, "Acción  "))),
				new Options(true, true));

		for (String line : message.text().split("\n")) {
			if (!line.endsWith(": ")) {
				assertThat(line).isEqualTo(line.stripTrailing());
			}
		}
		assertThat(message.text()).contains("Novedad: Falla grave");
		assertThat(message.text().toLowerCase()).doesNotContain("uid").doesNotContain("puk").doesNotContain("ocpp")
				.doesNotContain("serie").doesNotContain("responsable");
	}
}
