import { Component, inject, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { firstValueFrom } from 'rxjs';

import { ReviewedDayResponse } from '../../../../core/api/generated';
import { Notifier } from '../../../../core/notifications/notifier';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../../shared/ui/confirm-dialog/confirm-dialog';
import { ReviewedDaysFacade } from '../../application/reviewed-days-facade';
import { formatDay, formatInstant } from '../../domain/intermittency-time';
import {
  JustificationDialog,
  JustificationDialogData,
} from '../justification-dialog/justification-dialog';

/**
 * Dias revisados de la red KIA (solo editores, FR-044): al cerrar su turno, un Analista o Administrador marca el
 * dia como revisado. Esa marca acredita la cobertura de la disponibilidad estimada; un dia sin marca queda
 * «sin datos». Hoy y los dias futuros no se pueden marcar. Retirar la marca pide justificacion.
 */
@Component({
  selector: 'sarc-reviewed-days-panel',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  providers: [ReviewedDaysFacade],
  template: `
    <header class="page-header">
      <div>
        <h1>Días revisados</h1>
        <p>
          Marque cada día terminado como revisado para toda la red KIA. Los puntos sin ventanas ese
          día cuentan con 0 minutos fuera de línea; un día sin marca queda sin datos y nunca cuenta
          como 100 % de disponibilidad.
        </p>
      </div>
    </header>

    <section class="period" aria-label="Período">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Mes</mat-label>
        <input
          matInput
          type="month"
          [max]="currentMonth()"
          [value]="facade.month()"
          (change)="onMonth($event)"
        />
      </mat-form-field>
      @if (facade.month()) {
        <button matButton="tonal" type="button" (click)="facade.showRecent()">
          Últimos 31 días
        </button>
      }
      <p class="muted" aria-live="polite">{{ shown() }}</p>
    </section>

    <section class="results" aria-label="Días revisados" [attr.aria-busy]="facade.loading()">
      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" aria-label="Cargando los días" />
      }
      @if (facade.error()) {
        <div class="state" role="alert">
          <p>{{ facade.error() }}</p>
          <button matButton="tonal" type="button" (click)="facade.load()">Reintentar ahora</button>
        </div>
      } @else {
        <ul class="items">
          @for (day of facade.days(); track day.day) {
            <li class="item">
              <div class="info">
                <p>
                  <strong>{{ formatDay(day.day) }}</strong>
                  @if (day.active) {
                    <span class="badge badge-accent">Revisado</span>
                  } @else if (!day.finished) {
                    <span class="badge badge-off">En curso</span>
                  } @else {
                    <span class="badge badge-off">Sin datos</span>
                  }
                </p>
                @if (day.active && day.markedAt) {
                  <p class="muted">Marcado el {{ clock(day.markedAt) }}</p>
                }
                @if (!day.finished) {
                  <p class="muted">Se puede marcar cuando el día termine.</p>
                }
                @if (day.pendingLinkRows > 0) {
                  <p class="muted">
                    <span class="badge badge-warn">{{
                      plural(day.pendingLinkRows, 'fila por vincular', 'filas por vincular')
                    }}</span>
                    dejan este día sin cubrir para toda la red hasta resolverlas.
                  </p>
                }
                @if (day.unconfirmedBatches > 0) {
                  <p class="muted">
                    <span class="badge badge-info">{{
                      plural(day.unconfirmedBatches, 'lote sin confirmar', 'lotes sin confirmar')
                    }}</span>
                    con filas de este día; todavía no afectan la cobertura.
                  </p>
                }
              </div>
              <div class="actions">
                @if (day.active) {
                  <button
                    matButton
                    type="button"
                    [attr.aria-label]="'Retirar la marca del ' + formatDay(day.day)"
                    (click)="withdraw(day)"
                  >
                    Retirar marca
                  </button>
                } @else {
                  <button
                    matButton="filled"
                    type="button"
                    [disabled]="!day.finished"
                    [attr.aria-label]="'Marcar como revisado el ' + formatDay(day.day)"
                    (click)="mark(day)"
                  >
                    Marcar como revisado
                  </button>
                }
              </div>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    @use '../../../../shared/ui/page-chrome';
    @use '../../../../shared/ui/badges';
    @use '../../../../shared/ui/dialog-form';

    :host {
      display: block;
    }

    .period {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-3);
      margin-bottom: var(--sarc-space-4);

      p {
        margin: 0;
      }
    }

    .results {
      overflow: hidden;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--sarc-radius-md);
      background: var(--mat-sys-surface);
    }

    .items {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .item {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--sarc-space-2) var(--sarc-space-4);
      padding: var(--sarc-space-3) var(--sarc-space-4);
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }

    .info {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-1);
      min-width: 0;
      flex: 1 1 320px;

      p {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--sarc-space-1) var(--sarc-space-2);
        margin: 0;
        overflow-wrap: anywhere;
      }
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--sarc-space-2);
    }

    .muted {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class ReviewedDaysPanel implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly facade = inject(ReviewedDaysFacade);
  protected readonly formatDay = formatDay;

  ngOnInit(): void {
    void this.facade.load();
  }

  protected currentMonth(): string {
    return this.facade.today().slice(0, 7);
  }

  /** Que se esta mostrando: los ultimos 31 dias o el mes elegido. */
  protected shown(): string {
    const month = this.facade.month();
    if (!month) return 'Mostrando los últimos 31 días.';
    return `Mostrando ${new Date(`${month}-15T12:00:00`).toLocaleDateString('es-EC', { month: 'long', year: 'numeric' })}.`;
  }

  /** Un mes vacio no es un periodo: el campo vuelve al mes vigente en lugar de quedar en blanco. */
  protected onMonth(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.value) {
      void this.facade.chooseMonth(input.value);
    } else {
      input.value = this.facade.month();
    }
  }

  protected clock(instant: string): string {
    return formatInstant(instant);
  }

  protected plural(count: number, one: string, many: string): string {
    return `${count} ${count === 1 ? one : many}`;
  }

  /** Marca el dia; si hay lotes en vista previa que lo tocan, avisa antes porque todavia no cuentan (FR-045). */
  protected async mark(day: ReviewedDayResponse): Promise<void> {
    if (!day.finished) return;
    if (day.unconfirmedBatches > 0 && !(await this.confirmWarning(day))) return;
    try {
      const { warnings } = await this.facade.mark(day);
      // La lista puede estar vieja: si otro editor subio un lote despues de cargarla, el servidor lo avisa aqui.
      const base = `${formatDay(day.day)} quedó marcado como revisado.`;
      this.notifier.success(
        warnings.length === 0
          ? base
          : `${base} Atención: ${this.plural(warnings.length, 'lote sin confirmar tiene', 'lotes sin confirmar tienen')} ` +
              `filas de este día (${warnings.map((batch) => batch.fileName).join(', ')}); ` +
              'la cobertura puede cambiar al confirmarlos.',
      );
    } catch (error) {
      this.notifier.error(error instanceof Error ? error.message : 'No se pudo marcar el día.');
    }
  }

  protected async withdraw(day: ReviewedDayResponse): Promise<void> {
    const data: JustificationDialogData = {
      title: 'Retirar la marca de revisado',
      action: 'Retirar marca',
      target: formatDay(day.day),
      hint: 'El día vuelve a quedar sin datos y deja de contar en la disponibilidad estimada. El historial se conserva.',
      submit: (justification) => this.facade.withdraw(day, justification),
    };
    const done = await firstValueFrom(
      this.dialog
        .open<JustificationDialog, JustificationDialogData, unknown>(JustificationDialog, {
          data,
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
    if (done) this.notifier.success('Se retiró la marca. El día quedó sin datos.');
  }

  private async confirmWarning(day: ReviewedDayResponse): Promise<boolean> {
    const batches = this.plural(day.unconfirmedBatches, 'lote', 'lotes');
    const data: ConfirmDialogData = {
      title: 'Hay lotes sin confirmar',
      message:
        `${batches} en vista previa tienen filas del ${formatDay(day.day)} y todavía no cuentan. ` +
        'Si se confirman después, la cobertura del día puede cambiar. ¿Marcarlo como revisado igualmente?',
      confirmLabel: 'Marcar como revisado',
    };
    const confirmed = await firstValueFrom(
      this.dialog
        .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
        .afterClosed(),
    );
    return confirmed === true;
  }
}
