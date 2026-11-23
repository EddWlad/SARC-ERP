import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, maxLength } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import {
  CloseRequest,
  TicketCommunicationResponse,
  TicketEpisode,
  TicketEvidenceResponse,
  TicketSummaryResponse,
} from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import {
  CAUSE_KIND_LABELS,
  CauseKind,
  CLOSURE_CHECK_KEYS,
  CLOSURE_CHECK_LABELS,
  CLOSURE_CHECK_FIELD_LABELS,
  CLOSURE_MISSING_LABELS,
  CLOSURE_RESULT_LABELS,
  ClosureCheck,
  ClosureCheckKey,
  closureMissing,
  ClosureMissing,
  ClosureResult,
  isExcludedResult,
} from '../../domain/incident-closure';
import { ElementClass, MilestoneStatus } from '../../domain/incident-labels';
import { isRetroactiveLocal, operatingInstant } from '../../domain/incident-time';

export type CloseResult = Omit<CloseRequest, 'version'>;

export interface CloseDialogData {
  readonly elementClass: ElementClass;
  /** El episodio abierto: sus hitos y su juicio frente a la referencia. */
  readonly episode: TicketEpisode;
  readonly evidence: readonly TicketEvidenceResponse[];
  readonly communications: readonly TicketCommunicationResponse[];
  /** Busca tickets para elegir el original de un duplicado. */
  readonly searchTickets: (text: string) => Promise<TicketSummaryResponse[]>;
  readonly save: (result: CloseResult) => Promise<void>;
}

interface CloseFormValue {
  /** `aaaa-mm-ddThh:mm` en la zona operativa; vacío es el momento actual (FR-041). */
  t4: string;
  t4Reason: string;
  result: ClosureResult | '';
  causeKind: CauseKind | '';
  causeText: string;
  recommendations: string;
  platformNormal: ClosureCheck | '';
  stabilityChecked: ClosureCheck | '';
  noAffectedUser: ClosureCheck | '';
  postTest: ClosureCheck | '';
  finalEvidenceWaiver: string;
  closureCommunicationWaiver: string;
  deviationJustification: string;
}

/** Código del servidor cuando faltan requisitos; trae la lista completa en `missing`. */
const CLOSE_REQUIREMENTS = 'TICKET_CLOSE_REQUIREMENTS';

/**
 * Cierra el ticket (FR-047, FR-048, FR-048a). Propone T4 con la hora actual sin escribirla (SC-002) y admite una hora
 * anterior con su motivo. Pide solo lo que corresponde a la clase del elemento: un equipo, las cuatro confirmaciones del
 * protocolo; el chatbot y el elemento del sitio, no. Antes de enviar lista lo que falta, con el mismo cálculo del
 * servidor, y si el servidor aun así rechaza el cierre muestra su lista. Un duplicado exige buscar el ticket original, y
 * un duplicado o un registrado por error piden solo lo imprescindible (no son casos reales, FR-050).
 */
