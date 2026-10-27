import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, Signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { LucideCircleAlert } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { toProblem } from '../../../core/http/problem';
import { Notifier } from '../../../core/notifications/notifier';
import { ConfirmDialog, ConfirmDialogData } from '../../ui/confirm-dialog/confirm-dialog';
import { canDiscard, canRetry, ImportBatchLike, ORIGIN_LABELS } from '../import-batch';
import { ImportStatusBadge } from '../import-status-badge';

/** Lo que la cabecera necesita del estado de un lote (lo cumple `ImportBatchState`). */
export interface ImportBatchControls {
  readonly batch: Signal<ImportBatchLike | undefined>;
  readonly busy: Signal<boolean>;
  readonly inProgress: Signal<boolean>;
  readonly reviewable: Signal<boolean>;
  readonly canConfirm: Signal<boolean>;
  confirm(): Promise<unknown>;
  retry(): Promise<unknown>;
  discard(): Promise<unknown>;
  reload(): void;
}

/**
 * Cabecera de la vista previa de un lote: archivo, origen, etapa, avisos de progreso, fallo o fin,
 * y las acciones Confirmar, Descartar y Reintentar con su dialogo de confirmacion.
 */
@Component({
  selector: 'sarc-import-batch-header',
  imports: [DatePipe, MatButtonModule, MatProgressBarModule, LucideCircleAlert, ImportStatusBadge],
  templateUrl: './import-batch-header.html',
  styleUrl: './import-batch-header.scss',
})
export class ImportBatchHeader {
  readonly state = input.required<ImportBatchControls>();
  /** Que es el lote, antes del origen (p. ej. "Enerlink" o "Equipos"). */
  readonly kind = input.required<string>();
  /** Que hace confirmar, en el dialogo de confirmacion. */
  readonly confirmMessage = input.required<string>();
  /** Aviso mientras se aplican las filas aprobadas. */
  readonly applyingMessage = input.required<string>();
  /** Aviso al terminar (p. ej. cuantas filas se aplicaron). */
  readonly completedMessage = input.required<string>();

  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly originLabels = ORIGIN_LABELS;
  protected readonly canDiscard = computed(() => {
    const batch = this.state().batch();
    return batch !== undefined && canDiscard(batch);
  });
  protected readonly canRetry = computed(() => {
    const batch = this.state().batch();
    return batch !== undefined && canRetry(batch);
  });

  protected async confirm(): Promise<void> {
    const confirmed = await this.ask({
      title: 'Confirmar importación',
      message: this.confirmMessage(),
      confirmLabel: 'Confirmar',
    });
    if (confirmed) {
      await this.run(() => this.state().confirm(), 'Aplicando las filas aprobadas...');
    }
  }

  protected async retry(): Promise<void> {
    await this.run(() => this.state().retry(), 'Reanudando la importación...');
  }

  protected async discard(): Promise<void> {
    const confirmed = await this.ask({
      title: 'Descartar importación',
      message: 'El lote se cierra sin incorporar nada. El archivo original se conserva.',
      confirmLabel: 'Descartar',
    });
    if (confirmed) {
      await this.run(() => this.state().discard(), 'Importación descartada.');
    }
  }

  private async ask(data: ConfirmDialogData): Promise<boolean> {
    return (await firstValueFrom(this.dialog.open(ConfirmDialog, { data }).afterClosed())) === true;
  }

  private async run(operation: () => Promise<unknown>, success: string): Promise<void> {
    try {
      await operation();
      this.notifier.success(success);
    } catch (error) {
      const problem = toProblem(error);
      this.notifier.error(problem.detail);
      if (problem.code === 'CONCURRENT_MODIFICATION') {
        this.state().reload();
      }
    }
  }
}
