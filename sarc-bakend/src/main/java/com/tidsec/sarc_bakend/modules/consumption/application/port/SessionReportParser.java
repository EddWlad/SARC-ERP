package com.tidsec.sarc_bakend.modules.consumption.application.port;

import java.util.List;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.importing.TabularRow;

/**
 * Interpreta el reporte original de una plataforma (Strategy, TRD 05): cada fuente sabe en que
 * columnas, formato de fecha, zona y separador decimal vienen sus datos. Agregar una fuente es
 * agregar una implementacion.
 */
public interface SessionReportParser {

	SessionSource source();

	/**
	 * Una {@link ParsedSession} por fila de datos. Si el archivo no tiene las columnas de la fuente
	 * lanza {@code BusinessRuleException}: no es un reporte de esa plataforma.
	 */
	List<ParsedSession> parse(List<TabularRow> rows);
}