@Component({
  selector: 'sarc-close-dialog',
  imports: [
    FormField,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    LucideTriangleAlert,
  ],
  template: `
    <h2 mat-dialog-title>Cerrar ticket</h2>
    <form novalidate (submit)="onSubmit($event)">
      <mat-dialog-content class="fields">
        @if (errorMessage(); as message) {
          <div class="form-alert" role="alert">
            <svg lucideTriangleAlert [size]="20" aria-hidden="true"></svg>
            <div>
              <p>{{ message }}</p>
              @if (serverMissing().length > 0) {
                <ul>
                  @for (item of serverMissing(); track item) {
                    <li>{{ missingLabels[item] ?? item }}</li>
                  }
                </ul>
              }
            </div>
          </div>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Hora de cierre (T4)</mat-label>
          <input matInput type="datetime-local" [formField]="closeForm.t4" />
          <mat-hint>Vacía, es el momento actual. Hora de Ecuador.</mat-hint>
        </mat-form-field>
        @if (retroactive()) {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Motivo de la hora anterior</mat-label>
            <input matInput autocomplete="off" [formField]="closeForm.t4Reason" />
          </mat-form-field>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Resultado del cierre</mat-label>
          <mat-select [formField]="closeForm.result">
            @for (result of results; track result) {
              <mat-option [value]="result">{{ resultLabels[result] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (model().result === 'DUPLICATE') {
          <div class="original">
            <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Ticket original</mat-label>
              <input
                matInput
                type="search"
                autocomplete="off"
                placeholder="Código o texto del ticket original"
                [value]="originalText()"
                (input)="onSearch($event)"
              />
              <mat-hint>Busque el ticket que ya registra esta falla.</mat-hint>
            </mat-form-field>
            @if (original(); as chosen) {
              <p class="chosen">
                Original elegido: <strong>{{ chosen.code }}</strong> · {{ chosen.elementLabel }}
              </p>
            }
            @if (candidates().length > 0) {
              <ul class="candidates" aria-label="Tickets encontrados">
                @for (candidate of candidates(); track candidate.id) {
                  <li>
                    <button matButton type="button" (click)="choose(candidate)">
                      {{ candidate.code }} · {{ candidate.elementLabel }}
                    </button>
                  </li>
                }
              </ul>
            }
          </div>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Causa</mat-label>
          <mat-select [formField]="closeForm.causeKind">
            @for (kind of causeKinds; track kind) {
              <mat-option [value]="kind">{{ causeLabels[kind] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (model().causeKind !== '' && model().causeKind !== 'UNDETERMINED') {
          <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Descripción de la causa</mat-label>
            <textarea matInput rows="2" [formField]="closeForm.causeText"></textarea>
            @if (model().causeKind === 'PROBABLE') {
              <mat-hint
                >Una causa probable necesita una evidencia del episodio que la respalde.</mat-hint
              >
            }
          </mat-form-field>
        }

        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Recomendaciones o pendientes</mat-label>
          <textarea matInput rows="2" [formField]="closeForm.recommendations"></textarea>
          <mat-hint>Opcional.</mat-hint>
        </mat-form-field>

        @if (!excluded()) {
          @if (data.elementClass === 'EQUIPMENT') {
            <fieldset class="checks">
              <legend>Confirmaciones del protocolo</legend>
              @for (key of checkKeys; track key) {
                <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
                  <mat-label>{{ checkQuestions[key] }}</mat-label>
                  <mat-select [formField]="closeForm[key]">
                    @for (check of checkOptions; track check) {
                      <mat-option [value]="check">{{ checkLabels[check] }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              }
            </fieldset>
          }

          @if (hasFinalEvidence()) {
            <p class="ok">Evidencia final: cargada.</p>
          } @else {
            <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Sin evidencia final: motivo</mat-label>
              <input matInput autocomplete="off" [formField]="closeForm.finalEvidenceWaiver" />
              <mat-hint>
                O cierre este diálogo y cargue una evidencia con el uso «Final» en Evidencias.
              </mat-hint>
            </mat-form-field>
          }

          @if (hasClosureCommunication()) {
            <p class="ok">Comunicación de cierre: registrada.</p>
          } @else {
            <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Sin comunicación de cierre: motivo</mat-label>
              <input
                matInput
                autocomplete="off"
                [formField]="closeForm.closureCommunicationWaiver"
              />
              <mat-hint> O registre el envío de cierre desde «Mensaje para WhatsApp». </mat-hint>
            </mat-form-field>
          }

          @if (outside()) {
            <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Justificación de la desviación</mat-label>
              <textarea matInput rows="2" [formField]="closeForm.deviationJustification"></textarea>
              <mat-hint>Referencia operativa, no SLA contractual.</mat-hint>
            </mat-form-field>
          }
        }

        <section class="missing" role="status" aria-live="polite" aria-labelledby="missing-title">
          @if (missing().length === 0) {
            <p id="missing-title" class="ok">No falta nada: puede cerrar el ticket.</p>
          } @else {
            <p id="missing-title"><strong>Falta para cerrar</strong></p>
            <ul>
              @for (item of missing(); track item) {
                <li>{{ missingLabels[item] }}</li>
              }
            </ul>
          }
        </section>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button matButton type="button" [disabled]="busy()" (click)="unsaved.cancel()">
          Cancelar
        </button>
        <button matButton="filled" type="submit" [disabled]="busy() || missing().length > 0">
          {{ busy() ? 'Cerrando…' : 'Cerrar ticket' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../../shared/ui/dialog-form';

    .fields {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      min-width: min(480px, 80vw);
    }

    .checks {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      margin: 0;
      padding: 0;
      border: 0;

      legend {
        padding: 0 0 var(--sarc-space-2);
        font: var(--mat-sys-label-large);
      }
    }

    .original {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-2);
    }

    .candidates {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .chosen {
      margin: 0;
    }

    .ok {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }

    .missing {
      padding: var(--sarc-space-3);
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-small);

      p {
        margin: 0;
      }

      ul {
        margin: var(--sarc-space-1) 0 0;
        padding-inline-start: var(--sarc-space-4);
      }
    }

    .form-alert ul {
      margin: var(--sarc-space-1) 0 0;
      padding-inline-start: var(--sarc-space-4);
    }
  `,
})
export class CloseDialog {
  protected readonly data = inject<CloseDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<CloseDialog, boolean>>(MatDialogRef);
  protected readonly unsaved = guardUnsavedChanges(() => this.closeForm().dirty());

