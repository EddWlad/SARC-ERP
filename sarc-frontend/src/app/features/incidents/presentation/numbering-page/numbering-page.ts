import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { form, FormField, required, submit, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { LucideLock, LucideTriangleAlert } from '@lucide/angular';

import { NumberingFacade, parseStartNumber } from '../../application/numbering-facade';
import { NUMBERING_ENVIRONMENT_LABELS } from '../../domain/incident-labels';

interface NumberingFormValue {
  startNumber: string;
  confirmation: string;
}

/**
 * Configuracion del numero inicial de tickets (FR-013). Explica el bloqueo, pide el numero y la confirmacion del
 * codigo resultante, muestra el ambiente que exige el servidor y queda en solo lectura una vez configurada. No hay
 * numero inicial por defecto: el de produccion lo define el responsable del producto antes de la puesta en marcha.
 */
@Component({
  selector: 'sarc-numbering-page',
  imports: [
    FormField,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    LucideLock,
    LucideTriangleAlert,
  ],
  providers: [NumberingFacade],
  template: `
    <header class="page-header">
      <div>
        <h1>Numeración de tickets</h1>
        <p>
          Define el primer número de la secuencia <strong>TICKET-NNN</strong>. Mientras no esté
          configurada, no se pueden crear tickets.
        </p>
      </div>
    </header>

    @if (facade.loading() && !facade.state()) {
      <div class="state" role="status">
        <mat-spinner diameter="32" />
        <p>Cargando la numeración…</p>
      </div>
    } @else if (facade.state(); as state) {
      @if (facade.serverWithoutEnvironment()) {
        <div class="alert" role="alert">
          <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
          <p>
            El servidor no tiene configurado el ambiente de la numeración (producción o prueba).
            Mientras falte, no se puede configurar la numeración ni crear tickets. Pida al
            responsable técnico que lo configure.
          </p>
        </div>
      }

      @if (state.configured) {
        <section class="panel" aria-labelledby="configured-title">
          <h2 id="configured-title">
            <svg lucideLock [size]="20" aria-hidden="true"></svg>
            Numeración configurada
          </h2>
          <dl class="summary">
            <div>
              <dt>Ambiente</dt>
              <dd>{{ environmentLabel(state.environment) }}</dd>
            </div>
            <div>
              <dt>Número inicial</dt>
              <dd>{{ state.startNumber }}</dd>
            </div>
            <div>
              <dt>Próximo código</dt>
              <dd>{{ state.nextCode }}</dd>
            </div>
          </dl>
          <p class="note">
            El número inicial no se puede cambiar. El próximo código es informativo: el número se
            asigna al crear cada ticket y nunca se reutiliza.
          </p>
        </section>
      } @else {
        <section class="panel" aria-labelledby="pending-title">
          <h2 id="pending-title">Sin configurar</h2>
          <p class="note">
            Una vez creado el primer ticket, el número inicial ya no se puede cambiar. Confirme el
            código que resulta para evitar un error de digitación.
          </p>
          <form novalidate (submit)="onSubmit($event)">
            @if (facade.error(); as message) {
              <div class="alert" role="alert">
                <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
                <p>{{ message }}</p>
              </div>
            }
            <div class="fields">
              <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                <mat-label>Ambiente</mat-label>
                <input
                  matInput
                  readonly
                  [value]="environmentLabel(facade.expectedEnvironment())"
                  aria-describedby="environment-hint"
                />
                <mat-hint id="environment-hint">Lo define el servidor.</mat-hint>
              </mat-form-field>

              <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                <mat-label>Número inicial</mat-label>
                <input
                  matInput
                  inputmode="numeric"
                  autocomplete="off"
                  [formField]="numberingForm.startNumber"
                />
                @for (error of numberingForm.startNumber().errors(); track error.kind) {
                  <mat-error>{{ error.message }}</mat-error>
                }
              </mat-form-field>

              <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                <mat-label>Confirmar el código</mat-label>
                <input
                  matInput
                  autocomplete="off"
                  [placeholder]="expectedCode() ?? 'TICKET-NNN'"
                  [formField]="numberingForm.confirmation"
                />
                @if (expectedCode(); as code) {
                  <mat-hint>Escriba {{ code }} para confirmar.</mat-hint>
                }
                @for (error of numberingForm.confirmation().errors(); track error.kind) {
                  <mat-error>{{ error.message }}</mat-error>
                }
              </mat-form-field>
            </div>
            <div class="actions">
              <button
                matButton="filled"
                type="submit"
                [disabled]="facade.saving() || facade.serverWithoutEnvironment()"
              >
                {{ facade.saving() ? 'Guardando…' : 'Configurar numeración' }}
              </button>
            </div>
          </form>
        </section>
      }
    } @else if (facade.error(); as message) {
      <div class="state" role="alert">
        <p>{{ message }}</p>
        <button matButton type="button" (click)="facade.load()">Reintentar</button>
      </div>
    }
  `,
  styles: `
    @use '../../../../shared/ui/page-chrome';

    :host {
      display: block;
    }

    .panel {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      max-width: 40rem;
      padding: var(--sarc-space-5);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-medium);
      background: var(--mat-sys-surface);

      h2 {
        display: flex;
        align-items: center;
        gap: var(--sarc-space-2);
        margin: 0;
        font: var(--mat-sys-title-medium);
      }
    }

    .note {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }

    .fields {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);

      /* El ejemplo del campo no debe parecer texto ya escrito. */
      .mat-mdc-input-element::placeholder {
        color: color-mix(in srgb, var(--mat-sys-on-surface-variant) 50%, transparent);
        opacity: 1;
      }
    }

    .actions {
      display: flex;
      justify-content: flex-end;
      margin-top: var(--sarc-space-4);
    }

    .alert {
      display: flex;
      gap: var(--sarc-space-3);
      align-items: flex-start;
      max-width: 40rem;
      margin-bottom: var(--sarc-space-4);
      padding: var(--sarc-space-3) var(--sarc-space-4);
      border-radius: var(--mat-sys-corner-small);
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);

      p {
        margin: 0;
      }
    }

    .summary {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
      gap: var(--sarc-space-4);
      margin: 0;

      dt {
        color: var(--mat-sys-on-surface-variant);
        font: var(--mat-sys-label-large);
      }

      dd {
        margin: var(--sarc-space-1) 0 0;
        font: var(--mat-sys-title-medium);
      }
    }
  `,
})
export class NumberingPage implements OnInit {
  protected readonly facade = inject(NumberingFacade);

  protected readonly model = signal<NumberingFormValue>({ startNumber: '', confirmation: '' });
  protected readonly numberingForm = form(this.model, (path) => {
    required(path.startNumber, { message: 'Ingrese el número inicial' });
    validate(path.startNumber, ({ value }) =>
      value() === '' || parseStartNumber(value()) !== null
        ? undefined
        : { kind: 'startNumber', message: 'Debe ser un entero de 1 a 999.999.999' },
    );
    required(path.confirmation, { message: 'Confirme el código resultante' });
  });

  /** El codigo que resulta del numero escrito; nulo mientras no es valido. */
  protected readonly expectedCode = computed(() => {
    const start = parseStartNumber(this.model().startNumber);
    return start === null ? null : this.facade.codeFor(start);
  });

  ngOnInit(): void {
    void this.facade.load();
  }

  protected environmentLabel(environment: string | null | undefined): string {
    return environment === 'PRODUCTION' || environment === 'TEST'
      ? NUMBERING_ENVIRONMENT_LABELS[environment]
      : 'Sin definir';
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.numberingForm, async () => {
      const start = parseStartNumber(this.model().startNumber);
      const code = this.expectedCode();
      if (start === null || code === null) return;
      if (this.model().confirmation.trim().toUpperCase() !== code) {
        this.facade.error.set(`La confirmación debe repetir el código resultante: ${code}.`);
        return;
      }
      await this.facade.configure(start, this.model().confirmation);
    });
  }
}
