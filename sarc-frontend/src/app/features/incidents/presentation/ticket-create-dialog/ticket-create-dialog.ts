import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { LucideChevronDown, LucideChevronUp, LucideTriangleAlert } from '@lucide/angular';

import { SessionStore } from '../../../../core/auth/session-store';
import { Notifier } from '../../../../core/notifications/notifier';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { TicketCreateFacade } from '../../application/ticket-create-facade';
import {
  INCIDENT_TYPES_BY_CLASS,
  INCIDENT_TYPE_LABELS,
  IncidentType,
  PRIORITY_LABELS,
  Priority,
  EVIDENCE_ACCEPT,
  EVIDENCE_MAX_BYTES,
  EVIDENCE_PRIVACY_REMINDER,
  fileSizeText,
  REPORT_ORIGIN_LABELS,
  ReportOrigin,
} from '../../domain/incident-labels';
import { INCIDENT_NUMBERING } from '../../domain/incident-permissions';
import { isRetroactiveLocal, operatingInstant } from '../../domain/incident-time';
import { ElementPicker } from '../element-picker/element-picker';

interface TicketFormValue {
  incidentType: IncidentType | '';
  description: string;
  reportOrigin: ReportOrigin;
  reportOriginDetail: string;
  priority: Priority | '';
  priorityReason: string;
  /** `aaaa-mm-ddThh:mm` en la zona operativa; vacio es el momento de creacion. */
  t0: string;
  t0Reason: string;
  overrideReason: string;
}

export { operatingInstant };

/**
 * Registrar un ticket en un diálogo, como «Nuevo equipo» en Inventario: el elemento afectado, el tipo y la descripción
 * (FR-010, SC-001). «Más datos» despliega el origen, la prioridad, T0 y la evidencia inicial. Si el elemento ya tiene
 * tickets abiertos, avisa en línea y ofrece agregar el seguimiento a uno de ellos o crear de todos modos con un motivo
 * (FR-016). Al crear cierra y abre el detalle del ticket nuevo.
 */
