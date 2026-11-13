package com.tidsec.sarc_bakend.modules.intermittency.domain.model.window;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.FutureInstantPolicy;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.LongWindowPolicy;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Ventana de intermitencia de un punto de carga KIA: el periodo en que estuvo fuera de linea, con instantes
 * completos en minuto exacto. Es la cabecera vigente; cada cambio agrega una {@link WindowRevision}.
 *
 * <p>No tiene setters. Los cambios de estado son metodos que aplican la tabla de transiciones de
 * data-model.md y devuelven la revision creada. La duracion se deriva siempre de los instantes. La regla de
 * no solapamiento entre ventanas la aplica el servicio de escritura; aqui solo se validan las reglas de una
 * ventana por si sola.
 */
@Entity
@Table(name = "itm_window")
public class IntermittencyWindow extends BaseAuditableEntity {

	public static final String AUDIT_ENTITY_TYPE = "INTERMITTENCY_WINDOW";

	public static final int MAX_JUSTIFICATION = 500;

	@Column(nullable = false, updatable = false)
	private UUID equipmentId;

	/** Nombre y codigo del punto en la fecha del hecho: sobrevive a renombres del equipo (D12). */
	@Column(nullable = false, updatable = false, length = 200)
	private String pointLabel;

	@Column(nullable = false)
	private Instant startAt;

	private Instant endAt;

	private Integer durationMinutes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private WindowState state;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<ReviewReason> reviewReasons = new ArrayList<>();

	@Column(length = 500)
	private String comment;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private WindowSource source;

	@Column(updatable = false)
	private UUID batchId;

	@Column(updatable = false)
	private Integer rowNumber;

	@Column(updatable = false)
	private Integer pairNumber;

	@Column(nullable = false)
	private int currentRevision;

	protected IntermittencyWindow() {
	}

	private IntermittencyWindow(UUID equipmentId, String pointLabel, WindowSource source, ImportProvenance provenance) {
		super(UUID.randomUUID());
		this.equipmentId = equipmentId;
		this.pointLabel = pointLabel;
		this.source = source;
		if (provenance != null) {
			this.batchId = provenance.batchId();
			this.rowNumber = provenance.rowNumber();
			this.pairNumber = provenance.pairNumber();
		}
		this.currentRevision = 1;
	}

	/**
	 * Crea una ventana. Sin fin queda OPEN; con fin, CONFIRMED o, si dura 240 minutos o mas, REVIEW. Con
	 * {@code test} queda TEST (siempre cerrada, FR-016) y conserva sus motivos de revision para cuando se
	 * quite la marca. Los instantes futuros se rechazan (FR-021b). La primera revision se obtiene con
	 * {@link #snapshot}.
	 */
	public static IntermittencyWindow register(UUID equipmentId, String pointLabel, Instant startAt, Instant endAt,
			String comment, WindowSource source, ImportProvenance provenance, boolean test, Instant now) {
		if (pointLabel == null || pointLabel.isBlank()) {
			throw new IllegalArgumentException("La ventana necesita la etiqueta del punto");
		}
		IntermittencyWindow window = new IntermittencyWindow(equipmentId, abbreviate(pointLabel.strip(), 200), source,
				provenance);
		requireMinute(startAt, "El inicio");
		requireNotFuture(startAt, now, "El inicio");
		window.startAt = startAt;
		window.comment = optionalComment(comment);
		if (endAt == null) {
			if (test) {
				throw new BusinessRuleException("OPEN_TEST_NOT_ALLOWED",
						"Una ventana de prueba debe estar cerrada; registre su fin o marquela como prueba despues de cerrarla");
			}
			window.state = WindowState.OPEN;
			return window;
		}
		window.setEnd(endAt, now);
		WindowState closed = window.closedState();
		window.state = test ? WindowState.TEST : closed;
		return window;
	}

	/** Revision con los valores actuales; la primera se pide justo despues de crear la ventana. */
	public WindowRevision snapshot(WindowChange change, String justification, UUID actorId, Instant at) {
		return new WindowRevision(this, change, justification, actorId, at);
	}

	/** Pone fin a una ventana OPEN: queda CONFIRMED o, si dura 240 minutos o mas, REVIEW. */
	public WindowRevision close(Instant endAt, UUID actorId, Instant now) {
		if (state != WindowState.OPEN) {
			throw new BusinessRuleException("WINDOW_NOT_OPEN", "Solo se cierra una ventana abierta");
		}
		setEnd(endAt, now);
		this.state = closedState();
		return nextRevision(WindowChange.CLOSED, null, actorId, now);
	}

