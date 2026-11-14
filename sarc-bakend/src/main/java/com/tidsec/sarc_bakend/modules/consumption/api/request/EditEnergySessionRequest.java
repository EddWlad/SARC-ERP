package com.tidsec.sarc_bakend.modules.consumption.api.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.application.command.EditSessionCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Edicion de una sesion. Campo ausente: se conserva. {@code endedAt}, {@code energyKwh} o
 * {@code equipmentId} en {@code null}: no informado o sin vincular. Las fechas llevan su desplazamiento.
 */
@JsonDeserialize(using = EditEnergySessionRequest.Reader.class)
public record EditEnergySessionRequest(
		@NotNull(message = "Indique la versión revisada") Long version,
		@NotBlank(message = "Ingrese el motivo de la edición")
		@Size(max = 500, message = "El motivo admite hasta 500 caracteres") String reason,
		@Schema(description = "Inicio con su desplazamiento, p. ej. 2026-11-11T00:30:00Z o 2026-11-10T19:30:00-05:00")
		Optional<OffsetDateTime> startedAt,
		@Schema(nullable = true, description = "Fin con su desplazamiento; null = no informado")
		Optional<OffsetDateTime> endedAt,
		@Schema(nullable = true, description = "kWh; null = no informado (distinto de 0)")
		Optional<@DecimalMin(value = "0", message = "La energía no puede ser negativa") BigDecimal> energyKwh,
		@Schema(nullable = true, description = "Equipo del inventario; null = pendiente de vincular")
		Optional<UUID> equipmentId) {

	public EditEnergySessionRequest {
		reason = reason == null ? null : reason.strip();
	}

	public EditSessionCommand toCommand(UUID id) {
		return new EditSessionCommand(id, version, reason, startedAt, endedAt, energyKwh, equipmentId);
	}

	/**
	 * Distingue un campo ausente (se conserva: {@code null}) de uno enviado en {@code null} (no informado:
	 * {@code Optional.empty()}). Jackson entrega {@code Optional.empty()} en ambos casos.
	 */
	static final class Reader extends ValueDeserializer<EditEnergySessionRequest> {

		@Override
		public EditEnergySessionRequest deserialize(JsonParser parser, DeserializationContext context) {
			JsonNode body = context.readTree(parser);
			return new EditEnergySessionRequest(value(context, body, "version", Long.class),
					value(context, body, "reason", String.class), change(context, body, "startedAt", OffsetDateTime.class),
					change(context, body, "endedAt", OffsetDateTime.class),
					change(context, body, "energyKwh", BigDecimal.class), change(context, body, "equipmentId", UUID.class));
		}

		private static <T> T value(DeserializationContext context, JsonNode body, String name, Class<T> type) {
			JsonNode node = body.get(name);
			if (node == null || node.isNull()) {
				return null;
			}
			if (type == OffsetDateTime.class) {
				// Se conserva el desplazamiento escrito: Jackson lo llevaria a UTC y se perderia el de origen.
				try {
					return type.cast(OffsetDateTime.parse(node.asString()));
				}
				catch (DateTimeParseException ex) {
					throw InvalidFormatException.from(context.getParser(), "Fecha con desplazamiento invalida en " + name,
							node.asString(), OffsetDateTime.class);
				}
			}
			return context.readTreeAsValue(node, type);
		}

		@SuppressWarnings("OptionalAssignedToNull")
		private static <T> Optional<T> change(DeserializationContext context, JsonNode body, String name,
				Class<T> type) {
			if (!body.has(name)) {
				return null;
			}
			return Optional.ofNullable(value(context, body, name, type));
		}
	}
}
