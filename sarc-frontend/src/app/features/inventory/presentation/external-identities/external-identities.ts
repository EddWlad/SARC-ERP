import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { LucideBadgeCheck, LucidePlus, LucideTrash2 } from '@lucide/angular';

import { ExternalIdentity, PLATFORM_LABELS } from '../../domain/external-identity';

/**
 * Identidades del equipo en Wallbox, Enerlink y OCPP para conciliar importaciones (RF-002.10).
 * Una identidad verificada ya no se puede quitar: las importaciones dependen de ella.
 */
@Component({
  selector: 'sarc-external-identities',
  imports: [DatePipe, MatButtonModule, LucidePlus, LucideBadgeCheck, LucideTrash2],
  template: `
    @if (identities().length === 0) {
      <p class="empty">Sin identidades externas registradas.</p>
    } @else {
      <ul class="list">
        @for (identity of identities(); track identity.id) {
          <li>
            <div>
              <strong>{{ platformLabels[identity.platform] }}</strong>
              <span class="id">{{ identity.externalId }}</span>
              <span class="muted">
                @if (identity.scope) {
                  Ámbito {{ identity.scope }}
                }
                @if (identity.alias) {
                  · Alias {{ identity.alias }}
                }
              </span>
            </div>
            <div class="status">
              @if (identity.verified) {
                <span class="badge" [title]="'Verificada ' + (identity.verifiedAt | date: 'short')">
                  <svg lucideBadgeCheck [size]="14" aria-hidden="true"></svg>
                  Verificada
                </span>
              } @else {
                <span class="badge badge-off">Sin verificar</span>
                @if (editable()) {
                  <button matButton type="button" (click)="verify.emit(identity)">Verificar</button>
                  <button
                    matIconButton
                    type="button"
                    [attr.aria-label]="'Quitar identidad ' + identity.externalId"
                    (click)="remove.emit(identity)"
                  >
                    <svg lucideTrash2 [size]="18"></svg>
                  </button>
                }
              }
            </div>
          </li>
        }
      </ul>
    }
    @if (editable()) {
      <button matButton="tonal" type="button" class="add" (click)="add.emit()">
        <svg lucidePlus [size]="18"></svg>
        Agregar identidad
      </button>
    }
  `,
  styles: `
    @use '../../../../shared/ui/badges';

    .list {
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: var(--sarc-space-2);
        padding: var(--sarc-space-2) 0;

        & + li {
          border-top: 1px solid var(--mat-sys-outline-variant);
        }
      }
    }

    .id {
      margin-left: var(--sarc-space-2);
      font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
    }

    .muted,
    .empty {
      display: block;
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }

    .status {
      display: flex;
      align-items: center;
      gap: var(--sarc-space-1);
    }

    .badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    .add {
      margin-top: var(--sarc-space-3);
    }
  `,
})
export class ExternalIdentities {
  readonly identities = input.required<readonly ExternalIdentity[]>();
  readonly editable = input(false);
  readonly add = output<void>();
  readonly verify = output<ExternalIdentity>();
  readonly remove = output<ExternalIdentity>();

  protected readonly platformLabels = PLATFORM_LABELS;
}
