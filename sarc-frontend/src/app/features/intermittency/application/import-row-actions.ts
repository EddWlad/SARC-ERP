import {
  EditImportRowRequest,
  ImportRowDecisionRequest,
  RowChangeResponse,
} from '../../../core/api/generated';
import { IntermittencyImportRow } from '../domain/intermittency-import';

/**
 * Lo que los dialogos de la vista previa necesitan de la fachada del lote: corregir, vincular y decidir una fila.
 * Llega por datos porque los dialogos viven fuera del arbol que provee la fachada.
 */
export interface ImportRowActions {
  editRow(
    row: IntermittencyImportRow,
    body: Omit<EditImportRowRequest, 'version'>,
  ): Promise<RowChangeResponse>;
  /** Devuelve la fila ya vinculada; en filas de lotes confirmados el vinculo se aplica al resolverla. */
  linkRow(
    row: IntermittencyImportRow,
    equipmentId: string,
  ): Promise<Pick<RowChangeResponse, 'row'>>;
  decideRow(
    row: IntermittencyImportRow,
    body: Omit<ImportRowDecisionRequest, 'version'>,
  ): Promise<RowChangeResponse>;
}

/** Datos con los que se abre un dialogo sobre una fila. */
export interface ImportRowDialogData {
  readonly row: IntermittencyImportRow;
  readonly actions: ImportRowActions;
  /**
   * La fila es pendiente de un lote ya confirmado: se resuelve de una vez (incorporar u omitir con motivo) y
   * dejarla pendiente ya no es una opcion.
   */
  readonly resolving?: boolean;
}
