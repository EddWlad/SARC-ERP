package com.tidsec.sarc_bakend.modules.incident.domain;

import java.util.List;
import java.util.Map;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.error.PayloadTooLargeException;
import com.tidsec.sarc_bakend.shared.error.SarcException;

/**
 * Codigos estables de error del modulo de incidencias (contracts/incidents-api.md) y las excepciones que los
 * llevan. {@code GlobalExceptionHandler} las traduce a problem+json: {@link ConflictException} a 409,
 * {@link BusinessRuleException} a 422 y {@link NotFoundException} a 404. Ningun mensaje incluye secretos ni
 * datos restringidos.
 */
public final class IncidentErrors {

	// Numeracion
	public static final String NUMBERING_NOT_CONFIGURED = "TICKET_NUMBERING_NOT_CONFIGURED";
	public static final String NUMBERING_ENVIRONMENT_MISMATCH = "TICKET_NUMBERING_ENVIRONMENT_MISMATCH";
	public static final String NUMBERING_LOCKED = "TICKET_NUMBERING_LOCKED";
	public static final String NUMBERING_CONFIRMATION_MISMATCH = "TICKET_NUMBERING_CONFIRMATION_MISMATCH";

	// Creacion y elemento afectado
	public static final String OPEN_DUPLICATE = "TICKET_OPEN_DUPLICATE";
	public static final String ELEMENT_INVALID = "TICKET_ELEMENT_INVALID";
	public static final String T0_INVALID = "TICKET_T0_INVALID";
	public static final String MAPS_URL_INVALID = "TICKET_MAPS_URL_INVALID";
	public static final String RESPONSIBLE_INVALID = "TICKET_RESPONSIBLE_INVALID";
	public static final String PRIORITY_REASON_REQUIRED = "TICKET_PRIORITY_REASON_REQUIRED";
	public static final String RELATION_INVALID = "TICKET_RELATION_INVALID";
	public static final String RELATION_EXISTS = "TICKET_RELATION_EXISTS";

	// Ticket, version y ciclo de vida
	public static final String TICKET_NOT_FOUND = "TICKET_NOT_FOUND";
	public static final String VERSION_CONFLICT = "TICKET_VERSION_CONFLICT";
	public static final String ALREADY_CLOSED = "TICKET_ALREADY_CLOSED";
	public static final String ALREADY_OPEN = "TICKET_ALREADY_OPEN";
	public static final String CLOSE_REQUIREMENTS = "TICKET_CLOSE_REQUIREMENTS";
	public static final String REOPEN_FORBIDDEN = "TICKET_REOPEN_FORBIDDEN";

	// Hitos y episodios
	public static final String MILESTONE_INVALID = "MILESTONE_INVALID";
	public static final String MILESTONE_T4_BEFORE_MILESTONES = "MILESTONE_T4_BEFORE_MILESTONES";
	public static final String MILESTONE_BEFORE_T0 = "MILESTONE_BEFORE_T0";
	public static final String MILESTONE_IN_FUTURE = "MILESTONE_IN_FUTURE";
	public static final String MILESTONE_ORDER_UNCONFIRMED = "MILESTONE_ORDER_UNCONFIRMED";
	public static final String MILESTONE_REASON_REQUIRED = "MILESTONE_REASON_REQUIRED";
	public static final String MILESTONE_T4_NOT_APPLICABLE = "MILESTONE_T4_NOT_APPLICABLE";
	public static final String EPISODE_NOT_EDITABLE = "EPISODE_NOT_EDITABLE";

	// Visitas
	public static final String EVENT_INVALID = "TICKET_EVENT_INVALID";
	public static final String JUSTIFICATION_REQUIRED = "TICKET_JUSTIFICATION_REQUIRED";
	public static final String VISIT_TRANSITION_INVALID = "VISIT_TRANSITION_INVALID";
	public static final String VISIT_NOT_FOUND = "VISIT_NOT_FOUND";

	// Evidencias
	public static final String EVIDENCE_FORMAT_NOT_ALLOWED = "EVIDENCE_FORMAT_NOT_ALLOWED";
	public static final String EVIDENCE_TOO_LARGE = "EVIDENCE_TOO_LARGE";
	public static final String EVIDENCE_NOT_FOUND = "EVIDENCE_NOT_FOUND";

	// Informes
	public static final String MAX_PERIOD_DAYS = "MAX_PERIOD_DAYS";

	private IncidentErrors() {
	}

	// ----- Numeracion -----

	public static SarcException numberingNotConfigured() {
		return new ConflictException(NUMBERING_NOT_CONFIGURED,
				"Falta configurar el numero inicial de tickets; un Administrador debe configurarlo antes de crear tickets");
	}

	/**
	 * Al crear un ticket: la propiedad de ambiente del servidor falta o no coincide con la numeracion guardada
	 * (409). Se comprueba antes que la configuracion (research R2).
	 */
	public static SarcException numberingEnvironmentMismatchOnCreate() {
		return new ConflictException(NUMBERING_ENVIRONMENT_MISMATCH,
				"El ambiente de la numeracion no coincide con el del servidor; no se pueden crear tickets");
	}

	/** Al configurar la numeracion: el ambiente enviado no coincide con el del servidor, o este falta (422). */
	public static SarcException numberingEnvironmentMismatchOnConfigure() {
		return new BusinessRuleException(NUMBERING_ENVIRONMENT_MISMATCH,
				"El ambiente enviado no coincide con el del servidor, o el servidor no lo tiene configurado");
	}

	public static SarcException numberingLocked() {
		return new ConflictException(NUMBERING_LOCKED,
				"El numero inicial ya no se puede cambiar: ya existen tickets");
	}

