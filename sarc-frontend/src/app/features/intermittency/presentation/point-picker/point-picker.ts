import { Component, inject, input, linkedSignal, output } from '@angular/core';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LucideX } from '@lucide/angular';

import { KiaPointResponse } from '../../../../core/api/generated';
import { PointOptions } from '../../application/point-options';

function labelOf(point: KiaPointResponse): string {
  return point.assignedCode ? `${point.name} · ${point.assignedCode}` : point.name;
}

/** Elige un punto de carga KIA: se busca por nombre o codigo, igual que el filtro de equipo de Consumo. */
@Component({
  selector: 'sarc-point-picker',
  imports: [MatAutocompleteModule, MatButtonModule, MatFormFieldModule, MatInputModule, LucideX],
  providers: [PointOptions],
  template: `
    <mat-form-field appearance="outline" subscriptSizing="dynamic">
      <mat-label>Punto de carga KIA</mat-label>
      <input
        matInput
        type="search"
        placeholder="Nombre o código"
        autocomplete="off"
        [value]="label()"
        [matAutocomplete]="auto"
        (input)="onType($event)"
      />
      @if (point()) {
        <button
          matIconButton
          matSuffix
          type="button"
          aria-label="Quitar el punto"
          (click)="clear()"
        >
          <svg lucideX [size]="18" aria-hidden="true"></svg>
        </button>
      }
      <mat-autocomplete #auto="matAutocomplete" (optionSelected)="onSelect($event)">
        @for (option of options.options(); track option.id) {
          <mat-option [value]="option">
            {{ option.name }}
            @if (option.assignedCode) {
              <span class="code">· {{ option.assignedCode }}</span>
            }
            @if (option.adminStatus === 'INACTIVE') {
              <span class="code">· Inactivo</span>
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
export class PointPicker {
  /** Punto elegido, o nulo. */
  readonly point = input.required<KiaPointResponse | null>();
  readonly pointChange = output<KiaPointResponse | null>();

  protected readonly options = inject(PointOptions);
  /** Texto del campo; se vacia cuando el punto se quita desde afuera (por ejemplo, al guardar y registrar otro). */
  protected readonly label = linkedSignal<KiaPointResponse | null, string>({
    source: this.point,
    computation: (point, previous) =>
      point ? labelOf(point) : previous?.source ? '' : (previous?.value ?? ''),
  });

  protected onType(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.label.set(text);
    this.options.query.set(text);
    if (text === '' && this.point() !== null) this.pointChange.emit(null);
  }

  protected onSelect(event: MatAutocompleteSelectedEvent): void {
    const point = event.option.value as KiaPointResponse;
    this.label.set(labelOf(point));
    this.pointChange.emit(point);
  }

  protected clear(): void {
    this.label.set('');
    this.options.query.set('');
    this.pointChange.emit(null);
  }
}
