package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.IntermittencyQuery;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Preset;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

import jakarta.validation.constraints.Size;

/**
 * Filtros del dashboard por query string. {@code month} es {@code aaaa-mm} para las quincenas; {@code from} y
 * {@code to} son dias, con {@code to} exclusivo, para el rango personalizado. Sin nada, la quincena en curso.
 */
public record IntermittencyFilterParams(Preset preset, @Size(max = 7) String month,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @Size(max = 200) List<UUID> equipmentIds) {

	public IntermittencyQuery toQuery() {
		return new IntermittencyQuery(preset, parseMonth(month), from, to, equipmentIds);
	}

	private static YearMonth parseMonth(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return YearMonth.parse(text);
		}
		catch (DateTimeParseException invalid) {
			throw new BusinessRuleException("PERIOD_INVALID", "El mes debe tener el formato aaaa-mm");
		}
	}
}
