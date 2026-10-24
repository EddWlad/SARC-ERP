import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { LucideFilterX } from '@lucide/angular';

import {
  NETWORK_LABELS,
  NETWORKS,
  TYPE_LABELS,
  TYPES,
} from '../../../../shared/vocabulary/charging-infrastructure';
import { PeriodSelection } from '../../application/period-selection';
import { ConsumptionFilters, SOURCE_LABELS, SOURCES } from '../../domain/consumption';
import { EquipmentFilter } from '../equipment-filter/equipment-filter';
import { PeriodFilter } from '../period-filter/period-filter';

/**
 * Filtros de consumo en una fila sobre las graficas (PRD RF-003.13): periodo, red, plataforma,
 * tipo y equipo. Filtros propios de una pantalla se proyectan al final.
 */
@Component({
  selector: 'sarc-consumption-filter-bar',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    LucideFilterX,
    EquipmentFilter,
    PeriodFilter,
  ],
  template: `
    <sarc-period-filter [period]="period()" />
    <section class="fields" aria-label="Filtros">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Red</mat-label>
        <mat-select
          [value]="filters().network"
          (selectionChange)="change({ network: $event.value })"
        >
          <mat-option value="">Todas</mat-option>
          @for (network of networks; track network) {
            <mat-option [value]="network">{{ networkLabels[network] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Plataforma</mat-label>
        <mat-select [value]="filters().source" (selectionChange)="change({ source: $event.value })">
          <mat-option value="">Todas</mat-option>
          @for (source of sources; track source) {
            <mat-option [value]="source">{{ sourceLabels[source] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Tipo</mat-label>
        <mat-select [value]="filters().type" (selectionChange)="change({ type: $event.value })">
          <mat-option value="">Todos</mat-option>
          @for (type of types; track type) {
            <mat-option [value]="type">{{ typeLabels[type] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <sarc-equipment-filter
        [equipmentId]="filters().equipmentId"
        (equipmentChange)="change({ equipmentId: $event })"
      />

      <ng-content />

      @if (hasFilters() || ownFiltersActive()) {
        <button matButton type="button" class="clear" (click)="cleared.emit()">
          <svg lucideFilterX [size]="18"></svg>
          Limpiar filtros
        </button>
      }
    </section>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      margin-bottom: var(--sarc-space-5);
    }

    .fields {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: var(--sarc-space-3);
      align-items: center;
    }

    .clear {
      justify-self: start;
    }
  `,
})
export class ConsumptionFilterBar {
  readonly period = input.required<PeriodSelection>();
  readonly filters = input.required<ConsumptionFilters>();
  /** Hay filtros proyectados (propios de la pantalla) con valor: tambien se ofrece limpiar. */
  readonly ownFiltersActive = input(false);
  readonly filtersChange = output<Partial<ConsumptionFilters>>();
  readonly cleared = output<void>();

  protected readonly hasFilters = computed(() =>
    Object.values(this.filters()).some((value) => value !== ''),
  );
  protected readonly networks = NETWORKS;
  protected readonly networkLabels = NETWORK_LABELS;
  protected readonly types = TYPES;
  protected readonly typeLabels = TYPE_LABELS;
  protected readonly sources = SOURCES;
  protected readonly sourceLabels = SOURCE_LABELS;

  protected change(changes: Partial<ConsumptionFilters>): void {
    this.filtersChange.emit(changes);
  }
}
