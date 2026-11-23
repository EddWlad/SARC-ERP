import { Component, inject } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { TicketCreateFacade } from '../../application/ticket-create-facade';
import { SITE_ELEMENT_KIND_LABELS, SiteElementKind } from '../../domain/incident-labels';
import { AssetSearch } from '../asset-search/asset-search';

/**
 * Datos de un elemento del sitio, como una barrera vehicular (FR-001a): su clase, su nombre (obligatorio para «Otro
 * elemento») y su lugar, que es un equipo de referencia del inventario o un sitio escrito a mano con su ubicacion y un
 * enlace de Maps opcional. Del equipo de referencia solo se toman el nombre, la ubicacion y el Maps: nunca sus
 * conectores ni su red. Sin enlace de Maps no hay error.
 */
@Component({
  selector: 'sarc-site-element-fields',
  imports: [
    AssetSearch,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
      <mat-label>Clase de elemento</mat-label>
      <mat-select [value]="facade.siteKind()" (selectionChange)="onKind($event.value)">
        @for (kind of kinds; track kind) {
          <mat-option [value]="kind">{{ kindLabels[kind] }}</mat-option>
        }
      </mat-select>
    </mat-form-field>

    <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
      <mat-label>Nombre del elemento</mat-label>
      <input
        matInput
        autocomplete="off"
        maxlength="120"
        [required]="facade.siteKind() === 'OTHER'"
        [value]="facade.siteElementName()"
        (input)="facade.siteElementName.set($any($event.target).value)"
        (blur)="facade.refreshMatches()"
      />
      <mat-hint>
        {{
          facade.siteKind() === 'OTHER'
            ? 'Obligatorio. Por ejemplo: Portón peatonal.'
            : 'Opcional. Por ejemplo: Entrada o Salida.'
        }}
      </mat-hint>
    </mat-form-field>

    <div class="place" role="group" aria-labelledby="site-place-label">
      <span id="site-place-label" class="place-label">Lugar del elemento</span>
      <mat-button-toggle-group
        hideSingleSelectionIndicator
        aria-labelledby="site-place-label"
        [value]="facade.siteLocationMode()"
        (change)="facade.setSiteLocationMode($event.value)"
      >
        <mat-button-toggle value="REFERENCE">Equipo de referencia</mat-button-toggle>
        <mat-button-toggle value="MANUAL">Sitio escrito a mano</mat-button-toggle>
      </mat-button-toggle-group>
    </div>

    @if (facade.siteLocationMode() === 'REFERENCE') {
      <sarc-asset-search label="Equipo de referencia del sitio" [connectors]="false" />
      <p class="note">
        Del equipo se toman el sitio, la ubicación y el enlace de Maps. El ticket no es del equipo.
      </p>
    } @else {
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Nombre del sitio</mat-label>
        <input
          matInput
          autocomplete="off"
          maxlength="150"
          [value]="facade.manualSiteName()"
          (input)="facade.manualSiteName.set($any($event.target).value)"
          (blur)="facade.refreshMatches()"
        />
      </mat-form-field>
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Ubicación</mat-label>
        <input
          matInput
          autocomplete="off"
          maxlength="300"
          [value]="facade.manualLocation()"
          (input)="facade.manualLocation.set($any($event.target).value)"
        />
      </mat-form-field>
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Enlace de Google Maps</mat-label>
        <input
          matInput
          type="url"
          autocomplete="off"
          maxlength="500"
          placeholder="https://"
          [value]="facade.manualMapsUrl()"
          (input)="facade.manualMapsUrl.set($any($event.target).value)"
        />
        <mat-hint>Opcional. Debe empezar con https://.</mat-hint>
      </mat-form-field>
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

    .place {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
    }

    .place-label {
      font: var(--mat-sys-label-large);
    }

    mat-button-toggle-group {
      --mat-button-toggle-shape: var(--mat-sys-corner-medium);
      width: 100%;
      flex-wrap: wrap;
    }

    mat-button-toggle {
      flex: 1 1 auto;
    }

    .note {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class SiteElementFields {
  protected readonly facade = inject(TicketCreateFacade);

  protected readonly kinds = Object.keys(SITE_ELEMENT_KIND_LABELS) as SiteElementKind[];
  protected readonly kindLabels = SITE_ELEMENT_KIND_LABELS;

  protected onKind(kind: SiteElementKind): void {
    this.facade.siteKind.set(kind);
    void this.facade.refreshMatches();
  }
}
