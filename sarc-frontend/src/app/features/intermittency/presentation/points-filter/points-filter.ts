import { Component, computed, inject, input, output, signal } from '@angular/core';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { LucideX } from '@lucide/angular';

import { KiaPointResponse } from '../../../../core/api/generated';
import { PointOptions } from '../../application/point-options';

/** Filtro por uno o varios puntos KIA: se buscan por nombre o codigo y quedan como fichas que se quitan. */
@Component({
  selector: 'sarc-points-filter',
  imports: [MatAutocompleteModule, MatChipsModule, MatFormFieldModule, LucideX],
  providers: [PointOptions],
  template: `
    <mat-form-field appearance="outline" subscriptSizing="dynamic">
      <mat-label>Puntos KIA</mat-label>
      <mat-chip-grid #grid aria-label="Puntos elegidos">
        @for (point of points(); track point.id) {
          <mat-chip-row (removed)="remove(point)">
            {{ point.name }}
            <button matChipRemove type="button" [attr.aria-label]="'Quitar ' + point.name">
              <svg lucideX [size]="16" aria-hidden="true"></svg>
            </button>
          </mat-chip-row>
        }
      </mat-chip-grid>
      <input
        type="search"
        autocomplete="off"
        placeholder="Nombre o código"
        [value]="text()"
        [matChipInputFor]="grid"
        [matAutocomplete]="auto"
        (input)="onType($event)"
      />
      <mat-autocomplete #auto="matAutocomplete" (optionSelected)="onSelect($event)">
        @for (option of available(); track option.id) {
          <mat-option [value]="option">
            {{ option.name }}
            @if (option.assignedCode) {
              <span class="code">· {{ option.assignedCode }}</span>
            }
          </mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
  styles: `
    mat-form-field {
      width: 100%;
    }

    .code {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class PointsFilter {
  /** Puntos elegidos; vacio es todos. */
  readonly points = input.required<readonly KiaPointResponse[]>();
  readonly pointsChange = output<readonly KiaPointResponse[]>();

  protected readonly options = inject(PointOptions);
  protected readonly text = signal('');
  /** Las opciones sin los puntos que ya estan elegidos. */
  protected readonly available = computed(() => {
    const chosen = new Set(this.points().map((point) => point.id));
    return this.options.options().filter((option) => !chosen.has(option.id));
  });

  protected onType(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.text.set(text);
    this.options.query.set(text);
  }

  protected onSelect(event: MatAutocompleteSelectedEvent): void {
    const point = event.option.value as KiaPointResponse;
    event.option.deselect();
    this.text.set('');
    this.options.query.set('');
    this.pointsChange.emit([...this.points(), point]);
  }

  protected remove(point: KiaPointResponse): void {
    this.pointsChange.emit(this.points().filter((other) => other.id !== point.id));
  }
}
