import { Component, computed, inject, input, output } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';

import { QuantityFormat } from '../format/quantity-format';

/** Una clasificacion de filas de la vista previa con su conteo y que pasa con ellas al confirmar. */
export interface ClassificationOption {
  readonly value: string;
  readonly label: string;
  readonly count: number;
  readonly hint: string;
}

/**
 * Chips para filtrar las filas de la vista previa por clasificacion. Solo muestra las que tienen
 * filas y explica la elegida.
 */
@Component({
  selector: 'sarc-import-classification-filter',
  imports: [MatChipsModule],
  template: `
    <mat-chip-listbox
      aria-label="Filtrar filas por clasificación"
      [value]="selected()"
      (change)="selectedChange.emit($event.value ?? '')"
    >
      <mat-chip-option value="" [selectable]="selected() !== ''">
        Todas ({{ quantities.count(total()) }})
      </mat-chip-option>
      @for (option of visible(); track option.value) {
        <mat-chip-option [value]="option.value" [selectable]="selected() !== option.value">
          {{ option.label }} ({{ quantities.count(option.count) }})
        </mat-chip-option>
      }
    </mat-chip-listbox>
    @if (hint(); as text) {
      <p class="hint">{{ text }}</p>
    }
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: var(--sarc-space-3);
    }

    .hint {
      margin: var(--sarc-space-2) 0 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class ImportClassificationFilter {
  readonly options = input.required<readonly ClassificationOption[]>();
  readonly total = input.required<number>();
  /** Clasificacion elegida; '' = todas. */
  readonly selected = input.required<string>();
  readonly selectedChange = output<string>();

  protected readonly quantities = inject(QuantityFormat);
  protected readonly visible = computed(() => this.options().filter((option) => option.count > 0));
  protected readonly hint = computed(
    () => this.options().find((option) => option.value === this.selected())?.hint,
  );
}
