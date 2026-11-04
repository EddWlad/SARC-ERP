import {
  Component,
  computed,
  ElementRef,
  inject,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { CaptureFacade } from '../../application/capture-facade';

/** Lectura de un conector. El formulario se reinicia con cada conector de la secuencia. */
@Component({
  selector: 'sarc-measurement-form',
  imports: [FormField, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Registrar medición</h2>
    <form novalidate (submit)="onSubmit($event, false)">
      <mat-dialog-content>
        @if (facade.current(); as connector) {
          <p class="target">
            {{ connector.equipmentName }} · Conector {{ connector.ordinal }} ·
            {{ connector.connectorTypeName }}
          </p>
        }
        <div class="block">
          <mat-form-field appearance="outline">
            <mat-label>Pegar bloque de Enerlink (opcional)</mat-label>
            <textarea #firstField matInput rows="3" [formField]="block.text"></textarea>
          </mat-form-field>
          <button
            matButton="tonal"
            type="button"
            [disabled]="facade.busy() || !blockText().text.trim()"
            (click)="onParse()"
          >
            Interpretar
          </button>
        </div>
        <div class="fields">
          <mat-form-field appearance="outline">
            <mat-label>Disponible (min)</mat-label>
            <input matInput inputmode="decimal" [formField]="measurement.available" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Ocupado (min)</mat-label>
            <input matInput inputmode="decimal" [formField]="measurement.occupied" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Con falla (min)</mat-label>
            <input matInput inputmode="decimal" [formField]="measurement.fault" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Fuera de línea (min)</mat-label>
            <input matInput inputmode="decimal" [formField]="measurement.offline" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Porcentaje de Enerlink (opcional)</mat-label>
            <input matInput inputmode="decimal" [formField]="measurement.reportedPercent" />
          </mat-form-field>
        </div>
        @if (nonNumeric()) {
          <div class="form-alert" role="alert"><p>Ingrese solo números en los tiempos.</p></div>
        }
        @if (facade.conflict()) {
          <mat-form-field appearance="outline" class="justification">
            <mat-label>Justificación de la corrección</mat-label>
            <textarea matInput rows="2" [formField]="correction.text"></textarea>
          </mat-form-field>
        }
        @if (facade.notice(); as notice) {
          <p class="note" role="status">Quedó observada: {{ notice }}</p>
        }
        @if (facade.error(); as error) {
          <div class="form-alert" role="alert">
            <p>{{ error }}</p>
          </div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="facade.busy()" (click)="unsaved.cancel()">
          Cerrar
        </button>
        @if (facade.conflict()) {
          <button
            matButton="filled"
            type="button"
            [disabled]="facade.busy() || !justification().text.trim()"
            (click)="onCorrect()"
          >
            Guardar como corrección
          </button>
        } @else {
          <button matButton="outlined" type="submit" [disabled]="facade.busy()">Guardar</button>
          <button
            matButton="filled"
            type="button"
            [disabled]="facade.busy()"
            (click)="onSubmit($event, true)"
          >
            Guardar y siguiente
          </button>
        }
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    .target {
      margin: 0 0 var(--sarc-space-3);
      font: var(--mat-sys-title-small);
      overflow-wrap: anywhere;
    }

    .block {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: start;
      gap: var(--sarc-space-3);

      @media (max-width: 599.98px) {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    .fields {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--sarc-space-2) var(--sarc-space-4);
      min-width: min(480px, 75vw);

      @media (max-width: 599.98px) {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    .wide {
      grid-column: 1 / -1;
    }

    .justification {
      width: 100%;
      margin-top: var(--sarc-space-3);
    }

    .note,
    .form-alert {
      margin-top: var(--sarc-space-2);
    }
  `,
})
export class MeasurementForm {
  /** Llega por datos: el dialogo vive fuera del arbol que provee la fachada de la pagina. */
  readonly facade = inject<CaptureFacade>(MAT_DIALOG_DATA);
  private readonly dialog = inject(MatDialogRef);
  private readonly firstField = viewChild.required<ElementRef<HTMLElement>>('firstField');
  /** Se reinicia al cambiar el conector porque el borrador de la fachada vuelve a blanco. */
  readonly model = linkedSignal(() => this.facade.draft());
  readonly measurement = form(this.model, (path) => {
    required(path.available, { message: 'Requerido' });
    required(path.occupied, { message: 'Requerido' });
    required(path.fault, { message: 'Requerido' });
    required(path.offline, { message: 'Requerido' });
  });
  protected readonly justification = signal({ text: '' });
  protected readonly correction = form(this.justification, (path) => maxLength(path.text, 500));
  protected readonly blockText = signal({ text: '' });
  protected readonly block = form(this.blockText, (path) => maxLength(path.text, 4000));
  protected readonly unsaved = guardUnsavedChanges(
    () => this.measurement().dirty() || this.block.text().dirty(),
  );
  protected readonly nonNumeric = computed(() => {
    const { available, occupied, fault, offline } = this.model();
    return [available, occupied, fault, offline].some(
      (value) => value !== '' && Number.isNaN(Number(value)),
    );
  });

  async onParse(): Promise<void> {
    await this.facade.parseBlock(this.blockText().text.trim());
  }

  async onCorrect(): Promise<void> {
    if (this.nonNumeric()) return;
    this.facade.updateDraft(this.model());
    if (await this.facade.saveCorrection(this.justification().text.trim())) {
      this.measurement().reset();
      this.dialog.close();
    }
  }

  onSubmit(event: Event, advance: boolean): void {
    event.preventDefault();
    if (this.nonNumeric()) return;
    void submit(this.measurement, async () => {
      this.facade.updateDraft(this.model());
      const saved = await this.facade.save(advance);
      if (saved) {
        this.measurement().reset();
        this.blockText.set({ text: '' });
        this.block().reset();
        if (!advance || !this.facade.current()) this.dialog.close();
        // El boton pulsado se deshabilita al guardar y el foco caeria fuera del dialogo.
        else this.firstField().nativeElement.focus();
      }
    });
  }
}
