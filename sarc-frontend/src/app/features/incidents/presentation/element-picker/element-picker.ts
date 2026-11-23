import { Component, inject } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { TicketCreateFacade } from '../../application/ticket-create-facade';
import {
  CHATBOT_SCOPE_LABELS,
  ChatbotScope,
  ELEMENT_CLASS_LABELS,
  ElementClass,
} from '../../domain/incident-labels';
import { AssetSearch } from '../asset-search/asset-search';
import { SiteElementFields } from '../site-element-fields/site-element-fields';

/**
 * Selector del elemento afectado (FR-001, FR-001a, FR-005): primero la clase (equipo, servicio de chatbot o elemento
 * del sitio) y despues solo los datos que esa clase tiene. Un equipo busca en el inventario y ofrece sus conectores; el
 * chatbot solo admite un alcance opcional; un elemento del sitio pide su clase, su nombre y su lugar. Nada se hereda
 * de la clase anterior ni se inventa un equipo. Lo provee la pagina de creacion.
 */
@Component({
  selector: 'sarc-element-picker',
  imports: [
    AssetSearch,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    SiteElementFields,
  ],
  template: `
    <div class="class-picker" role="group" aria-labelledby="element-class-label">
      <span id="element-class-label" class="class-label">Qué se ve afectado</span>
      <mat-button-toggle-group
        hideSingleSelectionIndicator
        aria-labelledby="element-class-label"
        [value]="facade.elementClass()"
        (change)="facade.setElementClass($event.value)"
      >
        @for (option of classes; track option) {
          <mat-button-toggle [value]="option">{{ classLabels[option] }}</mat-button-toggle>
        }
      </mat-button-toggle-group>
    </div>

    @switch (facade.elementClass()) {
      @case ('EQUIPMENT') {
        <sarc-asset-search />
      }
      @case ('CHATBOT_SERVICE') {
        <p class="note">
          Falla del propio servicio de chatbot. No se asocia a ningún equipo, conector ni red.
        </p>
        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Alcance de la falla</mat-label>
          <mat-select
            [value]="facade.chatbotScope()"
            (selectionChange)="facade.chatbotScope.set($event.value)"
          >
            <mat-option value="">Sin indicar</mat-option>
            @for (scope of scopes; track scope) {
              <mat-option [value]="scope">{{ scopeLabels[scope] }}</mat-option>
            }
          </mat-select>
          <mat-hint>Opcional.</mat-hint>
        </mat-form-field>
      }
      @case ('SITE_ELEMENT') {
        <sarc-site-element-fields />
      }
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
    }

    .class-picker {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
    }

    .class-label {
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

    mat-form-field {
      width: 100%;
    }

    .note {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class ElementPicker {
  protected readonly facade = inject(TicketCreateFacade);

  protected readonly classes = Object.keys(ELEMENT_CLASS_LABELS) as ElementClass[];
  protected readonly classLabels = ELEMENT_CLASS_LABELS;
  protected readonly scopes = Object.keys(CHATBOT_SCOPE_LABELS) as ChatbotScope[];
  protected readonly scopeLabels = CHATBOT_SCOPE_LABELS;
}
