package com.tidsec.sarc_bakend.shared.persistence;

import java.time.DayOfWeek;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Dia ISO en SMALLINT: 1 = lunes ... 7 = domingo (no el ordinal 0-6 del enum). */
@Converter(autoApply = true)
class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

	@Override
	public Short convertToDatabaseColumn(DayOfWeek day) {
		return day == null ? null : (short) day.getValue();
	}

	@Override
	public DayOfWeek convertToEntityAttribute(Short value) {
		return value == null ? null : DayOfWeek.of(value);
	}
}
