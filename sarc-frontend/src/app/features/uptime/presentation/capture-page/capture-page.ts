import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { LucidePlus, LucideSearch } from '@lucide/angular';

import { GridRowResponse } from '../../../../core/api/generated';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { CapturePeriod, CaptureFacade } from '../../application/capture-facade';
import { PERIOD_TYPE_LABELS, PeriodType } from '../../domain/uptime-labels';
import { MeasurementForm } from '../measurement-form/measurement-form';

const SITUATION_LABELS: Readonly<Record<string, string>> = {
  NONE: 'Sin registrar',
  PENDING: 'Pendiente',
  OBSERVED: 'Observada',
  VALIDATED: 'Validada',
  EXCLUDED: 'Excluida',
};

/** Convierte un `datetime-local` a instante en la zona operativa (Ecuador, sin horario de verano). */
const toOperatingInstant = (local: string): string => `${local}:00-05:00`;

/** Grilla de conectores para un periodo real, y secuencia de captura. */
@Component({
  selector: 'sarc-capture-page',
  imports: [
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    LucidePlus,
    LucideSearch,
  ],
  providers: [CaptureFacade],
  template: `
    <header class="page-header">
      <div>
        <h1>Captura de mediciones</h1>
        <p>Consulte el período real de Enerlink y registre cada conector en secuencia.</p>
      </div>
    </header>

    <form class="filters" aria-label="Período de captura" (submit)="load($event)">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Tipo de período</mat-label>
        <mat-select [(ngModel)]="periodType" name="periodType">
          @for (type of types; track type) {
            <mat-option [value]="type">{{ labels[type] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Desde</mat-label>
        <input matInput type="datetime-local" [(ngModel)]="start" name="start" required />
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Hasta</mat-label>
        <input matInput type="datetime-local" [(ngModel)]="end" name="end" required />
      </mat-form-field>
      <mat-checkbox [(ngModel)]="showInactive" name="showInactive">Incluir inactivos</mat-checkbox>
      <button matButton="filled" type="submit" [disabled]="!start() || !end()">
        <svg lucideSearch [size]="18"></svg>
        Consultar
      </button>
    </form>

    <section
      class="results"
      aria-label="Conectores del período"
      [attr.aria-busy]="facade.loading()"
    >
      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" aria-label="Cargando conectores" />
      }
      @if (facade.error() && !dialogOpen()) {
        <div class="state" role="alert">
          <p>{{ facade.error() }}</p>
        </div>
      } @else if (!loaded()) {
        <div class="state"><p>Elija un período para ver sus conectores.</p></div>
      } @else if (!facade.loading() && facade.rows().length === 0) {
        <div class="state"><p>No hay conectores evaluables con estos filtros.</p></div>
      } @else if (isHandset()) {
        <ul class="cards">
          @for (row of facade.rows(); track row.connectorId) {
            <li class="card">
              <div class="card-head">
                <div>
                  <strong>{{ row.equipmentName }}</strong>
                  <span class="muted block">
                    Conector {{ row.ordinal }} · {{ row.connectorTypeCode }}
                  </span>
                </div>
                <span class="badge" [class]="badgeOf(row)">{{
                  situationLabels[row.situation]
                }}</span>
              </div>
              @if (row.situation === 'NONE') {
                <button
                  matButton="tonal"
                  type="button"
                  [attr.aria-label]="registerLabel(row)"
                  (click)="capture(row.connectorId)"
                >
                  <svg lucidePlus [size]="18"></svg>
                  Registrar
                </button>
              }
            </li>
          }
        </ul>
      } @else {
        <table mat-table [dataSource]="facade.rows()" class="table">
          <ng-container matColumnDef="equipment">
            <th mat-header-cell *matHeaderCellDef>Equipo</th>
            <td mat-cell *matCellDef="let row">
              <strong>{{ row.equipmentName }}</strong>
            </td>
          </ng-container>
          <ng-container matColumnDef="connector">
            <th mat-header-cell *matHeaderCellDef>Conector</th>
            <td mat-cell *matCellDef="let row">{{ row.ordinal }} · {{ row.connectorTypeCode }}</td>
          </ng-container>
          <ng-container matColumnDef="situation">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let row">
              <span class="badge" [class]="badgeOf(row)">{{ situationLabels[row.situation] }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>
              <span class="sarc-visually-hidden">Acciones</span>
            </th>
            <td mat-cell *matCellDef="let row" class="actions">
              @if (row.situation === 'NONE') {
                <button
                  matButton="tonal"
                  type="button"
                  [attr.aria-label]="registerLabel(row)"
                  (click)="capture(row.connectorId)"
                >
                  <svg lucidePlus [size]="18"></svg>
                  Registrar
                </button>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      }
    </section>
  `,
  styles: `
    @use '../../../../shared/ui/list-page';
    @use '../../../../shared/ui/badges';

    :host {
      display: block;
    }

    // Se acomoda al ancho util: el menu lateral resta espacio que una media query no ve.
    .filters {
      grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
      align-items: center;
    }

    .actions {
      text-align: end;
    }
  `,
})
export class CapturePage {
  protected readonly facade = inject(CaptureFacade);
  private readonly dialog = inject(MatDialog);
  protected readonly isHandset = injectIsHandset();
  protected readonly labels = PERIOD_TYPE_LABELS;
  protected readonly situationLabels = SITUATION_LABELS;
  protected readonly columns = ['equipment', 'connector', 'situation', 'actions'];
  protected readonly types: PeriodType[] = ['ROLLING_24H', 'ROLLING_7D', 'ROLLING_30D'];
  protected readonly periodType = signal<PeriodType>('ROLLING_24H');
  protected readonly start = signal('');
  protected readonly end = signal('');
  protected readonly showInactive = this.facade.showInactive;
  protected readonly loaded = signal(false);
  protected readonly dialogOpen = signal(false);

  protected badgeOf(row: GridRowResponse): string {
    switch (row.situation) {
      case 'VALIDATED':
        return 'badge';
      case 'PENDING':
        return 'badge badge-info';
      case 'OBSERVED':
        return 'badge badge-warn';
      default:
        return 'badge badge-off';
    }
  }

  protected registerLabel(row: GridRowResponse): string {
    return `Registrar ${row.equipmentName}, conector ${row.ordinal}`;
  }

  protected async load(event: Event): Promise<void> {
    event.preventDefault();
    const period: CapturePeriod = {
      periodType: this.periodType(),
      periodStart: toOperatingInstant(this.start()),
      periodEnd: toOperatingInstant(this.end()),
    };
    this.facade.setPeriod(period);
    await this.facade.load();
    this.loaded.set(true);
  }

  protected capture(connectorId: string): void {
    this.facade.select(connectorId);
    this.dialogOpen.set(true);
    this.dialog
      .open(MeasurementForm, { data: this.facade, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe(() => {
        this.dialogOpen.set(false);
        this.facade.select(null);
      });
  }
}