@Component({
  selector: 'sarc-ticket-create-dialog',
  imports: [
    FormField,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    LucideChevronDown,
    LucideChevronUp,
    LucideTriangleAlert,
    ElementPicker,
  ],
  providers: [TicketCreateFacade],
  template: `
    <h2 mat-dialog-title>Registrar ticket</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        <p class="note">
          Qué se ve afectado, el tipo de incidencia y una descripción breve. El resto se completa
          solo.
        </p>

        @if (facade.numberingBlock(); as block) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <div>
              <p>
                @if (block === 'NOT_CONFIGURED') {
                  Todavía no se configuró el número inicial de tickets, así que no se pueden crear.
                } @else {
                  El ambiente de la numeración no coincide con el del servidor; no se pueden crear
                  tickets.
                }
              </p>
              @if (canConfigureNumbering) {
                <a class="alert-link" routerLink="/incidencias/numeracion" (click)="close()">
                  Ir a la configuración de la numeración
                </a>
              } @else {
                <p>Pida a un Administrador que lo configure.</p>
              }
            </div>
          </div>
        } @else if (facade.error(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <p>{{ message }}</p>
          </div>
        }

        <sarc-element-picker />

        @if (facade.hasMatches()) {
          <section class="duplicate" role="alert" aria-labelledby="duplicate-title">
            <h3 id="duplicate-title">{{ duplicateTitle() }}</h3>
            <ul>
              @for (match of facade.matches(); track match.id) {
                <li>
                  <strong>{{ match.code }}</strong> · {{ match.incidentTypeLabel }}
                  <a [routerLink]="['/incidencias/tickets', match.id]" (click)="close()">
                    Agregar seguimiento al ticket
                  </a>
                </li>
              }
            </ul>
            <mat-checkbox [checked]="createAnyway()" (change)="onCreateAnyway($event.checked)">
              Crear de todos modos
            </mat-checkbox>
            @if (createAnyway()) {
              <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                <mat-label>Motivo para crear otro ticket</mat-label>
                <input matInput autocomplete="off" [formField]="ticketForm.overrideReason" />
                @for (error of ticketForm.overrideReason().errors(); track error.kind) {
                  <mat-error>{{ error.message }}</mat-error>
                }
              </mat-form-field>
            }
          </section>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Tipo de incidencia</mat-label>
          <mat-select [formField]="ticketForm.incidentType">
            @for (type of types(); track type) {
              <mat-option [value]="type">{{ typeLabels[type] }}</mat-option>
            }
          </mat-select>
          @for (error of ticketForm.incidentType().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Descripción breve</mat-label>
          <textarea matInput rows="3" [formField]="ticketForm.description"></textarea>
          <mat-hint align="end">{{ model().description.length }} / 500</mat-hint>
          @for (error of ticketForm.description().errors(); track error.kind) {
            <mat-error>{{ error.message }}</mat-error>
          }
        </mat-form-field>

        <button
          class="more-toggle"
          matButton
          type="button"
          [attr.aria-expanded]="showMore()"
          aria-controls="more-data"
          (click)="showMore.set(!showMore())"
        >
          @if (showMore()) {
            <svg lucideChevronUp [size]="18" aria-hidden="true"></svg>
          } @else {
            <svg lucideChevronDown [size]="18" aria-hidden="true"></svg>
          }
          Más datos
        </button>

        @if (showMore()) {
          <section id="more-data" class="more" aria-label="Más datos">
            <div class="pair">
              <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                <mat-label>Origen del aviso</mat-label>
                <mat-select [formField]="ticketForm.reportOrigin">
                  @for (origin of origins; track origin) {
                    <mat-option [value]="origin">{{ originLabels[origin] }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>

              <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                <mat-label>Prioridad</mat-label>
                <mat-select [formField]="ticketForm.priority">
                  <mat-option value="">Propuesta según el tipo</mat-option>
                  @for (priority of priorities; track priority) {
                    <mat-option [value]="priority">{{ priorityLabels[priority] }}</mat-option>
                  }
                </mat-select>
                <mat-hint>Cambiarla exige un motivo.</mat-hint>
              </mat-form-field>
            </div>

            @if (model().reportOrigin === 'OTHER') {
              <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                <mat-label>Detalle del origen</mat-label>
                <input matInput autocomplete="off" [formField]="ticketForm.reportOriginDetail" />
                @for (error of ticketForm.reportOriginDetail().errors(); track error.kind) {
                  <mat-error>{{ error.message }}</mat-error>
                }
              </mat-form-field>
            }

            @if (model().priority !== '') {
              <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                <mat-label>Motivo de la prioridad</mat-label>
                <input matInput autocomplete="off" [formField]="ticketForm.priorityReason" />
                @for (error of ticketForm.priorityReason().errors(); track error.kind) {
                  <mat-error>{{ error.message }}</mat-error>
                }
              </mat-form-field>
            }

            <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Hora de detección (T0)</mat-label>
              <input matInput type="datetime-local" [formField]="ticketForm.t0" />
              <mat-hint>Vacía, es el momento de creación. Hora de Ecuador.</mat-hint>
            </mat-form-field>

            @if (retroactive()) {
              <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                <mat-label>Motivo de la hora anterior</mat-label>
                <input matInput autocomplete="off" [formField]="ticketForm.t0Reason" />
                @for (error of ticketForm.t0Reason().errors(); track error.kind) {
                  <mat-error>{{ error.message }}</mat-error>
                }
              </mat-form-field>
            }

            <div class="evidence">
              <span class="evidence-title">Evidencia inicial</span>
              <p class="note">{{ privacyReminder }}</p>
              @if (evidenceError(); as message) {
                <div class="form-alert" role="alert">
                  <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
                  <p>{{ message }}</p>
                </div>
              }
              <div class="evidence-controls">
                <input
                  #picker
                  class="sarc-visually-hidden"
                  type="file"
                  tabindex="-1"
                  aria-hidden="true"
                  [accept]="evidenceAccept"
                  (change)="onEvidencePicked(picker)"
                />
                <button matButton="tonal" type="button" (click)="picker.click()">
                  {{ facade.evidenceFile() ? 'Cambiar archivo' : 'Elegir archivo' }}
                </button>
                @if (facade.evidenceFile(); as file) {
                  <span class="file-name">{{ file.name }} · {{ fileSize(file.size) }}</span>
                  <button matButton type="button" (click)="facade.evidenceFile.set(null)">
                    Quitar
                  </button>
                } @else {
                  <span class="note">Imagen JPEG, PNG o WEBP, o PDF. Hasta 10 MB. Opcional.</span>
                }
              </div>
            </div>
          </section>
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="facade.submitting()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="facade.submitting() || blocked()">
          {{ facade.submitting() ? 'Creando…' : 'Crear ticket' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    .fields,
    .more,
    .evidence {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
    }

    // Los campos cortos van de a dos; en pantallas angostas se apilan.
    .pair {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--sarc-space-3);

      @media (max-width: 599.98px) {
        grid-template-columns: 1fr;
      }
    }

    .more-toggle {
      align-self: flex-start;
    }

    .evidence {
      gap: var(--sarc-space-2);
    }

    .evidence-title {
      font: var(--mat-sys-label-large);
    }

    .evidence-controls {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-2) var(--sarc-space-3);
    }

    .file-name {
      overflow-wrap: anywhere;
    }

    // El enlace va sobre el fondo de error: hereda su color para mantener el contraste.
    .alert-link {
      color: inherit;
      font-weight: 500;
    }

    .duplicate {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      padding: var(--sarc-space-3) var(--sarc-space-4);
      border: 1px solid var(--mat-sys-outline-variant);
      border-inline-start: 4px solid var(--mat-sys-tertiary);
      border-radius: var(--mat-sys-corner-small);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface);

      h3 {
        margin: 0;
        font: var(--mat-sys-title-small);
      }

      ul {
        margin: 0;
        padding-inline-start: var(--sarc-space-4);
      }

      a {
        margin-inline-start: var(--sarc-space-2);
        color: inherit;
        font-weight: 500;
      }
    }
  `,
})
export class TicketCreateDialog {
  protected readonly facade = inject(TicketCreateFacade);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly dialogRef = inject<MatDialogRef<TicketCreateDialog, string>>(MatDialogRef);
  protected readonly canConfigureNumbering = inject(SessionStore).hasAnyRole(INCIDENT_NUMBERING);

