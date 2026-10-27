package com.tidsec.sarc_bakend.shared.catalog;

import java.util.Map;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.report.ReportFile;

/**
 * Puerto publico del archivo de reportes (PRD RF-RPT-08): cada modulo que emite un reporte deja una
 * copia privada del archivo tal como se entrego, con su alcance y su huella, para poder mostrarlo
 * despues sin reconstruirlo. Lo implementa el modulo de reportes.
 */
public interface ReportArchive {

	/** Reporte emitido: que es ({@code kind}, p. ej. CONSUMPTION), su alcance legible y datos para la auditoria. */
	record IssuedReportFile(String kind, String scope, ReportFile file, Map<String, String> details) {
	}

	/**
	 * Archiva el reporte y registra su emision en la auditoria; devuelve el id del registro. Si el
	 * almacenamiento no esta disponible, la emision se audita igual (con su huella) y se avisa en el log:
	 * no se niega el reporte a quien lo pidio.
	 */
	UUID archive(IssuedReportFile report);
}
