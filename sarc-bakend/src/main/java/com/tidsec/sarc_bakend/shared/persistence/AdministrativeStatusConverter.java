package com.tidsec.sarc_bakend.shared.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/** Guarda el estado administrativo con el codigo numerico del PRD (SMALLINT 0/1/2). */
@Converter(autoApply = true)
class AdministrativeStatusConverter implements AttributeConverter<AdministrativeStatus, Short> {

	@Override
	public Short convertToDatabaseColumn(AdministrativeStatus status) {
		return status == null ? null : (short) status.code();
	}

	@Override
	public AdministrativeStatus convertToEntityAttribute(Short code) {
		return code == null ? null : AdministrativeStatus.fromCode(code);
	}
}
