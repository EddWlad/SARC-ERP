package com.tidsec.sarc_bakend.shared.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class HeaderLayoutTest {

	private static final List<HeaderLayout.Column> COLUMNS = List.of(new HeaderLayout.Column("id", "ID"),
			new HeaderLayout.Column("end", "TERMINO"), new HeaderLayout.Column("energy", "ENERGIA CARGADA"));

	@Test
	void findsHeadersBelowATitleIgnoringAccentsOrderAndExtraColumns() {
		List<TabularRow> rows = List.of(
				new TabularRow(1, List.of("", "Transacciones", "23-10-2026 13:56:11 UTC-05:00")),
				new TabularRow(2, List.of("SOC (%)", "ENERGÍA  CARGADA (kWh)", "ID", "TÉRMINO (UTC-05:00)")),
				new TabularRow(3, List.of("49", "22.42", "27151", "23-10-2026 00:14:50")));

		HeaderLayout layout = HeaderLayout.locate(rows, COLUMNS);

		assertThat(layout.dataRows()).hasSize(1);
		TabularRow data = layout.dataRows().getFirst();
		assertThat(layout.value(data, "id")).isEqualTo("27151");
		assertThat(layout.value(data, "energy")).isEqualTo("22.42");
		assertThat(layout.header("end")).isEqualTo("TÉRMINO (UTC-05:00)");
	}

	@Test
	void namesTheMissingColumnsWhenTheFileIsFromAnotherSource() {
		List<TabularRow> rows = List.of(new TabularRow(1, List.of("Serial number", "Session id", "Start")));

		assertThatThrownBy(() -> HeaderLayout.locate(rows, COLUMNS))
				.extracting("code", "message")
				.containsExactly("IMPORT_COLUMNS_MISSING",
						"El archivo no corresponde a la fuente elegida; faltan las columnas: ID, TERMINO, ENERGIA CARGADA");
	}
}
