package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Visita tecnica de un episodio, registrada a mano junto con su proveedor: SARC no asigna ni avisa al proveedor
 * (P-08). Guarda solo la etapa actual; el historial de etapas esta en los eventos {@code VISIT_STAGE}, y los informes
 * cuentan las visitas por etapa alcanzada, no solo por la actual.
 */
@Entity
@Table(name = "inc_visit")
public class Visit extends BaseAuditableEntity {

	@Column(nullable = false, updatable = false)
	private UUID ticketId;

	@Column(nullable = false, updatable = false)
	private UUID episodeId;

	private String providerName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VisitStage stage;

	private String cancelDecidedBy;

	private String cancelEvidenceNote;

	private String reescalationCondition;

	protected Visit() {
	}

	private Visit(UUID ticketId, UUID episodeId, String providerName) {
		super(UUID.randomUUID());
		this.ticketId = ticketId;
		this.episodeId = episodeId;
		this.providerName = providerName;
		this.stage = VisitStage.REQUESTED;
	}

	/** Una visita nueva, siempre en etapa {@code REQUESTED}. */
	public static Visit request(UUID ticketId, UUID episodeId, String providerName) {
		return new Visit(ticketId, episodeId, providerName);
	}

	/**
	 * Pasa a otra etapa. Solo se permiten las transiciones de {@link VisitStage#next()}; cancelar o aplazar exige quien
	 * decidio, la nota de evidencia y la condicion para volver a escalar (FR-035). {@code providerName} solo cambia si
	 * llega con valor, por ejemplo al asignar la visita.
	 */
	public void moveTo(VisitStage target, String providerName, String decidedBy, String evidenceNote,
			String reescalationCondition) {
		if (!stage.canMoveTo(target)) {
			throw IncidentErrors.visitTransitionInvalid("Una visita en etapa " + stage + " no puede pasar a " + target);
		}
		if (target.needsDecision()) {
			if (isBlank(decidedBy) || isBlank(evidenceNote) || isBlank(reescalationCondition)) {
				throw IncidentErrors.visitTransitionInvalid(
						"Cancelar o aplazar exige quién decidió, la evidencia y la condición para volver a escalar");
			}
			this.cancelDecidedBy = decidedBy.strip();
			this.cancelEvidenceNote = evidenceNote.strip();
			this.reescalationCondition = reescalationCondition.strip();
		}
		if (!isBlank(providerName)) {
			this.providerName = providerName.strip();
		}
		this.stage = target;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	public UUID getTicketId() {
		return ticketId;
	}

	public UUID getEpisodeId() {
		return episodeId;
	}

	public String getProviderName() {
		return providerName;
	}

	public VisitStage getStage() {
		return stage;
	}

	public String getCancelDecidedBy() {
		return cancelDecidedBy;
	}

	public String getCancelEvidenceNote() {
		return cancelEvidenceNote;
	}

	public String getReescalationCondition() {
		return reescalationCondition;
	}
}
