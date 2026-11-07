package com.tidsec.sarc_bakend.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;

/** Datos sinteticos006; solo pruebas con PostgreSQL desechable. */
public final class Feature006Fixtures {
    public static final LocalDate REFERENCE = LocalDate.of(2026, 10, 26);
    public static final Instant START = Instant.parse("2026-10-28T13:06:20Z");
    public static final Instant END = Instant.parse("2026-11-04T13:06:20Z");
    public static final UUID CONNECTOR_1 = Feature005Fixtures.connectorId(Feature005Fixtures.JAC_STATION, 1);
    public static final UUID CONNECTOR_2 = Feature005Fixtures.connectorId(Feature005Fixtures.JAC_STATION, 2);
    public static final byte[] ARCHIVED = "Informe sintetico anterior006; corte fijo".getBytes(StandardCharsets.UTF_8);
    private Feature006Fixtures() {}
    public static void seedInventory(JdbcTemplate jdbc) { Feature005Fixtures.seedInventory(jdbc); }
    public static byte[] weeklyWorkbook() throws IOException {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            String[] headers = {"Cargador", "Conector", "Uptime (%)", "Disponible (hrs)", "En falla (hrs)", "Fuera de linea (hrs)"};
            for (String name : new String[]{"Uptime Electrolineras", "Uptime Ptos. Carga"}) {
                var sheet = book.createSheet(name);
                var header = sheet.createRow(0);
                for (int c = 0; c < headers.length; c++) header.createCell(c).setCellValue(headers[c]);
                for (int n = 1; n <= 2; n++) {
                    var row = sheet.createRow(n);
                    row.createCell(0).setCellValue(name.contains("Electrolineras") ? "QA005 JAC Estación" : "QA005 JAC Punto");
                    row.createCell(1).setCellValue(n);
                    row.createCell(2).setCellValue(n == 1 ? 0 : 100);
                    row.createCell(3).setCellValue(163.5833);
                    row.createCell(4).setCellValue(0);
                    row.createCell(5).setCellValue(3.9);
                }
            }
            book.write(out);return out.toByteArray();
        }
    }
    public static UUID seedVisualMeasurements(JdbcTemplate jdbc) {
        for(boolean reference:new boolean[]{false,true}) {
            UUID id=UUID.randomUUID();
            jdbc.update("""
                INSERT INTO upt_measurement(id,connector_id,equipment_id,source,period_zone,period_start,period_end,
                    latest_revision,effective_revision,period_precision,reference_week,reference_period_type,created_at,updated_at,version)
                VALUES(?,?,?,'ENERLINK','America/Guayaquil',?,?,1,1,?,'2026-10-26','ROLLING_7D',now(),now(),0)
                """,id,CONNECTOR_1,Feature005Fixtures.JAC_STATION,reference?null:java.sql.Timestamp.from(START),reference?null:java.sql.Timestamp.from(END),reference?"REFERENCE_WEEK":"KNOWN_RANGE");
            jdbc.update("""
                INSERT INTO upt_measurement_revision(id,measurement_id,revision,reading_class,period_type,available_minutes,
                    occupied_minutes,fault_minutes,offline_minutes,reported_percent,uptime_basis,review_state,observations,origin,
                    equipment_label,connector_type_label,entered_at,validated_at)
                VALUES(?,?,1,'MEASURED','ROLLING_7D',9815,?,0,234,97.6800,?,'VALIDATED','[]','FILE','Nombre anterior del equipo','Tipo2',now(),now())
                """,UUID.randomUUID(),id,reference?null:31,reference?"REPORTED_PERCENT":"STATES");
        }
        UUID pending=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO upt_measurement(id,connector_id,equipment_id,source,period_zone,latest_revision,
                period_precision,reference_week,reference_period_type,created_at,updated_at,version)
            VALUES(?,?,?,'ENERLINK','America/Guayaquil',1,'REQUIRES_RECONCILIATION','2026-10-26','ROLLING_7D',now(),now(),0)
            """,pending,Feature005Fixtures.connectorId(Feature005Fixtures.JAC_POINT,1),Feature005Fixtures.JAC_POINT);
        jdbc.update("""
            INSERT INTO upt_measurement_revision(id,measurement_id,revision,reading_class,period_type,available_minutes,
                fault_minutes,offline_minutes,reported_percent,uptime_basis,review_state,observations,origin,equipment_label,connector_type_label,entered_at)
            VALUES(?,?,1,'MEASURED','ROLLING_7D',9815,0,234,97.68,'REPORTED_PERCENT','PENDING','[]','FILE','Nombre anterior','Tipo2',now())
            """,UUID.randomUUID(),pending);
        return pending;
    }

}
