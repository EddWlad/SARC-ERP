import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';

import { ConfirmDialog, ConfirmDialogData } from './confirm-dialog/confirm-dialog';

/** Cierre de un dialogo con formulario que respeta los cambios sin guardar. */
export interface UnsavedChangesGuard {
  /** Cancelar: cierra directo si no hay cambios; si los hay, pide confirmacion. */
  cancel(): Promise<void>;
}

const DISCARD: ConfirmDialogData = {
  title: 'Descartar cambios',
  message: 'Hay cambios sin guardar en el formulario. ¿Desea descartarlos?',
  confirmLabel: 'Descartar',
};

/**
 * Evita perder un formulario modificado: Esc, clic fuera del dialogo y Cancelar piden confirmacion,
 * y el navegador avisa antes de cerrar o recargar la pestaña. Llamar en el constructor del dialogo.
 */
export function guardUnsavedChanges(dirty: () => boolean): UnsavedChangesGuard {
  const ref = inject(MatDialogRef);
  const dialog = inject(MatDialog);
  const window = inject(DOCUMENT).defaultView;
  let asking = false;

  const cancel = async (): Promise<void> => {
    if (!dirty()) {
      ref.close();
      return;
    }
    if (asking) {
      return;
    }
    asking = true;
    try {
      const discard = await firstValueFrom(
        dialog.open(ConfirmDialog, { data: DISCARD }).afterClosed(),
      );
      if (discard === true) {
        ref.close();
      }
    } finally {
      asking = false;
    }
  };

  ref.disableClose = true;
  ref.backdropClick().subscribe(() => void cancel());
  ref.keydownEvents().subscribe((event) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      void cancel();
    }
  });

  const beforeUnload = (event: BeforeUnloadEvent) => {
    if (dirty()) {
      event.preventDefault();
    }
  };
  window?.addEventListener('beforeunload', beforeUnload);
  inject(DestroyRef).onDestroy(() => window?.removeEventListener('beforeunload', beforeUnload));

  return { cancel };
}
