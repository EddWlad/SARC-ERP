package com.tidsec.sarc_bakend.shared.importing.reading;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/**
 * Lee la primera hoja de un XLSX/XLS, un CSV o un bloque pegado desde Excel y lo entrega como filas
 * de texto. No interpreta valores: eso lo hace cada importador, que conoce el formato de su fuente.
 * Las fechas de Excel se convierten a texto ISO y los numeros a su forma decimal exacta, para que
 * el importador reciba siempre texto.
 */
@Component
public class TabularReader {

	/** TRD 09.1: limites de tamano y filas antes de procesar. */
	public static final int MAX_BYTES = 20 * 1024 * 1024;
	public static final int MAX_ROWS = 50_000;
	/** Los reportes de las plataformas traen unas decenas de columnas; el resto se ignora. */
	static final int MAX_COLUMNS = 200;
	/**
	 * Tope de cada parte descomprimida de un XLSX: 50 000 filas de un reporte ocupan unas decenas de MB.
	 * Sin el, un archivo de 20 MiB muy comprimido podria inflarse a varios GB en memoria.
	 */
	static final long MAX_XLSX_ENTRY_BYTES = 128L * 1024 * 1024;

	static {
		ZipSecureFile.setMaxEntrySize(MAX_XLSX_ENTRY_BYTES);
	}

	private static final byte[] ZIP_SIGNATURE = { 'P', 'K', 3, 4 };
	private static final byte[] OLE_SIGNATURE = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0 };
	private static final DateTimeFormatter EXCEL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

	public List<TabularRow> read(byte[] content) {
		if (content.length == 0) {
			throw new BusinessRuleException("IMPORT_FILE_EMPTY", "El archivo esta vacio");
		}
		if (content.length > MAX_BYTES) {
			throw new BusinessRuleException("IMPORT_FILE_TOO_LARGE", "El archivo supera 20 MiB");
		}
		if (startsWith(content, ZIP_SIGNATURE) || startsWith(content, OLE_SIGNATURE)) {
			return readWorkbook(content);
		}
		return readText(decode(content));
	}

	/** Texto pegado desde una hoja (tabulaciones) o un CSV con coma o punto y coma. */
	public List<TabularRow> readText(String text) {
		if (text.isBlank()) {
			throw new BusinessRuleException("IMPORT_FILE_EMPTY", "No hay datos para importar");
		}
		String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
		char delimiter = detectDelimiter(lines);
		List<TabularRow> rows = new ArrayList<>();
		StringBuilder pending = null;
		int startLine = 0;
		for (int index = 0; index < lines.length; index++) {
			// Un campo entre comillas puede contener saltos de linea: se acumula hasta cerrarlo.
			if (pending == null) {
				pending = new StringBuilder(lines[index]);
				startLine = index + 1;
			}
			else {
				pending.append('\n').append(lines[index]);
			}
			if (hasOpenQuote(pending)) {
				continue;
			}
			TabularRow row = new TabularRow(startLine, splitLine(pending.toString(), delimiter));
			pending = null;
			if (!row.isBlank()) {
				addRow(rows, row);
			}
		}
		if (pending != null) {
			// Un campo abierto que nunca se cierra se tragaria el resto del archivo sin avisar.
			throw new BusinessRuleException("IMPORT_UNCLOSED_QUOTE", "El archivo tiene comillas sin cerrar desde la "
					+ "linea " + startLine + "; corrija ese campo y vuelva a cargarlo");
		}
		return rows;
	}

	private List<TabularRow> readWorkbook(byte[] content) {
		try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
			Sheet sheet = workbook.getSheetAt(0);
			List<TabularRow> rows = new ArrayList<>();
			for (Row row : sheet) {
				List<String> cells = new ArrayList<>();
				int columns = Math.min(row.getLastCellNum(), MAX_COLUMNS);
				for (int column = 0; column < columns; column++) {
					cells.add(text(row.getCell(column)));
				}
				TabularRow tabular = new TabularRow(row.getRowNum() + 1, cells);
				if (!tabular.isBlank()) {
					addRow(rows, tabular);
				}
			}
			return rows;
		}
		catch (BusinessRuleException ex) {
			throw ex;
		}
		catch (IOException | RuntimeException ex) {
			throw new BusinessRuleException("IMPORT_FILE_UNREADABLE",
					"No se pudo leer la hoja de calculo; verifique que sea un XLSX o XLS valido");
		}
	}

	private static String text(Cell cell) {
		if (cell == null) {
			return "";
		}
		CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
		return switch (type) {
			case STRING -> cell.getStringCellValue();
			case NUMERIC -> DateUtil.isCellDateFormatted(cell)
					? EXCEL_DATE_TIME.format(cell.getLocalDateTimeCellValue())
					: BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
			case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
			default -> "";
		};
	}

	private static void addRow(List<TabularRow> rows, TabularRow row) {
		if (rows.size() >= MAX_ROWS) {
			throw new BusinessRuleException("IMPORT_TOO_MANY_ROWS",
					"El archivo supera " + MAX_ROWS + " filas; divida la importacion");
		}
		rows.add(row);
	}

	/** El separador es el que mas aparece fuera de comillas en la primera linea con datos. */
	private static char detectDelimiter(String[] lines) {
		String sample = "";
		for (String line : lines) {
			if (!line.isBlank()) {
				sample = line;
				break;
			}
		}
		char best = '\t';
		long bestCount = -1;
		for (char candidate : new char[] { '\t', ';', ',' }) {
			long count = countOutsideQuotes(sample, candidate);
			if (count > bestCount) {
				best = candidate;
				bestCount = count;
			}
		}
		return best;
	}

	private static long countOutsideQuotes(String line, char target) {
		long count = 0;
		boolean quoted = false;
		for (char c : line.toCharArray()) {
			if (c == '"') {
				quoted = !quoted;
			}
			else if (c == target && !quoted) {
				count++;
			}
		}
		return count;
	}

	private static boolean hasOpenQuote(CharSequence text) {
		return text.chars().filter(c -> c == '"').count() % 2 != 0;
	}

	/** RFC 4180: comillas dobles para encerrar campos y "" para una comilla literal. */
	private static List<String> splitLine(String line, char delimiter) {
		List<String> cells = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					current.append('"');
					i++;
				}
				else {
					quoted = !quoted;
				}
			}
			else if (c == delimiter && !quoted) {
				cells.add(current.toString());
				current.setLength(0);
			}
			else {
				current.append(c);
			}
		}
		cells.add(current.toString());
		return cells;
	}

	/** UTF-8 (con o sin BOM); si no es UTF-8 valido, Windows-1252, que es lo que exporta Excel en espanol. */
	private static String decode(byte[] content) {
		try {
			String text = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(content))
					.toString();
			return text.startsWith("﻿") ? text.substring(1) : text;
		}
		catch (CharacterCodingException ex) {
			return new String(content, WINDOWS_1252);
		}
	}

	private static boolean startsWith(byte[] content, byte[] signature) {
		if (content.length < signature.length) {
			return false;
		}
		for (int i = 0; i < signature.length; i++) {
			if (content[i] != signature[i]) {
				return false;
			}
		}
		return true;
	}
}
