import { Component, inject, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { LucideFlaskConical } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { PendingRowResponse, WindowResponse } from '../../../../core/api/generated';
import { Notifier } from '../../../../core/notifications/notifier';
import { ImportRowDialogData } from '../../application/import-row-actions';
import { ReviewFacade, ReviewSection } from '../../application/review-facade';
import {
  CLASSIFICATION_LABELS,
  REASON_LABELS,
  STATE_BADGES,
  STATE_LABELS,
} from '../../domain/intermittency-labels';
import { durationText, formatInstant, present } from '../../domain/intermittency-time';
import {
  CloseWindowDialog,
  CloseWindowDialogData,
} from '../open-windows-panel/close-window-dialog';
import { CorrectionForm, CorrectionFormData, isClosed } from '../correction-form/correction-form';
import {
  JustificationDialog,
  JustificationDialogData,
} from '../justification-dialog/justification-dialog';
import { PointTestDialog, PointTestDialogData } from '../point-test-dialog/point-test-dialog';
import { RowDecisionPanel } from '../row-decision-panel/row-decision-panel';

const SECTIONS: readonly { value: ReviewSection; label: string }[] = [
  { value: 'REVIEW', label: 'Por revisar' },
  { value: 'OPEN', label: 'Abiertas' },
  { value: 'TEST', label: 'Prueba' },
  { value: 'PENDING', label: 'Pendientes de lotes' },
];

const EMPTY: Readonly<Record<ReviewSection, string>> = {
  REVIEW: 'No hay ventanas por revisar.',
  OPEN: 'No hay ventanas abiertas.',
  TEST: 'No hay ventanas de prueba.',
  PENDING: 'No hay filas pendientes de lotes confirmados.',
};

/** Accion sobre una ventana que pide justificacion. */
type WindowAction = 'confirm' | 'annul' | 'mark' | 'unmark';

const ACTIONS: Readonly<
  Record<WindowAction, { title: string; button: string; done: string; hint?: string }>
> = {
  confirm: {
    title: 'Confirmar la revisión',
    button: 'Confirmar',
    done: 'Revisión confirmada: la ventana ya cuenta en lo oficial.',
  },
  annul: {
    title: 'Anular la ventana',
    button: 'Anular',
    done: 'Ventana anulada. Su historial se conserva.',
    hint: 'Una ventana anulada deja de contar y libera su horario; el historial no se borra.',
  },
  mark: {
    title: 'Marcar como prueba',
    button: 'Marcar prueba',
    done: 'Ventana marcada como prueba.',
    hint: 'Una ventana de prueba no suma a los indicadores oficiales.',
  },
  unmark: {
    title: 'Quitar la prueba',
    button: 'Quitar prueba',
    done: 'Se quitó la prueba. La ventana vuelve a contar según su duración.',
  },
};

/**
 * Revision de lo que todavia no es oficial (solo editores): ventanas por revisar, abiertas y de prueba, y
 * filas pendientes de lotes confirmados. Cada cambio pide justificacion y conserva el historial (FR-017,
 * FR-021a, FR-028). Las ventanas largas muestran la duracion junto a sus instantes completos.
 */
@Component({
  selector: 'sarc-intermittency-review-page',
  imports: [MatButtonModule, MatButtonToggleModule, MatProgressBarModule, LucideFlaskConical],
  providers: [ReviewFacade],
  template: `
    <header class="page-header">
      <div>
        <h1>Revisión</h1>
        <p>
          Lo que aún no es oficial: ventanas por revisar, abiertas o de prueba, y filas de
          importaciones que quedaron pendientes. Cada cambio lleva justificación y conserva el
          historial.
        </p>
      </div>
      <div class="header-actions">
        <button matButton="tonal" type="button" (click)="pointTest()">
          <svg lucideFlaskConical [size]="18" aria-hidden="true"></svg>
          Marcar prueba por punto
        </button>
      </div>
    </header>

    <mat-button-toggle-group
      class="sections"
      aria-label="Sección de la revisión"
      [value]="facade.section()"
      (change)="facade.select($event.value)"
    >
      @for (section of sections; track section.value) {
        <mat-button-toggle [value]="section.value">
          {{ section.label }} ({{ facade.counts()[section.value] }})
        </mat-button-toggle>
      }
    </mat-button-toggle-group>

    <section class="results" aria-label="Elementos por revisar" [attr.aria-busy]="facade.loading()">
      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" aria-label="Cargando la revisión" />
      }
      @if (facade.error()) {
        <div class="state" role="alert">
          <p>{{ facade.error() }}</p>
          <button matButton="tonal" type="button" (click)="facade.load()">Reintentar ahora</button>
        </div>
      } @else if (facade.empty()) {
        <div class="state">
          <p>{{ empty[facade.section()] }}</p>
        </div>
      } @else if (facade.section() === 'PENDING') {
        <ul class="items">
          @for (item of facade.pending(); track item.row.id) {
            <li class="item">
              <div class="info">
                <p>
                  <strong>{{ item.fileName || 'Lote sin nombre' }}</strong> · fila
                  {{ item.row.rowNumber }}
                  <span class="badge badge-warn">{{
                    classifications[item.row.classification]
                  }}</span>
                </p>
                <p class="muted">
                  Confirmado el {{ clock(item.batchCreatedAt) }}
                  @for (issue of item.row.interpretation.rowIssues; track issue.code) {
                    · {{ issue.message }}
                  }
                </p>
              </div>
              <button
                matButton="tonal"
                type="button"
                [attr.aria-label]="
                  'Resolver la fila ' + item.row.rowNumber + ' de ' + item.fileName
                "
                (click)="resolve(item)"
              >
                Resolver
              </button>
            </li>
          }
        </ul>
      } @else {
        <ul class="items">
          @for (window of facade.windows(); track window.id) {
            <li class="item">
              <div class="info">
                <p>
                  <strong>{{ window.pointLabel }}</strong>
                  <span [class]="badges[window.state]">{{ stateLabels[window.state] }}</span>
                  @for (reason of window.reviewReasons; track reason) {
                    <span class="muted">{{ reasons[reason] }}</span>
                  }
                </p>
                <p>
                  {{ clock(window.startAt) }} →
                  {{ window.endAt ? clock(window.endAt) : 'abierta' }}
                  <strong class="duration">{{ duration(window) }}</strong>
                </p>
                @if (window.comment) {
                  <p class="muted">{{ window.comment }}</p>
                }
              </div>
              <div class="actions">
                @if (window.state === 'OPEN') {
                  <button matButton="tonal" type="button" (click)="close(window)">
                    Registrar fin
                  </button>
                } @else {
                  @if (window.state === 'REVIEW') {
                    <button matButton="filled" type="button" (click)="act(window, 'confirm')">
                      Confirmar
                    </button>
                    <button matButton="tonal" type="button" (click)="act(window, 'mark')">
                      Marcar prueba
                    </button>
                  }
                  @if (window.state === 'TEST') {
                    <button matButton="tonal" type="button" (click)="act(window, 'unmark')">
                      Quitar prueba
                    </button>
                  }
                  <button matButton type="button" (click)="correct(window)">Corregir</button>
                }
                <button matButton type="button" (click)="act(window, 'annul')">Anular</button>
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

    .sections {
      display: flex;
      flex-wrap: wrap;
      width: fit-content;
      max-width: 100%;
      margin-bottom: var(--sarc-space-4);
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

    .duration {
      margin-inline-start: var(--sarc-space-2);
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
export class IntermittencyReviewPage implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly facade = inject(ReviewFacade);
  protected readonly sections = SECTIONS;
  protected readonly empty = EMPTY;
  protected readonly badges = STATE_BADGES;
  protected readonly stateLabels = STATE_LABELS;
  protected readonly reasons = REASON_LABELS;
  protected readonly classifications = CLASSIFICATION_LABELS;

  ngOnInit(): void {
    void this.facade.load();
  }

  protected clock(instant: string): string {
    return formatInstant(instant);
  }

  /** Una ventana larga muestra la duracion junto a sus instantes completos (FR-021a). */
  protected duration(window: WindowResponse): string {
    const minutes = window.endAt ? window.durationMinutes : window.provisionalMinutes;
    if (!present(minutes)) return '';
    return `${durationText(minutes)} (${minutes} min)${window.endAt ? '' : ' provisional'}`;
  }

  protected async act(window: WindowResponse, action: WindowAction): Promise<void> {
    const config = ACTIONS[action];
    const submit = (justification: string) => {
      switch (action) {
        case 'confirm':
          return this.facade.confirmReview(window, justification);
        case 'annul':
          return this.facade.annul(window, justification);
        case 'mark':
          return this.facade.markTest(window, justification);
        case 'unmark':
          return this.facade.unmarkTest(window, justification);
      }
    };
    const data: JustificationDialogData = {
      title: config.title,
      action: config.button,
      target: `${window.pointLabel} · ${this.clock(window.startAt)}`,
      ...(config.hint ? { hint: config.hint } : {}),
      submit,
    };
    const done = await firstValueFrom(
      this.dialog
        .open<JustificationDialog, JustificationDialogData, unknown>(JustificationDialog, {
          data,
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
    if (done) this.notifier.success(config.done);
  }

  protected async correct(window: WindowResponse): Promise<void> {
    // Solo se corrigen ventanas cerradas; el boton ya no se ofrece para una abierta.
    if (!isClosed(window)) return;
    const data: CorrectionFormData = {
      window,
      submit: (body) => this.facade.correct(window, body),
    };
    const done = await firstValueFrom(
      this.dialog
        .open<CorrectionForm, CorrectionFormData, unknown>(CorrectionForm, {
          data,
          width: '640px',
          maxWidth: '95vw',
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
    if (done) this.notifier.success('Corrección guardada. Se conservó la versión anterior.');
  }

  protected async close(window: WindowResponse): Promise<void> {
    const data: CloseWindowDialogData = { window, closer: this.facade };
    const closed = await firstValueFrom(
      this.dialog
        .open<CloseWindowDialog, CloseWindowDialogData, WindowResponse>(CloseWindowDialog, {
          data,
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
    if (closed) {
      this.notifier.success(
        closed.state === 'REVIEW'
          ? 'Fin registrado. La ventana dura 4 horas o más y quedó por revisar.'
          : 'Fin registrado. La ventana quedó confirmada.',
      );
    }
  }

  protected async pointTest(): Promise<void> {
    const data: PointTestDialogData = { actions: this.facade };
    await firstValueFrom(
      this.dialog
        .open<PointTestDialog, PointTestDialogData, boolean>(PointTestDialog, {
          data,
          width: '640px',
          maxWidth: '95vw',
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
  }

  protected async resolve(item: PendingRowResponse): Promise<void> {
    const data: ImportRowDialogData = {
      row: item.row,
      actions: this.facade.rowActions(item),
      resolving: true,
    };
    const row = await firstValueFrom(
      this.dialog
        .open<RowDecisionPanel, ImportRowDialogData, unknown>(RowDecisionPanel, {
          data,
          width: '720px',
          maxWidth: '95vw',
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
    if (row) this.notifier.success('Fila resuelta.');
  }
}
