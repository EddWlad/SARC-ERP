import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { LucideFileUp } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { ImportBatchLike, ORIGIN_LABELS } from '../../../../shared/imports/import-batch';
import {
  ImportDialog,
  ImportDialogData,
} from '../../../../shared/imports/import-dialog/import-dialog';
import { ImportStatusBadge } from '../../../../shared/imports/import-status-badge';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { UptimeImportHistory } from '../../application/uptime-import-history';
import {
  DeclaredPeriodType,
  DECLARED_PERIOD_TYPES,
  reportWeeks,
  UptimeImportBatch,
} from '../../domain/uptime-import';
import { PERIOD_TYPE_LABELS } from '../../domain/uptime-labels';

/** Historial de lotes de uptime. Por ahora solo pegado: el archivo espera la muestra real. */
@Component({
  selector: 'sarc-uptime-imports-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    LucideFileUp,
    ImportStatusBadge,
  ],
  providers: [UptimeImportHistory, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  template: `
    <header class="page-header">
      <div>
        <h1>Importaciones</h1>
        <p>
          Filas pegadas con su encabezado; se revisan en una vista previa antes de incorporarse.
        </p>
      </div>
      <div class="header-actions">
        <button matButton="filled" type="button" (click)="create()">
          <svg lucideFileUp [size]="18"></svg>
          Nueva importación
        </button>
      </div>
    </header>

    <section
      class="results"
      aria-label="Historial de importaciones"
      [attr.aria-busy]="facade.loading()"
    >
      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" aria-label="Cargando importaciones" />
      }
      @if (facade.failed()) {
        <div class="state" role="alert">
          <p>No se pudo cargar el historial.</p>
          <button matButton="tonal" type="button" (click)="facade.reload()">
            Reintentar ahora
          </button>
        </div>
      } @else if (!facade.loading() && facade.batches().length === 0) {
        <div class="state"><p>Aún no hay importaciones de uptime.</p></div>
      } @else if (isHandset()) {
        <ul class="cards">
          @for (batch of facade.batches(); track batch.id) {
            <li class="card">
              <div class="card-head">
                <a class="link" [routerLink]="[batch.id]">
                  <strong>{{ periodLabels[batch.declaredPeriodType] }}</strong>
                  <span class="muted block">{{ batch.createdAt | date: 'short' }}</span>
                </a>
                <sarc-import-status-badge [status]="batch.status" />
              </div>
              <span class="muted">
                {{ originLabels[batch.origin] }} · {{ quantities.count(batch.totalRows) }} filas ·
                {{ quantities.count(batch.appliedRows) }} incorporadas
              </span>
            </li>
          }
        </ul>
      } @else {
        <table mat-table [dataSource]="facade.batches()" class="table">
          <ng-container matColumnDef="created">
            <th mat-header-cell *matHeaderCellDef>Lote</th>
            <td mat-cell *matCellDef="let batch">
              <a class="link" [routerLink]="[batch.id]">
                <strong>{{ periodLabelOf(batch) }}</strong>
                <span class="muted block">{{ batch.createdAt | date: 'short' }}</span>
              </a>
            </td>
          </ng-container>
          <ng-container matColumnDef="origin">
            <th mat-header-cell *matHeaderCellDef>Origen</th>
            <td mat-cell *matCellDef="let batch">{{ originOf(batch) }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let batch">
              <sarc-import-status-badge [status]="batch.status" />
            </td>
          </ng-container>
          <ng-container matColumnDef="rows">
            <th mat-header-cell *matHeaderCellDef>Filas</th>
            <td mat-cell *matCellDef="let batch">
              {{ quantities.count(batch.totalRows) }}
              <span class="muted block">
                {{ quantities.count(batch.conflictRows) }} en conflicto ·
                {{ quantities.count(batch.pendingLinkRows) }} por vincular
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="applied">
            <th mat-header-cell *matHeaderCellDef class="number">Incorporadas</th>
            <td mat-cell *matCellDef="let batch" class="number">
              <strong>{{ quantities.count(batch.appliedRows) }}</strong>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      }
      <mat-paginator
        [length]="facade.totalElements()"
        [pageIndex]="facade.pageIndex()"
        [pageSize]="facade.pageSize()"
        [pageSizeOptions]="pageSizeOptions"
        (page)="onPage($event)"
        aria-label="Paginación de importaciones"
      />
    </section>
  `,
  styles: `
    @use '../../../../shared/ui/list-page';
    @use '../../../../shared/imports/import-history';

    :host {
      display: block;
    }
  `,
})
export class UptimeImportsPage {
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly facade = inject(UptimeImportHistory);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly periodLabels = PERIOD_TYPE_LABELS;
  protected readonly originLabels = ORIGIN_LABELS;
  protected readonly pageSizeOptions = [10, 20, 50];
  protected readonly isHandset = injectIsHandset();
  protected readonly columns = ['created', 'origin', 'status', 'rows', 'applied'];

  protected periodLabelOf(batch: UptimeImportBatch): string {
    return PERIOD_TYPE_LABELS[batch.declaredPeriodType];
  }

  protected originOf(batch: UptimeImportBatch): string {
    return ORIGIN_LABELS[batch.origin];
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  protected async create(): Promise<void> {
    const data: ImportDialogData = {
      fileHint: 'Informe semanal en XLSX, XLS o CSV; se leen todas sus hojas',
      pasteHint: 'Incluya la fila de encabezados; el tipo se declara, no se deduce.',
      choice: {
        label: 'Ventana consultada',
        requiredMessage: 'Seleccione la ventana consultada',
        options: DECLARED_PERIOD_TYPES.map((type) => ({
          value: type,
          label: PERIOD_TYPE_LABELS[type],
          hint: 'Columnas: Activo, Cargador, Conector, Desde, Hasta, Disponible, Ocupado, Con falla, Fuera de línea y Uptime.',
        })),
      },
      fileChoice: {
        label: 'Semana del informe',
        requiredMessage: 'Seleccione la semana que reporta el informe',
        options: reportWeeks(new Date()).map((week) => ({
          value: week.value,
          label: week.label,
          hint: 'Columnas: Cargador, Conector, Uptime (%), Fuera de línea, Disponible y En falla (hrs). Se guarda el uptime del informe.',
        })),
      },
      upload: (file, weekStart) => this.facade.upload(weekStart, file),
      paste: (text, type) => this.facade.paste(type as DeclaredPeriodType, text),
    };
    const batch = await firstValueFrom(
      this.dialog
        .open<ImportDialog, ImportDialogData, ImportBatchLike>(ImportDialog, {
          data,
          width: '560px',
          maxWidth: '95vw',
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
    if (batch !== undefined) {
      await this.router.navigate(['/uptime/importaciones', batch.id]);
    }
  }
}
