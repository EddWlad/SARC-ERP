package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.report;

import java.time.Instant;
import java.util.List;

import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.TopPoint;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;

/** Ayudas comunes del PDF y del Excel, para que ambos escriban lo mismo del mismo modo. */
final class IntermittencyReports {

	private IntermittencyReports() {
	}

	/** Un instante en hora de Ecuador, con el formato de los informes. */
	static String instant(CorporateBranding branding, Instant instant) {
		return branding.dateTimeFormatter().format(instant);
	}

	/** Posicion de cada fila del Top: los empatados en tiempo comparten la primera de ellos (1, 2, 3, 3, 3, 6). */
	static int[] positions(List<TopPoint> top) {
		int[] positions = new int[top.size()];
		for (int index = 0; index < top.size(); index++) {
			int first = index;
			while (first > 0 && top.get(first - 1).minutes() == top.get(index).minutes()) {
				first--;
			}
			positions[index] = first + 1;
		}
		return positions;
	}
}
