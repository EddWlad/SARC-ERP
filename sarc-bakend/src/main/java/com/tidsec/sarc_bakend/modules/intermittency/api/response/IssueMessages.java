package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IssueCode;

/** Texto en espanol de cada motivo, para la columna de error de la vista previa (FR-023). */
final class IssueMessages {

	private IssueMessages() {
	}

	static String of(IssueCode code) {
		return switch (code) {
			case DATE_FORMAT -> "La fecha no es valida";
			case TIME_FORMAT -> "La hora no es valida";
			case SECONDS_AMBIGUOUS -> "La hora tiene segundos que no se pueden redondear al minuto";
			case END_BEFORE_START -> "El fin es anterior o igual al inicio";
			case ZERO_DURATION -> "El inicio y el fin son iguales";
			case INCOMPLETE_PAIR -> "Falta el inicio o el fin";
			case FUTURE_INSTANT -> "Es posterior al minuto en curso";
			case ALREADY_RECORDED -> "Ya existe una ventana identica";
			case OVERLAPS_EXISTING -> "Se solapa con una ventana ya registrada";
			case OVERLAP_IN_BATCH -> "Se solapa con otra ventana del archivo";
			case DUPLICATE_IN_BATCH -> "Duplicada en el lote";
			case UNKNOWN_CODE -> "No hay un punto con ese codigo";
			case AMBIGUOUS_CODE -> "Mas de un punto tiene ese codigo";
			case DELETED_EQUIPMENT -> "El punto esta eliminado";
			case MISSING_CODE -> "La fila no trae codigo";
			case NOT_KIA_CHARGING_POINT -> "No es un punto de carga KIA";
			case TOTAL_MISMATCH -> "El total del archivo no coincide con sus ventanas";
			case LONG_WINDOW -> "Dura 4 horas o mas";
			case NAME_DIFFERS -> "El nombre en el archivo no coincide con el de Inventario (se vincula por el codigo)";
			case INACTIVE_EQUIPMENT -> "El punto esta inactivo";
			case TOTAL_MISSING -> "La fila no trae total inactivo";
		};
	}
}
