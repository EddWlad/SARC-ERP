import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ImportHistoryState } from '../../../shared/imports/import-history-state';
import { IntermittencyImportsApi } from '../data-access/intermittency-imports-api';
import { IntermittencyImportBatch } from '../domain/intermittency-import';

/** Historial de lotes de intermitencias y carga de uno nuevo: archivo XLSX o CSV, o filas pegadas. */
@Service({ autoProvided: false })
export class IntermittencyImportHistory extends ImportHistoryState<
  IntermittencyImportBatch,
  IntermittencyImportsApi
> {
  constructor() {
    super(inject(IntermittencyImportsApi));
  }

  upload(file: File): Promise<IntermittencyImportBatch> {
    return firstValueFrom(this.api.upload(file));
  }

  paste(text: string): Promise<IntermittencyImportBatch> {
    return firstValueFrom(this.api.paste(text));
  }
}
