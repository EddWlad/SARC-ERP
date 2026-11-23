package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.tidsec.sarc_bakend.modules.incident.application.port.IncidentSettings;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Evidence;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EvidenceFormat;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EvidencePurpose;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.CommunicationRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.EvidenceRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.error.ServiceUnavailableException;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.storage.DocumentFormat;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.shared.storage.ImageFormat;

/**
 * Evidencias de un ticket (FR-037): imagenes JPEG, PNG o WEBP, y PDF, hasta el limite configurado. El formato se decide
 * por la firma binaria del contenido, nunca por el nombre ni por el tipo que declara el cliente. El archivo se guarda
 * en el almacenamiento externo bajo una ruta formada solo con identificadores y la extension del formato detectado, de
 * modo que ningun nombre del cliente llega a una ruta. Una evidencia no se borra: se anula su evento.
 */
@Service
public class EvidenceService {

	private static final int MAX_NAME = 200;
	private static final int MAX_NOTE = 500;

	private final TicketLoader loader;
	private final TicketRepository tickets;
	private final TicketEventRepository events;
	private final EvidenceRepository evidence;
	private final CommunicationRepository communications;
	private final FileStorage storage;
	private final IncidentSettings settings;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final Clock clock;

	EvidenceService(TicketLoader loader, TicketRepository tickets, TicketEventRepository events,
			EvidenceRepository evidence, CommunicationRepository communications, FileStorage storage,
			IncidentSettings settings, AuditTrail audit, CurrentActor actor, Clock clock) {
		this.loader = loader;
		this.tickets = tickets;
		this.events = events;
		this.evidence = evidence;
		this.communications = communications;
		this.storage = storage;
		this.settings = settings;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
	}

	/**
	 * Rechaza un archivo mas grande que el limite antes de leerlo en memoria: el controlador lo llama con el tamano que
	 * declara la parte multipart, y {@link #add} lo vuelve a comprobar con el contenido real.
	 */
	@PreAuthorize(IncidentPermissions.EDITORS)
	public void requireWithinLimit(long sizeBytes) {
		if (sizeBytes > settings.evidenceMaxBytes()) {
			throw IncidentErrors.evidenceTooLarge(settings.evidenceMaxBytes());
		}
	}

