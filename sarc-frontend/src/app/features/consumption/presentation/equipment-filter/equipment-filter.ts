import { Component, inject, input, linkedSignal, output } from '@angular/core';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LucideX } from '@lucide/angular';

import { EquipmentSummaryResponse } from '../../../../core/api/generated';
import { EquipmentOptions } from '../../application/equipment-options';

/** Filtro por un equipo del inventario: se busca por nombre, codigo o serie. */
@Component({
  selector: 'sarc-equipment-filter',
  imports: [MatAutocompleteModule, MatButtonModule, MatFormFieldModule, MatInputModule, LucideX],
  providers: [EquipmentOptions],
  template: `
    <mat-form-field appearance="outline" subscriptSizing="dynamic">
      <mat-label>Equipo</mat-label>
      <input
        matInput
        type="search"
        placeholder="Nombre, código o serie"
        [value]="label()"
        [matAutocomplete]="auto"
        (input)="onType($event)"
      />
      @if (equipmentId() !== '') {
        <button
          matIconButton
          matSuffix
          type="button"
          aria-label="Quitar filtro de equipo"
          (click)="clear()"
        >
          <svg lucideX [size]="18"></svg>
        </button>
      }
      <mat-autocomplete #auto="matAutocomplete" (optionSelected)="onSelect($event)">
        @for (option of options.options(); track option.id) {
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
export class EquipmentFilter {
  /** Equipo filtrado; '' sin filtro. */
  readonly equipmentId = input.required<string>();
  readonly equipmentChange = output<string>();

  protected readonly options = inject(EquipmentOptions);
  /** Texto del campo; se vacia cuando el filtro se quita desde afuera ("Limpiar filtros"). */
  protected readonly label = linkedSignal<string, string>({
    source: this.equipmentId,
    computation: (id, previous) => (id === '' ? '' : (previous?.value ?? '')),
  });

  protected onType(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.label.set(text);
    this.options.query.set(text);
    if (text === '' && this.equipmentId() !== '') {
      this.equipmentChange.emit('');
    }
  }

  protected onSelect(event: MatAutocompleteSelectedEvent): void {
    const equipment = event.option.value as EquipmentSummaryResponse;
    this.label.set(
      equipment.assignedCode ? `${equipment.name} · ${equipment.assignedCode}` : equipment.name,
    );
    this.equipmentChange.emit(equipment.id);
  }

  protected clear(): void {
    this.label.set('');
    this.options.query.set('');
    this.equipmentChange.emit('');
  }
}