	public static SarcException numberingConfirmationMismatch(String expectedCode) {
		return new BusinessRuleException(NUMBERING_CONFIRMATION_MISMATCH,
				"La confirmacion debe repetir el codigo resultante: " + expectedCode,
				Map.of("expectedCode", expectedCode));
	}

	// ----- Creacion -----

	public static SarcException elementInvalid(String message) {
		return new BusinessRuleException(ELEMENT_INVALID, message);
	}

	public static SarcException t0Invalid(String message) {
		return new BusinessRuleException(T0_INVALID, message);
	}

	public static SarcException mapsUrlInvalid() {
		return new BusinessRuleException(MAPS_URL_INVALID, "El enlace de Maps debe empezar por https://");
	}

	public static SarcException responsibleInvalid() {
		return new BusinessRuleException(RESPONSIBLE_INVALID,
				"El responsable debe ser un Analista o un Administrador activo");
	}

	public static SarcException priorityReasonRequired() {
		return new BusinessRuleException(PRIORITY_REASON_REQUIRED,
				"Cambiar la prioridad propuesta exige un motivo");
	}

	public static SarcException relationInvalid(String message) {
		return new BusinessRuleException(RELATION_INVALID, message);
	}

	public static SarcException relationExists() {
		return new ConflictException(RELATION_EXISTS, "Los tickets ya están relacionados");
	}

	/** Ya hay tickets abiertos del mismo elemento; lleva sus datos para ofrecer el seguimiento (FR-016). */
	public static SarcException openDuplicate(List<Map<String, Object>> openTickets) {
		return new ConflictException(OPEN_DUPLICATE,
				"Ya hay tickets abiertos para este elemento; agregue el seguimiento a uno de ellos o indique un motivo para crear uno nuevo",
				Map.of("openTickets", openTickets));
	}

	// ----- Hitos -----

	public static SarcException milestoneInvalid(String message) {
		return new BusinessRuleException(MILESTONE_INVALID, message);
	}

	public static SarcException milestoneBeforeT0() {
		return new BusinessRuleException(MILESTONE_BEFORE_T0, "La hora no puede ser anterior a T0");
	}

	public static SarcException milestoneInFuture() {
		return new BusinessRuleException(MILESTONE_IN_FUTURE, "La hora no puede estar en el futuro");
	}

	public static SarcException milestoneOrderUnconfirmed() {
		return new BusinessRuleException(MILESTONE_ORDER_UNCONFIRMED,
				"T1, T2 y T3 no siguen ese orden: confirme el orden y justifíquelo para guardarlo");
	}

	public static SarcException milestoneReasonRequired(String message) {
		return new BusinessRuleException(MILESTONE_REASON_REQUIRED, message);
	}

	public static SarcException milestoneT4NotApplicable() {
		return new BusinessRuleException(MILESTONE_T4_NOT_APPLICABLE,
				"T4 siempre lleva hora: no admite «No aplica» ni «Sin dato»");
	}

	public static SarcException milestoneT4BeforeMilestones() {
		return new BusinessRuleException(MILESTONE_T4_BEFORE_MILESTONES,
				"T4 no puede ser anterior a ningún hito registrado del episodio");
	}

	public static SarcException episodeNotEditable(String message) {
		return new ConflictException(EPISODE_NOT_EDITABLE, message);
	}

	// ----- Cierre y reapertura -----

	/** El ticket no cumple los requisitos de cierre: {@code missing} nombra cada uno (FR-047, FR-048, FR-048a). */
	public static SarcException closeRequirements(List<String> missing) {
		return new BusinessRuleException(CLOSE_REQUIREMENTS,
				"Faltan requisitos para cerrar el ticket: " + String.join(", ", missing), Map.of("missing", missing));
	}

	public static SarcException alreadyClosed() {
		return new ConflictException(ALREADY_CLOSED, "El ticket ya está cerrado");
	}

	public static SarcException alreadyOpen() {
		return new ConflictException(ALREADY_OPEN, "El ticket ya está abierto");
	}

	public static SarcException reopenForbidden() {
		return new ConflictException(REOPEN_FORBIDDEN,
				"Un ticket cerrado como duplicado o registrado por error no se reabre; cree otro si hace falta");
	}

	// ----- Seguimiento -----

	public static SarcException eventInvalid(String message) {
		return new BusinessRuleException(EVENT_INVALID, message);
	}

	public static SarcException justificationRequired(String message) {
		return new BusinessRuleException(JUSTIFICATION_REQUIRED, message);
	}

	// ----- Visitas y evidencias -----

	public static SarcException visitTransitionInvalid(String message) {
		return new BusinessRuleException(VISIT_TRANSITION_INVALID, message);
	}

	public static SarcException visitNotFound() {
		return new NotFoundException(VISIT_NOT_FOUND, "La visita no existe");
	}

	public static SarcException evidenceFormatNotAllowed(String message) {
		return new BusinessRuleException(EVIDENCE_FORMAT_NOT_ALLOWED, message);
	}

	public static SarcException evidenceTooLarge(long maxBytes) {
		return new PayloadTooLargeException(EVIDENCE_TOO_LARGE,
				"La evidencia supera el límite de " + (maxBytes / (1024 * 1024)) + " MB");
	}

	public static SarcException evidenceNotFound() {
		return new NotFoundException(EVIDENCE_NOT_FOUND, "La evidencia no existe");
	}

	// ----- Ticket -----

	public static SarcException ticketNotFound() {
		return new NotFoundException(TICKET_NOT_FOUND, "El ticket no existe");
	}

	public static SarcException versionConflict() {
		return new ConflictException(VERSION_CONFLICT,
				"El ticket fue modificado por otro usuario; recargue e intente de nuevo");
	}
}
