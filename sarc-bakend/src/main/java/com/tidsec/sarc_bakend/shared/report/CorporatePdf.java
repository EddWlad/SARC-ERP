package com.tidsec.sarc_bakend.shared.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Documento PDF con la estructura corporativa comun a todos los modulos: encabezado con ambos
 * logos, titulo, fecha de corte, bloques de KPIs, tablas y notas de metodologia, y pie con
 * numeracion. Cada modulo solo decide el contenido.
 */
public final class CorporatePdf {

	private static final Color BRAND = new Color(0x51, 0x43, 0x6E);
	private static final Color BRAND_LIGHT = new Color(0xEE, 0xEB, 0xF5);
	private static final Color MUTED = new Color(0x5F, 0x5B, 0x66);
	private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND);
	private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
	private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND);
	private static final Font KPI_VALUE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND);
	private static final Font KPI_LABEL = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
	private static final Font HEADER_CELL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
	private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
	private static final Font NOTE = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, MUTED);

	private final ByteArrayOutputStream output = new ByteArrayOutputStream();
	private final Document document;

	private CorporatePdf(CorporateBranding branding, String title, String scope, Instant cutOff) {
		this.document = new Document(PageSize.A4.rotate(), 32, 32, 32, 40);
		PdfWriter writer = PdfWriter.getInstance(document, output);
		String generated = "Fecha de corte: " + branding.dateTimeFormatter().format(cutOff) + " ("
				+ branding.zone().getId() + ")";
		writer.setPageEvent(new Footer(title, generated));
		document.addTitle(title);
		document.addCreator("SARC - EOS Tech Company / Autoeastern");
		document.open();
		header(branding, title, scope, generated);
	}

	public static CorporatePdf create(CorporateBranding branding, String title, String scope, Instant cutOff) {
		return new CorporatePdf(branding, title, scope, cutOff);
	}

	public CorporatePdf section(String title) {
		Paragraph paragraph = new Paragraph(title, SECTION);
		paragraph.setSpacingBefore(12);
		paragraph.setSpacingAfter(6);
		document.add(paragraph);
		return this;
	}

	public CorporatePdf kpis(List<Kpi> kpis) {
		PdfPTable table = new PdfPTable(Math.max(1, kpis.size()));
		table.setWidthPercentage(100);
		for (Kpi kpi : kpis) {
			PdfPCell cell = new PdfPCell();
			cell.setBackgroundColor(BRAND_LIGHT);
			cell.setBorderColor(Color.WHITE);
			cell.setBorderWidth(3);
			cell.setPadding(8);
			Paragraph value = new Paragraph(kpi.value(), KPI_VALUE);
			value.setAlignment(Element.ALIGN_CENTER);
			Paragraph label = new Paragraph(kpi.label(), KPI_LABEL);
			label.setAlignment(Element.ALIGN_CENTER);
			cell.addElement(value);
			cell.addElement(label);
			table.addCell(cell);
		}
		document.add(table);
		return this;
	}

	public CorporatePdf table(List<String> headers, List<List<String>> rows, float[] relativeWidths) {
		PdfPTable table = new PdfPTable(relativeWidths);
		table.setWidthPercentage(100);
		table.setHeaderRows(1);
		for (String header : headers) {
			PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_CELL));
			cell.setBackgroundColor(BRAND);
			cell.setPadding(5);
			table.addCell(cell);
		}
		boolean shaded = false;
		for (List<String> row : rows) {
			for (String value : row) {
				PdfPCell cell = new PdfPCell(new Phrase(value, BODY));
				cell.setPadding(4);
				cell.setBorderColor(BRAND_LIGHT);
				if (shaded) {
					cell.setBackgroundColor(new Color(0xF8, 0xF7, 0xFB));
				}
				table.addCell(cell);
			}
			shaded = !shaded;
		}
		document.add(table);
		return this;
	}

	public CorporatePdf note(String text) {
		Paragraph paragraph = new Paragraph(text, NOTE);
		paragraph.setSpacingBefore(4);
		document.add(paragraph);
		return this;
	}

	public byte[] finish() {
		document.close();
		return output.toByteArray();
	}

	private void header(CorporateBranding branding, String title, String scope, String generated) {
		PdfPTable header = new PdfPTable(new float[] { 2, 6, 2 });
		header.setWidthPercentage(100);
		header.addCell(logoCell(branding.eosLogo(), Element.ALIGN_LEFT));
		PdfPCell center = new PdfPCell();
		center.setBorder(Rectangle.NO_BORDER);
		Paragraph titleParagraph = new Paragraph(title, TITLE);
		titleParagraph.setAlignment(Element.ALIGN_CENTER);
		Paragraph scopeParagraph = new Paragraph(scope, SUBTITLE);
		scopeParagraph.setAlignment(Element.ALIGN_CENTER);
		Paragraph generatedParagraph = new Paragraph(generated, SUBTITLE);
		generatedParagraph.setAlignment(Element.ALIGN_CENTER);
		center.addElement(titleParagraph);
		center.addElement(scopeParagraph);
		center.addElement(generatedParagraph);
		header.addCell(center);
		header.addCell(logoCell(branding.autoeasternLogo(), Element.ALIGN_RIGHT));
		header.setSpacingAfter(8);
		document.add(header);
	}

	private static PdfPCell logoCell(byte[] logo, int alignment) {
		try {
			Image image = Image.getInstance(logo);
			image.scaleToFit(110, 42);
			PdfPCell cell = new PdfPCell(image, false);
			cell.setBorder(Rectangle.NO_BORDER);
			cell.setHorizontalAlignment(alignment);
			cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			return cell;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static final class Footer extends PdfPageEventHelper {

		private final String title;
		private final String generated;

		Footer(String title, String generated) {
			this.title = title;
			this.generated = generated;
		}

		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			float y = document.bottom() - 18;
			ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
					new Phrase(title + " - " + generated, NOTE), document.left(), y, 0);
			ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
					new Phrase("Página " + writer.getPageNumber(), NOTE), document.right(), y, 0);
		}
	}
}
