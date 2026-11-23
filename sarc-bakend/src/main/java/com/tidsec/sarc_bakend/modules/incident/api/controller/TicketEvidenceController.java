package com.tidsec.sarc_bakend.modules.incident.api.controller;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tidsec.sarc_bakend.modules.incident.api.request.EvidenceMeta;
import com.tidsec.sarc_bakend.modules.incident.api.response.TicketDetailResponse;
import com.tidsec.sarc_bakend.modules.incident.application.service.EvidenceService;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketReadService;

import jakarta.validation.Valid;

/**
 * Evidencias de un ticket (FR-037): carga multipart con las partes {@code file} y {@code meta}, y descarga por
 * redireccion 302 a una URL firmada de vida corta. El archivo nunca pasa por este servidor al descargar.
 */
@RestController
@RequestMapping("/api/v1/incidents/tickets/{id}/evidence")
class TicketEvidenceController {

	private final EvidenceService evidence;
	private final TicketReadService read;

	TicketEvidenceController(EvidenceService evidence, TicketReadService read) {
		this.evidence = evidence;
		this.read = read;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	TicketDetailResponse uploadEvidence(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
			@Valid @RequestPart("meta") EvidenceMeta meta) throws IOException {
		evidence.requireWithinLimit(file.getSize());
		evidence.add(id, meta.version(), file.getBytes(), file.getOriginalFilename(), meta.purpose(), meta.eventId(),
				meta.communicationId(), meta.note());
		return TicketDetailResponse.from(read.detail(id));
	}

	@GetMapping("/{evidenceId}")
	ResponseEntity<Void> downloadEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId) {
		String url = evidence.accessUrl(id, evidenceId);
		return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.CACHE_CONTROL, "no-store")
				.location(URI.create(url)).build();
	}
}