	/**
	 * Corrige el inicio, el fin o el comentario de una ventana CONFIRMED o REVIEW. Se vuelven a aplicar las
	 * validaciones: puede quedar de nuevo en REVIEW. Una ventana cerrada no se reabre.
	 */
	public WindowRevision correct(Instant newStart, Instant newEnd, String newComment, String justification,
			UUID actorId, Instant now) {
		requireCorrectable();
		String reason = requireJustification(justification);
		String commentAfter = newComment == null ? comment : optionalComment(newComment);
		boolean unchanged = (newStart == null || newStart.equals(startAt)) && (newEnd == null || newEnd.equals(endAt))
				&& java.util.Objects.equals(commentAfter, comment);
		if (unchanged) {
			throw new BusinessRuleException("WINDOW_NOT_CHANGED", "La correccion no cambia ningun dato de la ventana");
		}
		Instant previousStart = startAt;
		if (newStart != null) {
			requireMinute(newStart, "El inicio");
			requireNotFuture(newStart, now, "El inicio");
			this.startAt = newStart;
		}
		try {
			setEnd(newEnd != null ? newEnd : endAt, now);
		}
		catch (BusinessRuleException invalid) {
			this.startAt = previousStart;
			throw invalid;
		}
		if (newComment != null) {
			this.comment = optionalComment(newComment);
		}
		this.state = closedState();
		return nextRevision(WindowChange.CORRECTED, reason, actorId, now);
	}

	/**
	 * La ventana de un archivo reemplaza a esta (FR-029): cambia su inicio y su fin con una revision justificada.
	 * Solo una ventana abierta, por revisar o confirmada; una abierta puede seguir abierta si el archivo no trae fin.
	 */
	public WindowRevision replaceByImport(Instant newStart, Instant newEnd, String justification, UUID actorId,
			Instant now) {
		requireState("WINDOW_NOT_REPLACEABLE", "Solo se reemplaza una ventana abierta, por revisar o confirmada",
				WindowState.OPEN, WindowState.REVIEW, WindowState.CONFIRMED);
		String reason = requireJustification(justification);
		if (newEnd == null && state != WindowState.OPEN) {
			throw new BusinessRuleException("WINDOW_NOT_REPLACEABLE",
					"Una ventana cerrada no se reemplaza por una abierta");
		}
		requireMinute(newStart, "El inicio");
		requireNotFuture(newStart, now, "El inicio");
		this.startAt = newStart;
		if (newEnd == null) {
			this.endAt = null;
			this.durationMinutes = null;
			this.reviewReasons = new ArrayList<>();
		}
		else {
			setEnd(newEnd, now);
			this.state = closedState();
		}
		return nextRevision(WindowChange.REPLACED_BY_IMPORT, reason, actorId, now);
	}

	/** Solo se corrige una ventana confirmada o por revisar. */
	public void requireCorrectable() {
		requireState("WINDOW_NOT_CORRECTABLE", "Solo se corrige una ventana confirmada o por revisar",
				WindowState.CONFIRMED, WindowState.REVIEW);
	}

	/** Un editor confirma una ventana por revisar. */
	public WindowRevision confirmReview(String justification, UUID actorId, Instant now) {
		requireState("WINDOW_NOT_IN_REVIEW", "Solo se confirma una ventana por revisar", WindowState.REVIEW);
		String reason = requireJustification(justification);
		this.state = WindowState.CONFIRMED;
		this.reviewReasons = new ArrayList<>();
		return nextRevision(WindowChange.REVIEW_CONFIRMED, reason, actorId, now);
	}

	/**
	 * Marca como prueba una ventana CONFIRMED o REVIEW. Desde REVIEW se conservan sus motivos de revision y no
	 * hace falta confirmarla antes: asi nunca cuenta en lo oficial.
	 */
	public WindowRevision markTest(String justification, UUID actorId, Instant now) {
		requireState("WINDOW_NOT_MARKABLE", "Solo se marca como prueba una ventana confirmada o por revisar",
				WindowState.CONFIRMED, WindowState.REVIEW);
		String reason = requireJustification(justification);
		this.state = WindowState.TEST;
		return nextRevision(WindowChange.MARKED_TEST, reason, actorId, now);
	}

