import { inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { FileSaver } from '../../../core/files/file-saver';
import { ImportHistoryState } from '../../../shared/imports/import-history-state';
import { EquipmentImportsApi } from '../data-access/equipment-imports-api';
import { EquipmentImportBatch } from '../domain/equipment-import';

/** Historial de cargas masivas de equipos, plantilla y carga de nuevos lotes (archivo o pegado). */
@Service({ autoProvided: false })
export class EquipmentImportsFacade extends ImportHistoryState<
  EquipmentImportBatch,
  EquipmentImportsApi
> {
  private readonly fileSaver = inject(FileSaver);

  readonly downloadingTemplate = signal(false);

  constructor() {
    super(inject(EquipmentImportsApi));
  }

  /** Si falla, rechaza con el `Problem` del backend. */
  async downloadTemplate(): Promise<void> {
    this.downloadingTemplate.set(true);
    try {
      await this.fileSaver.download(this.api.downloadTemplate(), 'plantilla-carga-equipos.xlsx');
    } finally {
      this.downloadingTemplate.set(false);
    }
  }

  /** El lote queda interpretandose en segundo plano; su pantalla muestra el avance. */
  upload(file: File): Promise<EquipmentImportBatch> {
    return firstValueFrom(this.api.upload(file));
  }

  paste(text: string): Promise<EquipmentImportBatch> {
    return firstValueFrom(this.api.paste(text));
  }
}
