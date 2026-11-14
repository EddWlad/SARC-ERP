import { Component, inject, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

import { KiaPointResponse, WindowResponse } from '../../../../core/api/generated';
import { SessionStore } from '../../../../core/auth/session-store';
import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { WindowsFacade } from '../../application/windows-facade';
import { INTERMITTENCY_EDITORS } from '../../domain/intermittency-permissions';
import {
  SOURCE_LABELS,
  STATE_BADGES,
  STATE_LABELS,
  WindowState,
} from '../../domain/intermittency-labels';
import {
  durationText,
  formatInstant,
  present,
  todayInOperatingZone,
} from '../../domain/intermittency-time';
import { PointPicker } from '../point-picker/point-picker';
import {
  WindowDetailDialog,
  WindowDetailDialogData,
} from '../window-detail-dialog/window-detail-dialog';

/** Estados que un editor puede pedir; el resto de los roles ve siempre solo las confirmadas (FR-005). */
const FILTERABLE_STATES: readonly WindowState[] = [
  'CONFIRMED',
  'REVIEW',
  'OPEN',
  'TEST',
  'ANNULLED',
];

/**
 * Historial de ventanas de intermitencia, para todos los roles: filtros por punto y rango de dias,
 * duracion en h:mm y minutos, e insignias de estado. El detalle con el historial de revisiones es solo
 * para editores.
 */
@Component({
  selector: 'sarc-intermittency-windows-page',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    PointPicker,
  ],
  providers: [WindowsFacade, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  template: `
    <header class="page-header">
      <div>
        <h1>Ventanas</h1>
        <p>
          Historial de las ventanas de intermitencia de los puntos KIA.
          @if (!canEdit) {
            Se muestran solo las ventanas confirmadas.
          }
        </p>
      </div>
    </header>

    <section class="filters" aria-label="Filtros">
      <sarc-point-picker [point]="facade.point()" (pointChange)="onPoint($event)" />
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Primer día</mat-label>
        <input
          matInput
          type="date"
          [max]="today"
          [value]="facade.firstDay()"
          (change)="onFirst($event)"
        />
      </mat-form-field>
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Último día (incluido)</mat-label>
        <input
          matInput
          type="date"
          [min]="facade.firstDay()"
          [max]="today"
          [value]="facade.lastDay()"
          (change)="onLast($event)"
        />
      </mat-form-field>
      @if (canEdit) {
        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Estado</mat-label>
          <mat-select
            multiple
            [value]="facade.states()"
            (selectionChange)="facade.filter({ states: $event.value })"
          >
            @for (state of filterable; track state) {
              <mat-option [value]="state">{{ stateLabels[state] }}</mat-option>
            }
          </mat-select>
          <mat-hint>Sin elegir, se muestran todos.</mat-hint>
        </mat-form-field>
      }
    </section>

    @if (facade.detailError()) {
      <div class="form-alert" role="alert">
        <p>{{ facade.detailError() }}</p>
      </div>
    }

    <section
      class="results"
      aria-label="Ventanas de intermitencia"
      [attr.aria-busy]="facade.loading()"
    >
      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" aria-label="Cargando ventanas" />
      }
      @if (facade.error()) {
        <div class="state" role="alert">
          <p>{{ facade.error() }}</p>
          <button matButton="tonal" type="button" (click)="facade.load()">Reintentar ahora</button>
        </div>
      } @else if (facade.empty()) {
        <div class="state"><p>No hay ventanas con estos filtros.</p></div>
      } @else if (isHandset()) {
        <ul class="cards">
          @for (window of facade.items(); track window.id) {
            <li class="card">
              <div class="card-head">
                <div>
                  <strong>{{ window.pointLabel }}</strong>
                  <span class="muted block">{{ range(window) }}</span>
                </div>
                <span [class]="badgeOf(window)">{{ stateOf(window) }}</span>
              </div>
              <span class="muted">{{ duration(window) }} · {{ sourceOf(window) }}</span>
              <button
                matButton="tonal"
                type="button"
                [attr.aria-label]="'Ver el detalle de la ventana de ' + window.pointLabel"
                (click)="open(window)"
              >
                Ver detalle
              </button>
            </li>
          }
        </ul>
      } @else {
        <table mat-table [dataSource]="facade.items()" class="table">
          <ng-container matColumnDef="point">
            <th mat-header-cell *matHeaderCellDef>Punto</th>
            <td mat-cell *matCellDef="let window">
              <strong>{{ window.pointLabel }}</strong>
              @if (window.comment) {
                <span class="muted block">{{ window.comment }}</span>
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="range">
            <th mat-header-cell *matHeaderCellDef>Inicio y fin</th>
            <td mat-cell *matCellDef="let window">{{ range(window) }}</td>
          </ng-container>
          <ng-container matColumnDef="duration">
            <th mat-header-cell *matHeaderCellDef class="number">Duración</th>
            <td mat-cell *matCellDef="let window" class="number">{{ duration(window) }}</td>
          </ng-container>
          <ng-container matColumnDef="state">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let window">
              <span [class]="badgeOf(window)">{{ stateOf(window) }}</span>
              <span class="muted block">{{ sourceOf(window) }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef class="actions-cell">
              <span class="visually-hidden">Acciones</span>
            </th>
            <td mat-cell *matCellDef="let window" class="actions-cell">
              <button
                matButton
                type="button"
                [attr.aria-label]="'Ver el detalle de la ventana de ' + window.pointLabel"
                (click)="open(window)"
              >
                Detalle
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      }
      <mat-paginator
        [length]="facade.total()"
        [pageIndex]="facade.pageIndex()"
        [pageSize]="facade.pageSize()"
        [pageSizeOptions]="pageSizeOptions"
        (page)="onPage($event)"
        aria-label="Paginación de ventanas"
      />
    </section>
  `,
  styles: `
    @use '../../../../shared/ui/list-page';
    @use '../../../../shared/ui/badges';
    @use '../../../../shared/ui/dialog-form';

    :host {
      display: block;
      --sarc-filter-columns: minmax(0, 2fr) repeat(3, minmax(0, 1fr));
    }

    .number {
      text-align: end;
    }

    .actions-cell {
      width: 96px;
    }

    .visually-hidden {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
    }
  `,
})
export class IntermittencyWindowsPage implements OnInit {
  private readonly dialog = inject(MatDialog);

  protected readonly facade = inject(WindowsFacade);
  protected readonly canEdit = inject(SessionStore).hasAnyRole(INTERMITTENCY_EDITORS);
  protected readonly isHandset = injectIsHandset();
  protected readonly today = todayInOperatingZone();
  protected readonly pageSizeOptions = [10, 20, 50];
  protected readonly columns = ['point', 'range', 'duration', 'state', 'actions'];
  protected readonly filterable = FILTERABLE_STATES;
  protected readonly stateLabels = STATE_LABELS;

  ngOnInit(): void {
    void this.facade.load();
  }

  protected badgeOf(window: WindowResponse): string {
    return STATE_BADGES[window.state];
  }

  protected stateOf(window: WindowResponse): string {
    return STATE_LABELS[window.state];
  }

  protected sourceOf(window: WindowResponse): string {
    return SOURCE_LABELS[window.source];
  }

  protected range(window: WindowResponse): string {
    return `${formatInstant(window.startAt)} → ${window.endAt ? formatInstant(window.endAt) : 'abierta'}`;
  }

  /** Abierta: duracion provisional rotulada como tal; nunca se confunde con lo oficial (FR-012). */
  protected duration(window: WindowResponse): string {
    if (window.endAt) {
      return present(window.durationMinutes)
        ? `${durationText(window.durationMinutes)} (${window.durationMinutes} min)`
        : '—';
    }
    return present(window.provisionalMinutes)
      ? `${durationText(window.provisionalMinutes)} provisional`
      : 'Abierta';
  }

  protected onPoint(point: KiaPointResponse | null): void {
    void this.facade.filter({ point });
  }

  protected onFirst(event: Event): void {
    void this.facade.filter({ firstDay: (event.target as HTMLInputElement).value });
  }

  protected onLast(event: Event): void {
    void this.facade.filter({ lastDay: (event.target as HTMLInputElement).value });
  }

  protected onPage(event: PageEvent): void {
    void this.facade.changePage(event.pageIndex, event.pageSize);
  }

  protected async open(window: WindowResponse): Promise<void> {
    const detail = await this.facade.openDetail(window.id);
    if (!detail) return;
    this.dialog.open<WindowDetailDialog, WindowDetailDialogData>(WindowDetailDialog, {
      data: { detail },
      width: '640px',
      maxWidth: '95vw',
      autoFocus: 'first-tabbable',
    });
  }
}
