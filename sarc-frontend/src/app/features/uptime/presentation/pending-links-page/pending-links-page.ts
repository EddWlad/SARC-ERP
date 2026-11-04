import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { LucideLink, LucideSearch } from '@lucide/angular';

import { PendingLinksFacade } from '../../application/pending-links-facade';
import { ConnectorAlias, UptimeImportRow } from '../../domain/uptime-import';

/** Concilia filas sin conector y administra las etiquetas aprobadas. */
@Component({
  selector: 'sarc-pending-links-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    LucideLink,
    LucideSearch,
  ],
  providers: [PendingLinksFacade],
  template: `
    <header class="page-header">
      <div>
        <h1>Vinculación</h1>
        <p>Estas filas quedan fuera de los indicadores hasta asignarles su conector.</p>
      </div>
    </header>

    <section class="filters" aria-label="Filtros">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Buscar equipo o conector</mat-label>
        <svg lucideSearch matPrefix [size]="18" class="prefix-icon"></svg>
        <input matInput type="search" #search (input)="facade.search(search.value)" />
      </mat-form-field>
    </section>

    <section class="results" aria-labelledby="pending-title" [attr.aria-busy]="facade.loading()">
      @if (facade.loading()) {
        <mat-progress-bar mode="indeterminate" aria-label="Cargando pendientes" />
      }
      <h2 class="results-title" id="pending-title">Pendientes ({{ facade.total() }})</h2>
      @if (facade.error(); as error) {
        <div class="state" role="alert">
          <p>{{ error }}</p>
          <button matButton="tonal" type="button" (click)="facade.load()">Reintentar</button>
        </div>
      } @else if (!facade.loading() && facade.rows().length === 0) {
        <div class="state"><p>No hay filas por vincular.</p></div>
      }
      <ul class="cards">
        @for (row of facade.rows(); track row.id) {
          <li class="card">
            <div class="card-head">
              <div>
                <strong class="wrap">
                  {{ row.sourceSite }} · {{ row.sourceCharger }} · {{ row.sourceConnector }}
                </strong>
                <span class="muted block">
                  {{ row.periodStart | date: 'short' }} a {{ row.periodEnd | date: 'short' }}
                </span>
              </div>
              <span class="badge badge-warn">Por vincular</span>
            </div>
            @for (issue of row.issues; track issue.code) {
              <span class="muted">{{ issue.message }}</span>
            }
            <div class="actions">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Conector</mat-label>
                <mat-select [value]="chosen(row)" (selectionChange)="choose(row.id, $event.value)">
                  @for (option of facade.optionsFor(row.equipmentId); track option.connectorId) {
                    <mat-option [value]="option.connectorId">
                      {{ option.equipmentName }} · Conector {{ option.ordinal }} ·
                      {{ option.connectorType }}
                    </mat-option>
                  }
                </mat-select>
                @if (isProposal(row)) {
                  <mat-hint>Propuesto por tipo de conector; confírmalo antes de vincular.</mat-hint>
                }
              </mat-form-field>
              <mat-checkbox
                #remember
                [checked]="!!row.suggestedConnectorId"
                [disabled]="!row.equipmentId"
              >
                Recordar esta etiqueta
              </mat-checkbox>
              <button
                matButton="filled"
                type="button"
                [disabled]="facade.busy() || !chosen(row)"
                (click)="link(row, remember.checked)"
              >
                <svg lucideLink [size]="18"></svg>
                Vincular
              </button>
            </div>
          </li>
        }
      </ul>
    </section>

    <section class="results aliases" aria-labelledby="aliases-title">
      <h2 class="results-title" id="aliases-title">Etiquetas aprobadas</h2>
      <p class="muted lead">
        Cambiarlas solo afecta cargas posteriores, nunca mediciones guardadas.
      </p>
      @if (facade.aliases().length === 0) {
        <div class="state"><p>Aún no hay etiquetas aprobadas.</p></div>
      }
      <ul class="cards">
        @for (alias of facade.aliases(); track alias.id) {
          <li class="card">
            <strong class="wrap">{{ alias.label }}</strong>
            <div class="actions">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Conector</mat-label>
                <mat-select
                  [value]="alias.connectorId"
                  [disabled]="facade.busy()"
                  (selectionChange)="facade.updateAlias(alias, $event.value, alias.active)"
                >
                  @if (!isListed(alias)) {
                    <mat-option [value]="alias.connectorId">Conector actual</mat-option>
                  }
                  @for (option of facade.optionsFor(alias.equipmentId); track option.connectorId) {
                    <mat-option [value]="option.connectorId">
                      Conector {{ option.ordinal }} · {{ option.connectorType }}
                    </mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-checkbox
                [checked]="alias.active"
                [disabled]="facade.busy()"
                (change)="facade.updateAlias(alias, alias.connectorId, $event.checked)"
              >
                Activa
              </mat-checkbox>
            </div>
          </li>
        }
      </ul>
    </section>
  `,
  styles: `
    @use '../../../../shared/ui/list-page';
    @use '../../../../shared/ui/badges';

    :host {
      display: block;
      --sarc-filter-columns: minmax(0, 420px);
    }

    .results-title {
      margin: 0;
      padding: var(--sarc-space-4) var(--sarc-space-4) var(--sarc-space-2);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-title-small);
    }

    .lead {
      display: block;
      margin: 0;
      padding: 0 var(--sarc-space-4) var(--sarc-space-2);
    }

    .aliases {
      margin-top: var(--sarc-space-4);
    }

    .card:last-child {
      border-bottom: 0;
    }

    .wrap {
      overflow-wrap: anywhere;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-3);

      mat-form-field {
        flex: 1 1 260px;
        min-width: 0;
      }
    }
  `,
})
export class PendingLinksPage implements OnInit {
  protected readonly facade = inject(PendingLinksFacade);
  protected readonly selected = signal<Readonly<Record<string, string>>>({});

  ngOnInit(): void {
    void this.facade.load();
  }

  protected choose(rowId: string, connectorId: string): void {
    this.selected.update((current) => ({ ...current, [rowId]: connectorId }));
  }

  /** Lo elegido por la persona; si aun no elige, el conector que propone el tipo de la etiqueta. */
  protected chosen(row: UptimeImportRow): string | undefined {
    return this.selected()[row.id] ?? row.suggestedConnectorId ?? undefined;
  }

  protected isProposal(row: UptimeImportRow): boolean {
    return !!row.suggestedConnectorId && this.chosen(row) === row.suggestedConnectorId;
  }

  protected isListed(alias: ConnectorAlias): boolean {
    return this.facade
      .optionsFor(alias.equipmentId)
      .some((option) => option.connectorId === alias.connectorId);
  }

  protected link(row: UptimeImportRow, rememberLabel: boolean): void {
    const connectorId = this.chosen(row);
    if (connectorId) {
      void this.facade.link(row, connectorId, rememberLabel);
    }
  }
}