  protected readonly results = Object.keys(CLOSURE_RESULT_LABELS) as ClosureResult[];
  protected readonly resultLabels = CLOSURE_RESULT_LABELS;
  protected readonly causeKinds = Object.keys(CAUSE_KIND_LABELS) as CauseKind[];
  protected readonly causeLabels = CAUSE_KIND_LABELS;
  protected readonly checkKeys = CLOSURE_CHECK_KEYS;
  protected readonly checkQuestions = CLOSURE_CHECK_FIELD_LABELS;
  protected readonly checkOptions = Object.keys(CLOSURE_CHECK_LABELS) as ClosureCheck[];
  protected readonly checkLabels = CLOSURE_CHECK_LABELS;
  protected readonly missingLabels: Readonly<Record<string, string>> = CLOSURE_MISSING_LABELS;

  protected readonly model = signal<CloseFormValue>({
    t4: '',
    t4Reason: '',
    result: '',
    causeKind: '',
    causeText: '',
    recommendations: '',
    platformNormal: '',
    stabilityChecked: '',
    noAffectedUser: '',
    postTest: '',
    finalEvidenceWaiver: '',
    closureCommunicationWaiver: '',
    deviationJustification: '',
  });

  protected readonly original = signal<TicketSummaryResponse | null>(null);
  protected readonly originalText = signal('');
  protected readonly candidates = signal<readonly TicketSummaryResponse[]>([]);
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly serverMissing = signal<readonly string[]>([]);
  protected readonly busy = signal(false);

  /** La hora de cierre es anterior al momento de guardar: exige un motivo (FR-041). */
  protected readonly retroactive = computed(() => isRetroactiveLocal(this.model().t4));
  protected readonly excluded = computed(() => isExcludedResult(this.model().result));

  /** El episodio nació con su T0; lo cargado después pertenece a él, no a un episodio anterior (reabierto). */
  private readonly episodeStart =
    this.data.episode.milestones.find((m) => m.kind === 'T0')?.recordedAt ?? '';

  protected readonly hasFinalEvidence = computed(() =>
    this.data.evidence.some(
      (item) => item.purpose === 'FINAL' && !item.annulled && item.uploadedAt >= this.episodeStart,
    ),
  );
  private readonly hasEpisodeEvidence = computed(() =>
    this.data.evidence.some((item) => !item.annulled && item.uploadedAt >= this.episodeStart),
  );
  protected readonly hasClosureCommunication = computed(() =>
    this.data.communications.some(
      (item) => item.kind === 'CLOSURE' && !item.annulled && item.recordedAt >= this.episodeStart,
    ),
  );
  protected readonly outside = computed(() =>
    this.data.episode.references.some((reference) => reference.result === 'OUTSIDE'),
  );

