import { Component, effect, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { LucideCircleCheck } from '@lucide/angular';

import { KiaPointResponse, WindowResponse } from '../../../../core/api/generated';
import { Notifier } from '../../../../core/notifications/notifier';
import { OpenWindowsFacade } from '../../application/open-windows-facade';
import { formatInstant } from '../../domain/intermittency-time';
import { CloseWindowDialog } from './close-window-dialog';

/**
 * Ventanas abiertas del punto elegido, con su duracion provisional rotulada como tal: nunca suma a lo oficial
 * hasta que se cierra (FR-012). Desde aqui se registra el fin.
 */
@Component({
  selector: 'sarc-open-windows-panel',
  imports: [MatButtonModule, LucideCircleCheck],
  providers: [OpenWindowsFacade],
  template: `
    @if (point(); as selected) {
      <section
        class="panel"
        aria-label="Ventanas abiertas del punto"
        [attr.aria-busy]="facade.loading()"
      >
        <h2>Ventanas abiertas de {{ selected.name }}</h2>
        @if (facade.error()) {
          <div class="form-alert" role="alert">
            <p>{{ facade.error() }}</p>
          </div>
        } @else if (!facade.loading() && facade.windows().length === 0) {
          <p class="note">Este punto no tiene ventanas abiertas.</p>
        } @else {
          <ul>
            @for (window of facade.windows(); track window.id) {
              <li>
                <div class="info">
                  <span class="badge badge-accent">Abierta</span>
                  <span>Desde {{ clock(window.startAt) }}</span>
                  @if (window.provisionalText) {
                    <span class="muted">Duración provisional: {{ window.provisionalText }}</span>
                  }
                </div>
                <button
                  matButton="tonal"
                  type="button"
                  [attr.aria-label]="
                    'Registrar el fin de la ventana abierta desde ' + clock(window.startAt)
                  "
                  (click)="close(window)"
                >
                  <svg lucideCircleCheck [size]="18" aria-hidden="true"></svg>
                  Registrar fin
                </button>
              </li>
            }
          </ul>
          <p class="note">
            Una ventana abierta bloquea las posteriores del mismo punto hasta que se cierra, y no
            suma al tiempo confirmado.
          </p>
        }
      </section>
    }
  `,
  styles: `
    @use '../../../../shared/ui/badges';
    @use '../../../../shared/ui/dialog-form';

    :host {
      display: block;
    }

    .panel {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      margin-top: var(--sarc-space-4);
      padding: var(--sarc-space-4);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--sarc-radius-md);
      background: var(--mat-sys-surface);
    }

    h2 {
      margin: 0;
      font: var(--mat-sys-title-medium);
    }

    ul {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    li {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--sarc-space-2) var(--sarc-space-3);
      padding: var(--sarc-space-2) 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }

    .info {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-2) var(--sarc-space-3);
    }

    .muted {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class OpenWindowsPanel {
  readonly point = input.required<KiaPointResponse | null>();
  /** Cambia con cada captura guardada: recarga la lista. */
  readonly refresh = input(0);

  protected readonly facade = inject(OpenWindowsFacade);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  constructor() {
    effect(() => {
      const point = this.point();
      this.refresh();
      void this.facade.load(point);
    });
  }

  protected clock(instant: string): string {
    return formatInstant(instant);
  }

  protected close(window: WindowResponse): void {
    this.dialog
      .open(CloseWindowDialog, {
        data: { window, closer: this.facade },
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((closed?: WindowResponse) => {
        if (!closed) return;
        this.notifier.success(
          closed.state === 'REVIEW'
            ? 'Fin registrado. La ventana dura 4 horas o más y quedó por revisar.'
            : 'Fin registrado. La ventana quedó confirmada.',
        );
        void this.facade.load(this.point());
      });
  }
}
