package com.tidsec.sarc_bakend.modules.inventory.infrastructure.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.application.port.EquipmentImportTemplate;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.policy.EquipmentSheetVocabulary;
import com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * Plantilla XLSX de carga masiva: hoja "Equipos" con las columnas que lee SARC y listas desplegables
 * con los valores validos, hoja "Instrucciones" y hoja "Listas" con el catalogo de conectores. Las
 * etiquetas salen del mismo vocabulario que usa el lector, asi que lo que se elige en la lista
 * siempre se acepta.
 */
@Component
class XlsxEquipmentImportTemplate implements EquipmentImportTemplate {

	static final String FILE_NAME = "plantilla-carga-equipos.xlsx";
	/** Filas con listas desplegables (el lector admite hasta 5 000 equipos por archivo). */
	private static final int VALIDATED_ROWS = 5_000;
	private static final byte[] CORPORATE = { 0x51, 0x43, 0x6E };
	private static final byte[] OPTIONAL = { (byte) 0x8A, (byte) 0x82, (byte) 0x99 };

	@Override
	public ReportFile render(List<ConnectorType> connectorTypes) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			CellStyle required = headerStyle(workbook, CORPORATE);
			CellStyle optional = headerStyle(workbook, OPTIONAL);
			Sheet equipment = workbook.createSheet("Equipos");
			Sheet instructions = workbook.createSheet("Instrucciones");
			Sheet lists = workbook.createSheet("Listas");

			writeHeader(equipment, required, optional);
			writeConnectorList(lists, required, connectorTypes);
			addValidations(equipment, connectorTypes.size());
			writeInstructions(instructions, required);

			workbook.write(out);
			return new ReportFile(FILE_NAME, ReportFormat.XLSX, out.toByteArray());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("No se pudo generar la plantilla de equipos", ex);
		}
	}

	private static void writeHeader(Sheet sheet, CellStyle required, CellStyle optional) {
		Row header = sheet.createRow(0);
		for (EquipmentSheetColumns column : EquipmentSheetColumns.values()) {
			Cell cell = header.createCell(column.ordinal());
			cell.setCellValue(column.header());
			cell.setCellStyle(column.required() ? required : optional);
			sheet.setColumnWidth(column.ordinal(), Math.max(14, column.header().length() + 4) * 256);
		}
		sheet.setColumnWidth(EquipmentSheetColumns.NAME.ordinal(), 32 * 256);
		sheet.setColumnWidth(EquipmentSheetColumns.LOCATION.ordinal(), 40 * 256);
		sheet.createFreezePane(0, 1);
	}

	private static void writeConnectorList(Sheet sheet, CellStyle header, List<ConnectorType> connectorTypes) {
		Row title = sheet.createRow(0);
		title.createCell(0).setCellValue("Código de conector");
		title.createCell(1).setCellValue("Nombre");
		title.getCell(0).setCellStyle(header);
		title.getCell(1).setCellStyle(header);
		for (int index = 0; index < connectorTypes.size(); index++) {
			Row row = sheet.createRow(index + 1);
			row.createCell(0).setCellValue(connectorTypes.get(index).getCode());
			row.createCell(1).setCellValue(connectorTypes.get(index).getName());
		}
		sheet.setColumnWidth(0, 22 * 256);
		sheet.setColumnWidth(1, 40 * 256);
	}

	private static void addValidations(Sheet sheet, int connectorCount) {
		DataValidationHelper helper = sheet.getDataValidationHelper();
		addList(sheet, helper, EquipmentSheetColumns.NETWORK, helper.createExplicitListConstraint(
				values(EquipmentSheetVocabulary.NETWORKS)));
		addList(sheet, helper, EquipmentSheetColumns.TYPE, helper.createExplicitListConstraint(
				values(EquipmentSheetVocabulary.TYPES)));
		addList(sheet, helper, EquipmentSheetColumns.BRAND, helper.createExplicitListConstraint(
				values(EquipmentSheetVocabulary.BRANDS)));
		addList(sheet, helper, EquipmentSheetColumns.USAGE_STATUS, helper.createExplicitListConstraint(
				values(EquipmentSheetVocabulary.USAGE_STATUSES)));
		if (connectorCount > 0) {
			String range = "Listas!$A$2:$A$" + (connectorCount + 1);
			for (EquipmentSheetColumns column : List.of(EquipmentSheetColumns.CONNECTOR_1,
					EquipmentSheetColumns.CONNECTOR_2, EquipmentSheetColumns.CONNECTOR_3)) {
				addList(sheet, helper, column, helper.createFormulaListConstraint(range));
			}
		}
	}

	private static void addList(Sheet sheet, DataValidationHelper helper, EquipmentSheetColumns column,
			DataValidationConstraint constraint) {
		DataValidation validation = helper.createValidation(constraint,
				new CellRangeAddressList(1, VALIDATED_ROWS, column.ordinal(), column.ordinal()));
		validation.setShowErrorBox(true);
		validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
		validation.createErrorBox(column.header(), "Elija un valor de la lista");
		sheet.addValidationData(validation);
	}

	private static void writeInstructions(Sheet sheet, CellStyle header) {
		List<String> notes = List.of(
				"Complete una fila por equipo en la hoja Equipos. Las columnas en morado son obligatorias para un equipo nuevo.",
				"La serie identifica al equipo: si ya existe, la fila actualiza ese equipo y SARC muestra qué cambia para que lo apruebe.",
				"En un equipo existente, una celda vacía conserva el dato actual: la carga masiva nunca borra información.",
				"La red y el tipo de un equipo existente no se cambian. Un equipo eliminado se recupera desde el inventario.",
				"El horario y la imagen no van en la plantilla: se completan después en cada equipo.",
				"Las identidades Enerlink, Wallbox y OCPP de la plantilla quedan verificadas al confirmar la importación.");
		int rowIndex = 0;
		for (String note : notes) {
			sheet.createRow(rowIndex++).createCell(0).setCellValue("• " + note);
		}
		rowIndex++;
		Row title = sheet.createRow(rowIndex++);
		String[] headers = { "Columna", "Obligatoria", "Descripción" };
		for (int index = 0; index < headers.length; index++) {
			title.createCell(index).setCellValue(headers[index]);
			title.getCell(index).setCellStyle(header);
		}
		for (EquipmentSheetColumns column : EquipmentSheetColumns.values()) {
			Row row = sheet.createRow(rowIndex++);
			row.createCell(0).setCellValue(column.header());
			row.createCell(1).setCellValue(column.required() ? "Sí" : "No");
			row.createCell(2).setCellValue(column.hint());
		}
		sheet.setColumnWidth(0, 24 * 256);
		sheet.setColumnWidth(1, 12 * 256);
		sheet.setColumnWidth(2, 90 * 256);
	}

	private static CellStyle headerStyle(XSSFWorkbook workbook, byte[] rgb) {
		Font font = workbook.createFont();
		font.setBold(true);
		font.setColor(IndexedColors.WHITE.getIndex());
		XSSFCellStyle style = workbook.createCellStyle();
		style.setFont(font);
		style.setFillForegroundColor(new XSSFColor(rgb));
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return style;
	}

	private static String[] values(Map<?, String> labels) {
		return labels.values().toArray(String[]::new);
	}
}