	/** Quita la marca de prueba; se vuelven a aplicar las validaciones, asi que puede volver a REVIEW. */
	public WindowRevision unmarkTest(String justification, UUID actorId, Instant now) {
		requireState("WINDOW_NOT_TEST", "Solo se quita la marca a una ventana de prueba", WindowState.TEST);
		String reason = requireJustification(justification);
		this.state = closedState();
		return nextRevision(WindowChange.UNMARKED_TEST, reason, actorId, now);
	}

	/** Deja la ventana sin efecto. Se conserva con su historial; nunca se borra. */
	public WindowRevision annul(String justification, UUID actorId, Instant now) {
		if (state == WindowState.ANNULLED) {
			throw new BusinessRuleException("WINDOW_ALREADY_ANNULLED", "La ventana ya esta anulada");
		}
		String reason = requireJustification(justification);
		this.state = WindowState.ANNULLED;
		return nextRevision(WindowChange.ANNULLED, reason, actorId, now);
	}

	// ----- Reglas de una ventana por si sola -----

	private void setEnd(Instant newEnd, Instant now) {
		requireMinute(newEnd, "El fin");
		if (!newEnd.isAfter(startAt)) {
			throw new BusinessRuleException("WINDOW_END_BEFORE_START", "El fin debe ser posterior al inicio");
		}
		requireNotFuture(newEnd, now, "El fin");
		this.endAt = newEnd;
		this.durationMinutes = (int) Duration.between(startAt, newEnd).toMinutes();
	}

	/** CONFIRMED o REVIEW segun la duracion; fija los motivos de revision. */
	private WindowState closedState() {
		this.reviewReasons = new ArrayList<>(LongWindowPolicy.reasonsFor(durationMinutes));
		return reviewReasons.isEmpty() ? WindowState.CONFIRMED : WindowState.REVIEW;
	}

	private WindowRevision nextRevision(WindowChange change, String justification, UUID actorId, Instant at) {
		this.currentRevision++;
		return snapshot(change, justification, actorId, at);
	}

	private void requireState(String code, String message, WindowState... allowed) {
		for (WindowState candidate : allowed) {
			if (state == candidate) {
				return;
			}
		}
		throw new BusinessRuleException(code, message);
	}

	/** Justificacion obligatoria de un cambio: no vacia y de hasta {@link #MAX_JUSTIFICATION} caracteres. */
	public static String requireJustification(String justification) {
		if (justification == null || justification.isBlank()) {
			throw new BusinessRuleException("JUSTIFICATION_REQUIRED", "Este cambio exige una justificacion");
		}
		String text = justification.strip();
		if (text.length() > MAX_JUSTIFICATION) {
			throw new BusinessRuleException("JUSTIFICATION_TOO_LONG",
					"La justificacion admite " + MAX_JUSTIFICATION + " caracteres");
		}
		return text;
	}

	private static String optionalComment(String comment) {
		if (comment == null || comment.isBlank()) {
			return null;
		}
		String text = comment.strip();
		if (text.length() > 500) {
			throw new BusinessRuleException("COMMENT_TOO_LONG", "El comentario admite 500 caracteres");
		}
		return text;
	}

	private static void requireMinute(Instant instant, String what) {
		if (instant.getNano() != 0 || instant.getEpochSecond() % 60 != 0) {
			throw new BusinessRuleException("WINDOW_INSTANT_NOT_MINUTE", what + " debe estar en minuto exacto");
		}
	}

	private static void requireNotFuture(Instant instant, Instant now, String what) {
		if (FutureInstantPolicy.isFuture(instant, now)) {
			throw new BusinessRuleException("WINDOW_FUTURE_INSTANT",
					what + " no puede ser posterior al minuto en curso");
		}
	}

	private static String abbreviate(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max);
	}

	// ----- Lectura -----

	public boolean isOpen() {
		return state == WindowState.OPEN;
	}

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public String getPointLabel() {
		return pointLabel;
	}

	public Instant getStartAt() {
		return startAt;
	}

	public Instant getEndAt() {
		return endAt;
	}

	public Integer getDurationMinutes() {
		return durationMinutes;
	}

	public WindowState getState() {
		return state;
	}

	public List<ReviewReason> getReviewReasons() {
		return List.copyOf(reviewReasons);
	}

	public String getComment() {
		return comment;
	}

	public WindowSource getSource() {
		return source;
	}

	public UUID getBatchId() {
		return batchId;
	}

	public Integer getRowNumber() {
		return rowNumber;
	}

	public Integer getPairNumber() {
		return pairNumber;
	}

	public int getCurrentRevision() {
		return currentRevision;
	}
}
