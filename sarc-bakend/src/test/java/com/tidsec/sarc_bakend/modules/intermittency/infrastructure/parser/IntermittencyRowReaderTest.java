package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowInterpretation;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.ImportRowInterpreter;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.Entry;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.Layout;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.TimeCells;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;

class IntermittencyRowReaderTest {

	private static final Instant NOW = Instant.parse("2026-11-10T20:00:00Z");

	private final IntermittencyRowReader parser = new IntermittencyRowReader(new TabularReader());
	private final ImportRowInterpreter interpreter = new ImportRowInterpreter(ZoneId.of("America/Guayaquil"));

	/** Lo que se entiende de cada fila, para comparar formatos entre si. */
	private List<String> understood(List<RawRow> rows) {
		return rows.stream().map(raw -> {
			RowInterpretation row = interpreter.interpret(raw, List.of(), null, NOW);
			return raw.rowNumber() + "|" + row.date() + "|" + row.code() + "|" + row.fileTotalMinutes() + "|"
					+ row.windows().stream().map(window -> window.pair() + ":" + window.startAt() + ">" + window.endAt()
							+ "=" + window.minutes() + window.issues()).toList();
		}).toList();
	}

	private static final List<Entry> ROWS = SyntheticOperationalLog.allRows();

	@Test
	void everyFormatProducesTheSameInterpretation() {
		List<String> xlsxFraction = understood(parser.parse(
				SyntheticOperationalLog.xlsx(ROWS, Layout.WITH_HEADER, TimeCells.FRACTION)));
		List<String> xlsxTime = understood(parser.parse(
				SyntheticOperationalLog.xlsx(ROWS, Layout.WITH_HEADER, TimeCells.TIME_FORMAT)));
		List<String> csvComma = understood(parser.parse(SyntheticOperationalLog.csv(ROWS, ',', Layout.WITH_HEADER)));
		List<String> csvSemicolon = understood(parser.parse(SyntheticOperationalLog.csv(ROWS, ';', Layout.WITH_HEADER)));
		List<String> pasted = understood(parser.parseText(SyntheticOperationalLog.pasted(ROWS, Layout.WITH_HEADER)));

		assertThat(xlsxFraction).hasSize(ROWS.size());
		assertThat(xlsxTime).isEqualTo(xlsxFraction);
		assertThat(csvComma).isEqualTo(xlsxFraction);
		assertThat(csvSemicolon).isEqualTo(xlsxFraction);
		assertThat(pasted).isEqualTo(xlsxFraction);
	}

	@Test
	void theSameRowsWithoutAHeaderFollowTheTemplateOrder() {
		List<String> withHeader = understood(parser.parse(SyntheticOperationalLog.csv(ROWS, ',', Layout.WITH_HEADER)));

		assertThat(understood(parser.parse(SyntheticOperationalLog.csv(ROWS, ';', Layout.HEADERLESS))))
				.isEqualTo(withHeader);
		assertThat(understood(parser.parseText(SyntheticOperationalLog.pasted(ROWS, Layout.HEADERLESS))))
				.isEqualTo(withHeader);
		assertThat(understood(parser.parse(SyntheticOperationalLog.xlsx(ROWS, Layout.HEADERLESS, TimeCells.FRACTION))))
				.isEqualTo(withHeader);
	}

	@Test
	void keepsTheOriginalRowNumbersAndTheColumnLabels() {
		List<RawRow> rows = parser.parse(SyntheticOperationalLog.csv(ROWS, ',', Layout.WITH_HEADER));

		assertThat(rows).extracting(RawRow::rowNumber).startsWith(3, 57, 61, 94, 99, 117);
		RawRow row3 = rows.getFirst();
		assertThat(row3.cells().getFirst().label()).isEqualTo("Fecha");
		assertThat(row3.get(RawRow.CODE)).isEqualTo("900001");
		assertThat(row3.pairs()).hasSize(7);
		assertThat(row3.get(RawRow.start(3))).isEqualTo("17:55");
		assertThat(row3.get(RawRow.TOTAL)).isEqualTo("1:49");
	}