  /** Esc, clic fuera o Cancelar no descartan lo escrito sin confirmarlo. */
  protected readonly unsaved = guardUnsavedChanges(
    () => this.ticketForm().dirty() || this.facade.asset() !== null,
  );

  /** Los tipos de la clase elegida (FR-022): el chatbot y el sitio no ofrecen los de un cargador. */
  protected readonly types = computed(() => INCIDENT_TYPES_BY_CLASS[this.facade.elementClass()]);
  protected readonly duplicateTitle = computed(() => {
    switch (this.facade.elementClass()) {
      case 'EQUIPMENT':
        return 'Este equipo ya tiene tickets abiertos';
      case 'CHATBOT_SERVICE':
        return 'El servicio de chatbot ya tiene tickets abiertos';
      case 'SITE_ELEMENT':
        return 'Este elemento del sitio ya tiene tickets abiertos';
    }
  });
  protected readonly typeLabels = INCIDENT_TYPE_LABELS;
  protected readonly origins = Object.keys(REPORT_ORIGIN_LABELS) as ReportOrigin[];
  protected readonly originLabels = REPORT_ORIGIN_LABELS;
  protected readonly priorities = Object.keys(PRIORITY_LABELS) as Priority[];
  protected readonly priorityLabels = PRIORITY_LABELS;

  protected readonly showMore = signal(false);
  protected readonly createAnyway = signal(false);

  protected readonly evidenceAccept = EVIDENCE_ACCEPT;
  protected readonly privacyReminder = EVIDENCE_PRIVACY_REMINDER;
  protected readonly fileSize = fileSizeText;
  protected readonly evidenceError = signal('');

