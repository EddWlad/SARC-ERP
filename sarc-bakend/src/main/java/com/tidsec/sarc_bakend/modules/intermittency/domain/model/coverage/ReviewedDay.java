package com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Marca de que un dia ya terminado se reviso para toda la red KIA: la base de la cobertura de la disponibilidad
 * estimada (FR-044). Hay un registro por dia; retirar la marca la deja inactiva y marcar de nuevo la reactiva.
 * Cada accion deja un {@link ReviewedDayEvent}.
 */
@Entity
@Table(name = "itm_reviewed_day")
public class ReviewedDay {

	public static final String AUDIT_ENTITY_TYPE = "INTERMITTENCY_REVIEWED_DAY";

	public static final int MAX_JUSTIFICATION = 500;

	@Id
	private LocalDate day;

	@Column(nullable = false)
	private boolean active;

	@Column(nullable = false)
	private UUID markedBy;

	@Column(nullable = false)
	private Instant markedAt;

	@Version
	@Column(nullable = false)
	private Long version;

	protected ReviewedDay() {
	}

	private ReviewedDay(LocalDate day, UUID actorId, Instant at) {
		this.day = day;
		this.active = true;
		this.markedBy = actorId;
		this.markedAt = at;
	}

	/** Marca por primera vez un dia ya terminado: hoy y los dias futuros no se pueden marcar (FR-044). */
	public static ReviewedDay mark(LocalDate day, LocalDate today, UUID actorId, Instant at) {
		requireFinished(day, today);
		return new ReviewedDay(day, actorId, at);
	}

	/** Vuelve a marcar un dia cuya marca se retiro; si ya esta marcado no cambia nada. */
	public void remark(LocalDate today, UUID actorId, Instant at) {
		requireFinished(day, today);
		if (!active) {
			this.active = true;
			this.markedBy = actorId;
			this.markedAt = at;
		}
	}

	/** Retira la marca con una justificacion obligatoria; el dia vuelve a quedar sin datos. */
	public void withdraw(String justification) {
		if (!active) {
			throw new BusinessRuleException("DAY_NOT_REVIEWED", "El dia no esta marcado como revisado");
		}
		requireJustification(justification);
		this.active = false;
	}

	public static void requireJustification(String justification) {
		if (justification == null || justification.isBlank()) {
			throw new BusinessRuleException("JUSTIFICATION_REQUIRED", "Indique la justificacion");
		}
		if (justification.strip().length() > MAX_JUSTIFICATION) {
			throw new BusinessRuleException("JUSTIFICATION_TOO_LONG",
					"La justificacion admite hasta " + MAX_JUSTIFICATION + " caracteres");
		}
	}

	/** Un cambio hecho desde una version anterior no se aplica sin volver a revisarlo (FR-018). */
	public void requireVersion(long expected) {
		if (version == null || version != expected) {
			throw new ConflictException("CONCURRENT_MODIFICATION",
					"El registro fue modificado por otro usuario; recargue e intente de nuevo");
		}
	}

	private static void requireFinished(LocalDate day, LocalDate today) {
		if (!day.isBefore(today)) {
			throw new BusinessRuleException("DAY_NOT_FINISHED", "Solo se marca un dia que ya termino");
		}
	}

	public LocalDate getDay() {
		return day;
	}

	public boolean isActive() {
		return active;
	}

	public UUID getMarkedBy() {
		return markedBy;
	}

	public Instant getMarkedAt() {
		return markedAt;
	}

	public Long getVersion() {
		return version;
	}
}
