package com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/** Excepcion de una fecha concreta: cerrado o un horario especial. Prevalece sobre el habitual. */
@Embeddable
public record ScheduleException(
		@Column(name = "exception_date", nullable = false) LocalDate date,
		@Column(name = "closed", nullable = false) boolean closed,
		@Column(name = "opens_at") LocalTime opensAt,
		@Column(name = "closes_at") LocalTime closesAt,
		@Column(name = "note", length = 200) String note) {

	public ScheduleException {
		if (date == null) {
			throw new BusinessRuleException("INVALID_SCHEDULE_EXCEPTION", "La excepcion necesita una fecha");
		}
		boolean hours = opensAt != null && closesAt != null && !opensAt.equals(closesAt);
		boolean noHours = opensAt == null && closesAt == null;
		if (closed ? !noHours : !hours) {
			throw new BusinessRuleException("INVALID_SCHEDULE_EXCEPTION",
					"Una excepcion es cerrada (sin horas) o tiene apertura y cierre distintos");
		}
		note = note == null || note.isBlank() ? null : note.strip();
		if (note != null && note.length() > 200) {
			throw new BusinessRuleException("INVALID_SCHEDULE_EXCEPTION", "La nota admite 200 caracteres");
		}
	}
}
