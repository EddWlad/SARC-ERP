import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ImportHistoryState } from '../../../shared/imports/import-history-state';
import { UptimeImportsApi } from '../data-access/uptime-imports-api';
import { DeclaredPeriodType, UptimeImportBatch } from '../domain/uptime-import';

/** Historial de lotes de uptime y carga de uno nuevo: informe semanal en archivo o filas pegadas. */
@Service({ autoProvided: false })
export class UptimeImportHistory extends ImportHistoryState<UptimeImportBatch, UptimeImportsApi> {
  constructor() {
    super(inject(UptimeImportsApi));
  }

  upload(weekStart: string, file: File): Promise<UptimeImportBatch> {
    return firstValueFrom(this.api.upload(weekStart, file));
  }

  paste(type: DeclaredPeriodType, text: string): Promise<UptimeImportBatch> {
    return firstValueFrom(this.api.paste(type, text));
  }
}
