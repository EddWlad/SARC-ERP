package com.tidsec.sarc_bakend.shared.report;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Identidad corporativa de los reportes (PRD RF-RPT-03): logos de EOS Tech Company y Autoeastern,
 * zona horaria de presentacion y formato de fechas. Los logos se recortan al cargarlos para que el
 * margen vacio de los archivos originales no los empequenezca en el encabezado.
 */
@Component
public class CorporateBranding {

	private final byte[] eosLogo;
	private final byte[] autoeasternLogo;
	private final ZoneId zone;
	private final DateTimeFormatter dateTimeFormatter;

	CorporateBranding(OperatingZone operatingZone) {
		this.eosLogo = loadTrimmed("reports/logos/eos-tech.png");
		this.autoeasternLogo = loadTrimmed("reports/logos/autoeastern.jpg");
		this.zone = operatingZone.zone();
		this.dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-EC"))
				.withZone(this.zone);
	}

	public byte[] eosLogo() {
		return eosLogo.clone();
	}

	public byte[] autoeasternLogo() {
		return autoeasternLogo.clone();
	}

	public ZoneId zone() {
		return zone;
	}

	public DateTimeFormatter dateTimeFormatter() {
		return dateTimeFormatter;
	}

	private static byte[] loadTrimmed(String path) {
		try (InputStream input = new ClassPathResource(path).getInputStream()) {
			BufferedImage image = ImageIO.read(input);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(trim(image), "png", output);
			return output.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("No se pudo cargar el logo " + path, ex);
		}
	}

	/** Recorta los bordes transparentes o casi blancos. */
	private static BufferedImage trim(BufferedImage image) {
		int minX = image.getWidth();
		int minY = image.getHeight();
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (isContent(image.getRGB(x, y))) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		return maxX < 0 ? image : image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	private static boolean isContent(int argb) {
		int alpha = argb >>> 24;
		int red = (argb >> 16) & 0xFF;
		int green = (argb >> 8) & 0xFF;
		int blue = argb & 0xFF;
		return alpha > 24 && (red < 235 || green < 235 || blue < 235);
	}
}