	@Test
	void headersAreRecognizedWithoutAccentsOrCaseAndInAnyOrder() {
		String csv = "TOTAL INACTIVO;fin 1; Inicio  1 ;CODIGO cargador;Fecha\n1:00;09:00;08:00;900001;01/10/2026\n";

		RawRow row = parser.parse(csv.getBytes(StandardCharsets.UTF_8)).getFirst();

		assertThat(row.get(RawRow.DATE)).isEqualTo("01/10/2026");
		assertThat(row.get(RawRow.CODE)).isEqualTo("900001");
		assertThat(row.get(RawRow.start(1))).isEqualTo("08:00");
		assertThat(row.get(RawRow.end(1))).isEqualTo("09:00");
		assertThat(row.get(RawRow.TOTAL)).isEqualTo("1:00");
	}

	@Test
	void readsWindows1252CsvAndUtf8WithBom() {
		String csv = "Fecha,Código cargador,Inicio 1,Fin 1\n01/10/2026,900001,08:00,09:00\n";

		assertThat(parser.parse(csv.getBytes(java.nio.charset.Charset.forName("windows-1252"))).getFirst()
				.get(RawRow.CODE)).isEqualTo("900001");
		assertThat(parser.parse(("﻿" + csv).getBytes(StandardCharsets.UTF_8)).getFirst().get(RawRow.CODE))
				.isEqualTo("900001");
	}

	@Test
	void onlyTheFirstSheetOfAWorkbookIsRead() throws IOException {
		byte[] original = SyntheticOperationalLog.xlsx(ROWS, Layout.WITH_HEADER, TimeCells.FRACTION);
		byte[] twoSheets;
		try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(original));
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			var other = book.createSheet("Otra");
			other.createRow(0).createCell(0).setCellValue("Fecha");
			other.createRow(1).createCell(0).setCellValue("no debe leerse");
			book.write(out);
			twoSheets = out.toByteArray();
		}

		assertThat(parser.parse(twoSheets)).hasSize(ROWS.size());
	}

	@Test
	void rejectsAMissingColumnNamingIt() {
		assertThatThrownBy(() -> parser.parse(SyntheticOperationalLog.xlsxWithoutCodeColumn(ROWS)))
				.isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
					assertThat(ex.code()).isEqualTo("IMPORT_HEADER_MISSING");
					assertThat(ex.getMessage()).contains("Código cargador");
				});
		assertHeaderMissing("Fecha,Código cargador,Inicio 1\n01/10/2026,900001,08:00\n", "Fin 1");
		assertHeaderMissing("Fecha,Código cargador,Fin 2,Inicio 1,Fin 1\n01/10/2026,900001,08:00,09:00,10:00\n",
				"Inicio 2");
		assertHeaderMissing("Fecha,Código cargador,Comentario\n01/10/2026,900001,x\n", "Inicio 1");
		assertHeaderMissing("Fecha,Inicio 1,Fin 1\n01/10/2026,08:00,09:00\n", "Código cargador");
	}

	@Test
	void aHeaderlessFileWithFourColumnsHasNoPairs() {
		assertHeaderMissing("01/10/2026,900001,Punto,Intermitencia\n", "Inicio 1");
	}

	@Test
	void aHeaderlessFileUsesTheColumnCountToTellTheTotalFromThePairs() {
		// 15 columnas: k = 11 (impar), 5 pares y Total.
		RawRow withTotal = parser.parseText("01/10/2026\t900001\tP\tc\t08:00\t09:00\t\t\t\t\t\t\t\t\t1:00\n").getFirst();
		assertThat(withTotal.pairs()).containsExactly(1, 2, 3, 4, 5);
		assertThat(withTotal.get(RawRow.TOTAL)).isEqualTo("1:00");

		// 18 columnas: k = 14 (par), 7 pares y sin Total.
		RawRow noTotal = parser.parseText(
				"01/10/2026\t900001\tP\tc\t08:00\t09:00\t\t\t\t\t\t\t\t\t\t\t\t13:00\n").getFirst();
		assertThat(noTotal.pairs()).hasSize(7);
		assertThat(noTotal.get(RawRow.TOTAL)).isEmpty();
		assertThat(noTotal.cells()).extracting(RawRow.RawCell::key).doesNotContain(RawRow.TOTAL);
	}

	private void assertHeaderMissing(String csv, String column) {
		assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)))
				.isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
					assertThat(ex.code()).isEqualTo("IMPORT_HEADER_MISSING");
					assertThat(ex.getMessage()).contains(column);
				});
	}
}