  /** Lo que falta para cerrar, con el mismo cálculo que el servidor, más el motivo de una hora anterior. */
  protected readonly missing = computed<ClosureMissing[]>(() => {
    const value = this.model();
    const status = (kind: string): MilestoneStatus =>
      (this.data.episode.milestones.find((m) => m.kind === kind)?.status ??
        'PENDING') as MilestoneStatus;
    const base = closureMissing({
      elementClass: this.data.elementClass,
      hasT4: true,
      milestones: { T1: status('T1'), T2: status('T2'), T3: status('T3') },
      result: value.result,
      duplicateOfId: this.original()?.id ?? '',
      causeKind: value.causeKind,
      causeText: value.causeText,
      checks: {
        platformNormal: value.platformNormal,
        stabilityChecked: value.stabilityChecked,
        noAffectedUser: value.noAffectedUser,
        postTest: value.postTest,
      },
      hasFinalEvidence: this.hasFinalEvidence(),
      finalEvidenceWaiver: value.finalEvidenceWaiver,
      hasEpisodeEvidence: this.hasEpisodeEvidence(),
      hasClosureCommunication: this.hasClosureCommunication(),
      closureCommunicationWaiver: value.closureCommunicationWaiver,
      outsideReference: this.outside(),
      deviationJustification: value.deviationJustification,
    });
    return base;
  });

  protected readonly closeForm = form(this.model, (path) => {
    maxLength(path.t4Reason, 300, { message: 'Máximo 300 caracteres' });
    maxLength(path.causeText, 500, { message: 'Máximo 500 caracteres' });
    maxLength(path.recommendations, 1000, { message: 'Máximo 1000 caracteres' });
    maxLength(path.finalEvidenceWaiver, 300, { message: 'Máximo 300 caracteres' });
    maxLength(path.closureCommunicationWaiver, 300, { message: 'Máximo 300 caracteres' });
    maxLength(path.deviationJustification, 500, { message: 'Máximo 500 caracteres' });
  });

  protected onSearch(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.originalText.set(text);
    this.original.set(null);
    clearTimeout(this.searchTimer);
    if (text.trim().length < 2) {
      this.candidates.set([]);
      return;
    }
    this.searchTimer = setTimeout(() => {
      this.data
        .searchTickets(text)
        .then((found) => this.candidates.set(found))
        .catch(() => this.candidates.set([]));
    }, 250);
  }

  protected choose(ticket: TicketSummaryResponse): void {
    this.original.set(ticket);
    this.originalText.set(ticket.code);
    this.candidates.set([]);
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.missing().length > 0 || this.busy()) return;
    const value = this.model();
    const excluded = this.excluded();
    this.busy.set(true);
    this.errorMessage.set(undefined);
    this.serverMissing.set([]);
    try {
      await this.data.save({
        t4: operatingInstant(value.t4),
        t4Reason: this.retroactive() ? value.t4Reason.trim() || null : null,
        result: value.result === '' ? undefined : value.result,
        duplicateOfTicketId: value.result === 'DUPLICATE' ? (this.original()?.id ?? null) : null,
        causeKind: value.causeKind === '' ? undefined : value.causeKind,
        causeText: value.causeKind === 'UNDETERMINED' ? null : value.causeText.trim() || null,
        recommendations: value.recommendations.trim() || null,
        ...(this.data.elementClass === 'EQUIPMENT' && !excluded
          ? {
              closureChecks: Object.fromEntries(
                this.checkKeys.map((key: ClosureCheckKey) => [key, value[key] || undefined]),
              ),
            }
          : {}),
        finalEvidenceWaiver:
          excluded || this.hasFinalEvidence() ? null : value.finalEvidenceWaiver.trim() || null,
        closureCommunicationWaiver:
          excluded || this.hasClosureCommunication()
            ? null
            : value.closureCommunicationWaiver.trim() || null,
        deviationJustification:
          this.outside() && !excluded ? value.deviationJustification.trim() || null : null,
      });
      this.dialogRef.close(true);
    } catch (error) {
      const problem = toProblem(error);
      this.errorMessage.set(problem.detail);
      if (problem.code === CLOSE_REQUIREMENTS) {
        const list = problem.properties['missing'];
        if (Array.isArray(list)) this.serverMissing.set(list.map(String));
      }
    } finally {
      this.busy.set(false);
    }
  }
}
