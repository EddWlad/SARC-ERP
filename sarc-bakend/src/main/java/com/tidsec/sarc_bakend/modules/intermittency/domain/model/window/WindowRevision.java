package com.tidsec.sarc_bakend.modules.intermittency.domain.model.window;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Valores de una ventana en un momento. El historial es inmutable: cada cambio agrega una revision. */
@Entity
@Table(name = "itm_window_revision")
public class WindowRevision {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID windowId;

	@Column(nullable = false, updatable = false)
	private int revision;

	@Column(nullable = false, updatable = false)
	private Instant startAt;

	@Column(updatable = false)
	private Instant endAt;

	@Column(updatable = false)
	private Integer durationMinutes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private WindowState state;

	@Column(updatable = false, length = 500)
	private String comment;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	private List<ReviewReason> reviewReasons;

	@Enumerated(EnumType.STRING)
	@Column(name = "change", nullable = false, updatable = false, length = 20)
	private WindowChange change;

	@Column(updatable = false, length = 500)
	private String justification;

	@Column(nullable = false, updatable = false)
	private UUID actorId;

	@Column(name = "at", nullable = false, updatable = false)
	private Instant at;

	protected WindowRevision() {
	}

	WindowRevision(IntermittencyWindow window, WindowChange change, String justification, UUID actorId, Instant at) {
		this.id = UUID.randomUUID();
		this.windowId = window.getId();
		this.revision = window.getCurrentRevision();
		this.startAt = window.getStartAt();
		this.endAt = window.getEndAt();
		this.durationMinutes = window.getDurationMinutes();
		this.state = window.getState();
		this.comment = window.getComment();
		this.reviewReasons = List.copyOf(window.getReviewReasons());
		this.change = change;
		this.justification = justification;
		this.actorId = actorId;
		this.at = at;
	}

	public UUID getId() {
		return id;
	}

	public UUID getWindowId() {
		return windowId;
	}

	public int getRevision() {
		return revision;
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

	public String getComment() {
		return comment;
	}

	public List<ReviewReason> getReviewReasons() {
		return reviewReasons;
	}

	public WindowChange getChange() {
		return change;
	}

	public String getJustification() {
		return justification;
	}

	public UUID getActorId() {
		return actorId;
	}

	public Instant getAt() {
		return at;
	}
}
