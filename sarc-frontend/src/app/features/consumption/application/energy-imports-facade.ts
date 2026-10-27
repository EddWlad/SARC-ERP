import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ImportHistoryState } from '../../../shared/imports/import-history-state';
import { EnergyImportsApi } from '../data-access/energy-imports-api';
import { SessionSource } from '../domain/consumption';
import { ImportBatch } from '../domain/energy-import';

/** Historial de importaciones de sesiones y carga de nuevos lotes (archivo o pegado). */
@Service({ autoProvided: false })
export class EnergyImportsFacade extends ImportHistoryState<ImportBatch, EnergyImportsApi> {
  constructor() {
    super(inject(EnergyImportsApi));
  }

  /** El lote queda interpretandose en segundo plano; su pantalla muestra el avance. */
  upload(source: SessionSource, file: File): Promise<ImportBatch> {
    return firstValueFrom(this.api.upload(source, file));
  }

  paste(source: SessionSource, text: string): Promise<ImportBatch> {
    return firstValueFrom(this.api.paste(source, text));
  }
}
