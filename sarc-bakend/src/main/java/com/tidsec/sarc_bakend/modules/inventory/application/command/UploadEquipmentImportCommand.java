package com.tidsec.sarc_bakend.modules.inventory.application.command;

/** Plantilla de carga masiva (XLSX/CSV) con los equipos a registrar o actualizar. */
public record UploadEquipmentImportCommand(String fileName, byte[] content) {
}
