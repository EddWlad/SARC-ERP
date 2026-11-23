import { Component, inject, input, signal } from '@angular/core';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LucideMapPin, LucideX } from '@lucide/angular';

import { IncidentAssetResponse } from '../../../../core/api/generated';
import { NETWORK_LABELS, TYPE_LABELS } from '../../../../shared/vocabulary/charging-infrastructure';
import { TicketCreateFacade } from '../../application/ticket-create-facade';

/** Espera tras la ultima tecla antes de consultar al servidor. */
const SEARCH_DELAY_MS = 250;

/**
 * Busqueda de un equipo activo con autocompletado (FR-001, FR-002, FR-003). Como elemento afectado deja «Equipo
 * completo» por defecto y ofrece los conectores activos como casillas; como equipo de referencia de un elemento del
 * sitio muestra solo sus datos. Los datos del inventario (red, tipo, ubicacion y enlace de Maps) se muestran sin
 * escribirlos (SC-001). Lo provee la pagina de creacion.
 */
@Component({
  selector: 'sarc-asset-search',
  imports: [
    MatAutocompleteModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    LucideMapPin,
    LucideX,
  ],
  template: `
    <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
      <mat-label>{{ label() }}</mat-label>
      <input
        matInput
        type="search"
        placeholder="Nombre o código del equipo"
        autocomplete="off"
        [value]="text()"
        [matAutocomplete]="auto"
        (input)="onType($event)"
        (focus)="onFocus()"
      />
      @if (facade.asset()) {
        <button
          matIconButton
          matSuffix
          type="button"
          aria-label="Quitar el equipo"
          (click)="clear()"
        >
          <svg lucideX [size]="18" aria-hidden="true"></svg>
        </button>
      }
      <mat-autocomplete #auto="matAutocomplete" (optionSelected)="onSelect($event)">
        @for (option of facade.results(); track option.id) {
          <mat-option [value]="option">
            {{ option.name }}
            @if (option.assignedCode) {
              <span class="muted">· {{ option.assignedCode }}</span>
            }
            <span class="muted">· {{ networks[option.network] }}</span>
          </mat-option>
        } @empty {
          @if (!facade.searching()) {
            <mat-option disabled>No hay equipos activos con ese texto</mat-option>
          }
        }
      </mat-autocomplete>
    </mat-form-field>

    @if (facade.asset(); as asset) {
      <section class="asset" [attr.aria-label]="'Datos de ' + label().toLowerCase()">
        <dl class="facts">
          @if (connectors()) {
            <div>
              <dt>Red</dt>
              <dd>{{ networks[asset.network] }}</dd>
            </div>
            <div>
              <dt>Tipo</dt>
              <dd>{{ types[asset.infrastructureType] }}</dd>
            </div>
          }
          @if (asset.assignedCode) {
            <div>
              <dt>Código</dt>
              <dd>{{ asset.assignedCode }}</dd>
            </div>
          }
          <div class="wide">
            <dt>Ubicación</dt>
            <dd>
              @if (asset.location) {
                {{ asset.location }}
              } @else {
                <span class="muted">Sin ubicación en el inventario</span>
              }
              @if (asset.mapsUrl) {
                <a [href]="asset.mapsUrl" target="_blank" rel="noopener noreferrer" class="maps">
                  <svg lucideMapPin [size]="16" aria-hidden="true"></svg>
                  Ver en Google Maps
                </a>
              }
            </dd>
          </div>
        </dl>

        @if (connectors()) {
          <fieldset class="connectors">
            <legend>Afectación</legend>
            <mat-checkbox [checked]="facade.wholeEquipment()" (change)="facade.setWholeEquipment()">
              Equipo completo
            </mat-checkbox>
            @for (connector of facade.activeConnectors(); track connector.id) {
              <mat-checkbox
                [checked]="facade.connectorIds().includes(connector.id)"
                (change)="facade.toggleConnector(connector.id)"
              >
                Conector {{ connector.position }}
                <span class="muted">· {{ connector.typeLabel }}</span>
              </mat-checkbox>
            }
            <p class="hint">
              Una falla general se registra como un solo ticket del equipo completo; elija
              conectores solo si la falla es de ellos.
            </p>
          </fieldset>
        }
      </section>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
    }

    mat-form-field {
      width: 100%;
    }

    .muted {
      margin-inline-start: var(--sarc-space-1);
      color: var(--mat-sys-on-surface-variant);
    }

    dd .muted {
      margin-inline-start: 0;
    }

    .asset {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      padding: var(--sarc-space-4);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-medium);
    }

    .facts {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
      gap: var(--sarc-space-3);
      margin: 0;

      dt {
        color: var(--mat-sys-on-surface-variant);
        font: var(--mat-sys-label-medium);
      }

      dd {
        margin: var(--sarc-space-1) 0 0;
        overflow-wrap: anywhere;
      }

      .wide {
        grid-column: 1 / -1;
      }
    }

    .maps {
      display: inline-flex;
      align-items: center;
      gap: var(--sarc-space-1);
      margin-inline-start: var(--sarc-space-2);
      color: var(--mat-sys-primary);
    }

    .connectors {
      display: flex;
      flex-direction: column;
      margin: 0;
      padding: 0;
      border: 0;

      legend {
        padding: 0;
        font: var(--mat-sys-label-large);
      }
    }

    .hint {
      margin: var(--sarc-space-1) 0 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class AssetSearch {
  /** Etiqueta del campo: «Equipo afectado» o «Equipo de referencia del sitio». */
  readonly label = input('Equipo afectado');
  /** Si ofrece elegir conectores; un equipo de referencia no los tiene. */
  readonly connectors = input(true);

  protected readonly facade = inject(TicketCreateFacade);
  protected readonly networks = NETWORK_LABELS;
  protected readonly types = TYPE_LABELS;

  /** Texto del campo; al elegir un equipo muestra su nombre. */
  protected readonly text = signal('');
  private timer: ReturnType<typeof setTimeout> | undefined;

  protected onType(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.text.set(value);
    if (this.facade.asset() !== null) this.facade.clearAsset();
    clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.facade.searchAssets(value), SEARCH_DELAY_MS);
  }

  protected onFocus(): void {
    if (this.facade.results().length === 0 && this.facade.asset() === null) {
      void this.facade.searchAssets(this.text());
    }
  }

  protected onSelect(event: MatAutocompleteSelectedEvent): void {
    const asset = event.option.value as IncidentAssetResponse;
    this.text.set(asset.name);
    void this.facade.selectAsset(asset);
  }

  protected clear(): void {
    clearTimeout(this.timer);
    this.text.set('');
    this.facade.clearAsset();
  }
}