	/** Carga una evidencia, deja su evento {@code EVIDENCE_ADDED} y guarda la huella SHA-256 del contenido. */
	@PreAuthorize(IncidentPermissions.EDITORS)
	@Transactional
	public UUID add(UUID ticketId, long version, byte[] content, String originalName, EvidencePurpose purpose,
			UUID eventId, UUID communicationId, String note) {
		Ticket ticket = loader.load(ticketId, version);
		if (content == null || content.length == 0) {
			throw IncidentErrors.evidenceFormatNotAllowed("El archivo está vacío");
		}
		if (content.length > settings.evidenceMaxBytes()) {
			throw IncidentErrors.evidenceTooLarge(settings.evidenceMaxBytes());
		}
		EvidenceFormat format = detect(content).orElseThrow(() -> IncidentErrors
				.evidenceFormatNotAllowed("Solo se aceptan imágenes JPEG, PNG o WEBP, y archivos PDF"));
		if (eventId != null && events.findById(eventId).filter(found -> found.getTicketId().equals(ticketId)).isEmpty()) {
			throw IncidentErrors.eventInvalid("El evento indicado no pertenece al ticket");
		}
		if (communicationId != null && communications.findById(communicationId)
				.filter(found -> found.getTicketId().equals(ticketId)).isEmpty()) {
			throw IncidentErrors.eventInvalid("La comunicación indicada no pertenece al ticket");
		}
		String cleanNote = note == null || note.isBlank() ? null : note.strip();
		if (cleanNote != null && cleanNote.length() > MAX_NOTE) {
			throw IncidentErrors.eventInvalid("La nota admite hasta " + MAX_NOTE + " caracteres");
		}

		UUID evidenceId = UUID.randomUUID();
		String sha256 = ContentFingerprint.sha256(content);
		String reference = storage.storeDocument("incidents/evidence/" + ticketId + "/" + evidenceId,
				evidenceId + "." + format.extension(), content);
		// Si la transaccion no se confirma (tambien por un fallo del commit, ya fuera de este metodo), el archivo se borra.
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status != STATUS_COMMITTED) {
						discard(reference);
					}
				}
			});
		}
		try {
			Instant now = Instant.now(clock);
			UUID author = actor.id().orElse(null);
			Map<String, Object> details = new HashMap<>();
			details.put("evidenceId", evidenceId.toString());
			details.put("format", format.name());
			details.put("purpose", purpose.name());
			details.put("sizeBytes", content.length);
			if (eventId != null) {
				details.put("relatedEventId", eventId.toString());
			}
			if (communicationId != null) {
				details.put("communicationId", communicationId.toString());
			}
			// La evidencia queda ligada a su propio evento EVIDENCE_ADDED: anularlo es la forma de «quitarla».
			TicketEvent event = events.save(TicketEvent.record(ticketId, ticket.latestEpisode().getId(),
					EventType.EVIDENCE_ADDED, now, now, author, false, cleanNote, null, null, details, false));
			evidence.save(Evidence.of(evidenceId, ticketId, event.getId(), communicationId, purpose, reference, format, content.length,
					sha256, sanitize(originalName), author, now));
			ticket.touch(now);
			audit.record("TICKET_EVIDENCE_ADDED", "INCIDENT_TICKET", ticketId, Map.of("code",
					new TicketCode(ticket.getTicketNumber()).text(), "evidence", evidenceId.toString(), "sha256", sha256,
					"format", format.name()));
		}
		catch (RuntimeException ex) {
			discard(reference);
			throw ex;
		}
		return evidenceId;
	}

	/**
	 * URL firmada y de vida corta para descargar la evidencia. La pueden pedir los cinco roles, pero el permiso se
	 * comprueba aqui, antes de pedir la URL al almacenamiento: nunca se entrega una referencia sin autorizar.
	 */
	@PreAuthorize(IncidentPermissions.READERS)
	@Transactional(readOnly = true)
	public String accessUrl(UUID ticketId, UUID evidenceId) {
		tickets.findById(ticketId).orElseThrow(IncidentErrors::ticketNotFound);
		Evidence found = evidence.findById(evidenceId).filter(item -> item.getTicketId().equals(ticketId))
				.orElseThrow(IncidentErrors::evidenceNotFound);
		return storage.documentUrl(found.getStorageRef()).orElseThrow(() -> new ServiceUnavailableException(
				FileStorage.NOT_CONFIGURED, "El almacenamiento de archivos no está configurado en este entorno"));
	}

	private static Optional<EvidenceFormat> detect(byte[] content) {
		if (DocumentFormat.isPdf(content)) {
			return Optional.of(EvidenceFormat.PDF);
		}
		return ImageFormat.detect(content).map(image -> switch (image) {
			case JPEG -> EvidenceFormat.JPEG;
			case PNG -> EvidenceFormat.PNG;
			case WEBP -> EvidenceFormat.WEBP;
		});
	}

	/** Nombre solo para mostrar: sin ruta, sin caracteres de control y acotado. Nunca forma parte de una ruta. */
	static String sanitize(String name) {
		if (name == null) {
			return "evidencia";
		}
		String base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
				.replaceAll("\\p{Cntrl}", "").strip();
		if (base.isEmpty()) {
			return "evidencia";
		}
		return base.length() > MAX_NAME ? base.substring(0, MAX_NAME) : base;
	}

	private void discard(String reference) {
		try {
			storage.delete(reference);
		}
		catch (RuntimeException ignored) {
			// El archivo huerfano no debe tapar el error original de la operacion.
		}
	}
}
