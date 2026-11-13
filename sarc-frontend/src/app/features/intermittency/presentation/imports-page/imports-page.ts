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
import { IntermittencyImportHistory } from '../../application/import-history';
import { IntermittencyImportBatch } from '../../domain/intermittency-import';

/** Historial de lotes de intermitencias: archivo XLSX o CSV, o filas pegadas desde el Excel operativo. */
@Component({
  selector: 'sarc-intermittency-imports-page',
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
  providers: [
    IntermittencyImportHistory,
    { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl },
  ],
  template: `
    <header class="page-header">
      <div>
        <h1>Importaciones</h1>
        <p>
          Cargue el registro operativo en XLSX o CSV, o pegue las filas desde el Excel. Se revisa en
          una vista previa que se puede corregir antes de confirmar.
        </p>
      </div>
      <div class="header-actions">
        <button matButton="filled" type="button" (click)="create()">
          <svg lucideFileUp [size]="18" aria-hidden="true"></svg>
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
        <div class="state"><p>Aún no hay importaciones de intermitencias.</p></div>
      } @else if (isHandset()) {
        <ul class="cards">
          @for (batch of facade.batches(); track batch.id) {
            <li class="card">
              <div class="card-head">
                <a class="link" [routerLink]="[batch.id]">
                  <strong>{{ batch.fileName }}</strong>
                  <span class="muted block">{{ batch.createdAt | date: 'short' }}</span>
                </a>
                <sarc-import-status-badge [status]="batch.status" />
              </div>
              <span class="muted">
                {{ originOf(batch) }} · {{ quantities.count(batch.totalRows) }} filas ·
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
                <strong>{{ batch.fileName }}</strong>
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
                {{ quantities.count(batch.counts.conflict) }} en conflicto ·
                {{ quantities.count(batch.counts.invalid) }} inválidas ·
                {{ quantities.count(batch.counts.pendingLink) }} por vincular
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
    @use '../../../../shared/ui/badges';
    @use '../../../../shared/imports/import-history';

    :host {
      display: block;
    }
  `,
})
export class IntermittencyImportsPage {
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly facade = inject(IntermittencyImportHistory);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly pageSizeOptions = [10, 20, 50];
  protected readonly isHandset = injectIsHandset();
  protected readonly columns = ['created', 'origin', 'status', 'rows', 'applied'];

  protected originOf(batch: IntermittencyImportBatch): string {
    return ORIGIN_LABELS[batch.origin];
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  protected async create(): Promise<void> {
    const data: ImportDialogData = {
      fileHint:
        'Registro operativo en XLSX o CSV (solo se lee la primera hoja). Columnas: Fecha, Código cargador, ' +
        'Nombre cargador, Comentario, pares Inicio y Fin, y Total inactivo.',
      pasteHint:
        'Pegue las filas copiadas del Excel; si no incluye la fila de encabezados, se usa el orden de la plantilla.',
      upload: (file) => this.facade.upload(file),
      paste: (text) => this.facade.paste(text),
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
      await this.router.navigate(['/intermitencias/importaciones', batch.id]);
    }
  }
}