  protected readonly model = signal<TicketFormValue>({
    incidentType: '',
    description: '',
    reportOrigin: 'PLATFORM',
    reportOriginDetail: '',
    priority: '',
    priorityReason: '',
    t0: '',
    t0Reason: '',
    overrideReason: '',
  });

  /** T0 es mas de un minuto anterior a ahora: exige un motivo (FR-018). */
  protected readonly retroactive = computed(() => {
    return isRetroactiveLocal(this.model().t0);
  });

  /** Con tickets abiertos, solo se crea eligiendo «Crear de todos modos». */
  protected readonly blocked = computed(() => this.facade.hasMatches() && !this.createAnyway());

  protected readonly ticketForm = form(this.model, (path) => {
    required(path.incidentType, { message: 'Seleccione el tipo de incidencia' });
    required(path.description, { message: 'Escriba una descripción breve' });
    maxLength(path.description, 500, { message: 'Máximo 500 caracteres' });
    required(path.reportOriginDetail, {
      when: ({ valueOf }) => valueOf(path.reportOrigin) === 'OTHER',
      message: 'Indique el detalle del origen',
    });
    required(path.priorityReason, {
      when: ({ valueOf }) => valueOf(path.priority) !== '',
      message: 'Indique el motivo de la prioridad',
    });
    required(path.t0Reason, {
      when: () => this.retroactive(),
      message: 'Una hora anterior exige un motivo',
    });
    required(path.overrideReason, {
      when: () => this.createAnyway(),
      message: 'Indique el motivo para crear otro ticket',
    });
  });

  constructor() {
    // Al cambiar de clase, el tipo elegido deja de valer si no es de esa clase; el chatbot y el sitio proponen el suyo
    // y el aviso de «crear de todos modos» de la clase anterior no se arrastra.
    effect(() => {
      const allowed = this.types();
      const equipment = this.facade.elementClass() === 'EQUIPMENT';
      untracked(() => {
        this.model.update((value) =>
          value.incidentType !== '' && allowed.includes(value.incidentType)
            ? value
            : { ...value, incidentType: equipment ? '' : allowed[0] },
        );
        this.createAnyway.set(false);
      });
    });
  }

  protected onEvidencePicked(input: HTMLInputElement): void {
    const file = input.files?.item(0);
    input.value = '';
    if (!file) return;
    // El servidor decide por el contenido real; esto solo evita crear el ticket para luego rechazar el archivo.
    if (file.size === 0) {
      this.evidenceError.set('El archivo está vacío.');
    } else if (file.size > EVIDENCE_MAX_BYTES) {
      this.evidenceError.set('El archivo supera el límite de 10 MB.');
    } else {
      this.evidenceError.set('');
      this.facade.evidenceFile.set(file);
    }
  }

  protected onCreateAnyway(checked: boolean): void {
    this.createAnyway.set(checked);
  }

  /** Cierra sin pedir confirmación: se usa al salir hacia otra pantalla (el ticket abierto o la numeración). */
  protected close(): void {
    this.dialogRef.close();
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.ticketForm, async () => {
      const value = this.model();
      if (value.incidentType === '') return;
      const created = await this.facade.submit({
        incidentType: value.incidentType,
        description: value.description,
        reportOrigin: value.reportOrigin,
        reportOriginDetail: value.reportOrigin === 'OTHER' ? value.reportOriginDetail : undefined,
        priority: value.priority === '' ? undefined : value.priority,
        priorityReason: value.priority === '' ? undefined : value.priorityReason,
        t0: operatingInstant(value.t0) ?? undefined,
        t0Reason: this.retroactive() ? value.t0Reason : undefined,
        duplicateOverrideReason: this.createAnyway() ? value.overrideReason : undefined,
      });
      if (created === null) return;
      this.notifier.success(`Ticket ${created.summary.code} creado`);
      if (this.facade.evidenceWarning() !== '') {
        this.notifier.error(
          `La evidencia inicial no se pudo cargar: ${this.facade.evidenceWarning()} Adjúntela desde el detalle del ticket.`,
        );
      }
      this.dialogRef.close(created.summary.id);
      await this.router.navigate(['/incidencias/tickets', created.summary.id]);
    });
  }
}
