import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  linkedSignal,
  output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideX } from '@lucide/angular';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';

import { ScopeConnectorResponse } from '../../../../core/api/generated';

const SEARCH_DEBOUNCE_MS = 300;

/** Filtro por equipo con busqueda, como en Consumo; el conector aparece al elegir un equipo. */
@Component({
  selector: 'sarc-connector-filter',
  imports: [
    MatAutocompleteModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    LucideX,
  ],
  template: `
    <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
      <mat-label>Equipo</mat-label>
      <input
        matInput
        type="search"
        placeholder="Nombre o código"
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
        @for (option of equipment(); track option.equipmentId) {
          <mat-option [value]="option">
            {{ option.equipmentName }}
            @if (option.assignedCode) {
              <span class="code">· {{ option.assignedCode }}</span>
            }
          </mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
    @if (equipmentId() !== '') {
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Conector</mat-label>
        <mat-select [value]="connectorId()" (selectionChange)="connectorChange.emit($event.value)">
          <mat-option value="">Todos</mat-option>
          @for (item of connectors(); track item.connectorId) {
            <mat-option [value]="item.connectorId">
              Conector {{ item.ordinal }} · {{ item.connectorType }}
            </mat-option>
          }
        </mat-select>
      </mat-form-field>
    }
  `,
  styles: `
    :host {
      display: contents;
    }

    .code {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class ConnectorFilter {
  readonly options = input.required<readonly ScopeConnectorResponse[]>();
  /** Equipo filtrado; '' sin filtro. */
  readonly equipmentId = input.required<string>();
  readonly connectorId = input.required<string>();
  readonly equipmentChange = output<string>();
  readonly connectorChange = output<string>();
  readonly searched = output<string>();

  private readonly typed = new Subject<string>();
  /** Texto del campo; se vacia cuando el filtro se quita desde afuera ("Limpiar filtros"). */
  protected readonly label = linkedSignal<string, string>({
    source: this.equipmentId,
    computation: (id, previous) => (id === '' ? '' : (previous?.value ?? '')),
  });
  protected readonly equipment = computed(() => {
    const unique = new Map(this.options().map((item) => [item.equipmentId, item]));
    return [...unique.values()];
  });
  protected readonly connectors = computed(() =>
    this.options().filter((item) => item.equipmentId === this.equipmentId()),
  );

  constructor() {
    this.typed
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((text) => this.searched.emit(text));
  }

  protected onType(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.label.set(text);
    this.typed.next(text);
    if (text === '' && this.equipmentId() !== '') {
      this.equipmentChange.emit('');
    }
  }

  protected onSelect(event: MatAutocompleteSelectedEvent): void {
    const item = event.option.value as ScopeConnectorResponse;
    this.label.set(
      item.assignedCode ? `${item.equipmentName} · ${item.assignedCode}` : item.equipmentName,
    );
    this.equipmentChange.emit(item.equipmentId);
  }

  protected clear(): void {
    this.label.set('');
    this.typed.next('');
    this.equipmentChange.emit('');
  }
}
