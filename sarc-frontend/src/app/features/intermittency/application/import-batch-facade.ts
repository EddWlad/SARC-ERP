import { inject, Service } from '@angular/core';
import { firstValueFrom, throwError } from 'rxjs';

import {
  EditImportRowRequest,
  ImportRowDecisionRequest,
  RowChangeResponse,
} from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { ImportBatchState } from '../../../shared/imports/import-batch-state';
import { IntermittencyImportsApi } from '../data-access/intermittency-imports-api';
import { IntermittencyImportBatch, IntermittencyImportRow } from '../domain/intermittency-import';
import { RowClassification } from '../domain/intermittency-labels';

/** Las decisiones de intermitencias son por fila, con su propio cuerpo: no hay decision masiva. */
export type NoBulkDecision = never;

/**
 * Vista previa y seguimiento de un lote de intermitencias. El ciclo (interpretar, revisar, confirmar) es el comun
 * a las importaciones; lo propio son las correcciones, la vinculacion y las decisiones por fila (FR-026 a FR-029).
 * Cada cambio devuelve la fila y los conteos actualizados, y recarga la pagina visible: una edicion puede
 * reclasificar otras filas del mismo punto.
 */
@Service({ autoProvided: false })
export class IntermittencyImportBatchFacade extends ImportBatchState<
  IntermittencyImportBatch,
  IntermittencyImportRow,
  RowClassification,
  NoBulkDecision
> {
  private readonly imports: IntermittencyImportsApi;

  constructor() {
    const api = inject(IntermittencyImportsApi);
    super({
      batchUrl: (id) => api.batchUrl(id),
      rowsRequest: (id, classification, page, size) =>
        api.rowsRequest(id, classification, page, size),
      decide: () => throwError(() => new Error('Las decisiones de intermitencias son por fila')),
      confirm: (batch) => api.confirm(batch),
      retry: (batch) => api.retry(batch),
      discard: (batch) => api.discard(batch),
    });
    this.imports = api;
  }

  /** Corrige la fila en la vista previa. Falla con el mensaje del servidor. */
  editRow(
    row: IntermittencyImportRow,
    body: Omit<EditImportRowRequest, 'version'>,
  ): Promise<RowChangeResponse> {
    return this.perform((batch) => this.imports.editRow(batch, row, body));
  }

  linkRow(row: IntermittencyImportRow, equipmentId: string): Promise<RowChangeResponse> {
    return this.perform((batch) => this.imports.linkRow(batch, row, equipmentId));
  }

  decideRow(
    row: IntermittencyImportRow,
    body: Omit<ImportRowDecisionRequest, 'version'>,
  ): Promise<RowChangeResponse> {
    return this.perform((batch) => this.imports.decideRow(batch, row, body));
  }

  private async perform(
    request: (batch: IntermittencyImportBatch) => ReturnType<IntermittencyImportsApi['editRow']>,
  ): Promise<RowChangeResponse> {
    const batch = this.batch();
    if (batch === undefined) throw new Error('El lote aun no esta cargado');
    this.busy.set(true);
    try {
      const changed = await firstValueFrom(request(batch));
      this.reload();
      return changed;
    } catch (error) {
      // Una version desactualizada se arregla recargando; el mensaje llega tal cual del servidor.
      throw new Error(toProblem(error).detail, { cause: error });
    } finally {
      this.busy.set(false);
    }
  }
}
