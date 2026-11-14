import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, required, submit, validate } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { toProblem } from '../../../../core/http/problem';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { EnergyReview, EnergyDecision, Session } from '../../domain/consumption';
import { sourceUtcLabel, sourceUtcOffset } from '../../domain/source-time';

const MAX_JUSTIFICATION = 500;

export interface EnergyReviewDialogData {
  readonly session: Session;
  readonly canEdit: boolean;
  readonly history: () => Promise<EnergyReview[]>;
  readonly save: (decision: EnergyDecision, justification: string) => Promise<Session>;
  readonly stale: () => void;
}

/** Revision sobre la version que abrio el dialogo; un conflicto nunca se reintenta a ciegas. */
@Component({
  selector: 'sarc-energy-review-dialog',
  imports: [
    DatePipe,
    FormField,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    LucideTriangleAlert,
  ],
  templateUrl: './energy-review-dialog.html',
  styles: `
    @use '../../../../shared/ui/dialog-form';

    .fields {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-4);
      min-width: min(480px, 75vw);
    }

    .summary {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--sarc-space-3) var(--sarc-space-4);
      margin: 0;
      padding: var(--sarc-space-4);
      border-radius: var(--sarc-radius-md);
      background: var(--mat-sys-surface-container-low);
      overflow-wrap: anywhere;

      dt {
        color: var(--mat-sys-on-surface-variant);
        font: var(--mat-sys-title-small);
      }

      dd {
        margin: 0;
        font: var(--mat-sys-body-large);
      }

      .energy {
        font-weight: 700;
      }
    }

    .muted {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }

    .mono {
      font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
    }

    h3 {
      margin: 0;
      font: var(--mat-sys-title-small);
    }

    p {
      margin: 0;
    }

    .history {
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        display: flex;
        flex-direction: column;
        gap: var(--sarc-space-1);
        padding-block: var(--sarc-space-3);
        border-top: 1px solid var(--mat-sys-outline-variant);
        overflow-wrap: anywhere;
      }
    }
  `,
})
export class EnergyReviewDialog {
  readonly data = inject<EnergyReviewDialogData>(MAT_DIALOG_DATA);
  protected readonly sourceOffset = sourceUtcOffset;
  protected readonly sourceLabel = sourceUtcLabel;
  private readonly dialog = inject<MatDialogRef<EnergyReviewDialog, Session>>(MatDialogRef);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly maxLength = MAX_JUSTIFICATION;
  /** Esc, clic fuera o Cancelar no descartan una justificacion escrita sin confirmarlo. */
  protected readonly unsaved = guardUnsavedChanges(() => this.reviewForm().dirty());
  /** Sin decision preseleccionada: aprobar o rechazar es una eleccion consciente. */
  readonly model = signal({ decision: '' as EnergyDecision | '', justification: '' });
  readonly reviewForm = form(this.model, (path) => {
    required(path.decision, { message: 'Seleccione una decisión' });
    validate(path.justification, ({ value }) => {
      const text = value().trim();
      return text.length === 0
        ? { kind: 'required', message: 'Ingrese una justificación' }
        : text.length > MAX_JUSTIFICATION
          ? {
              kind: 'maxLength',
              message: `Máximo ${MAX_JUSTIFICATION} caracteres sin espacios exteriores`,
            }
          : undefined;
    });
  });
  protected readonly remaining = computed(
    () => MAX_JUSTIFICATION - this.model().justification.trim().length,
  );
  readonly history = signal<EnergyReview[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly stale = signal(false);
  readonly error = signal('');

  constructor() {
    void this.loadHistory();
  }

  async loadHistory(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.history.set(await this.data.history());
    } catch (error) {
      this.error.set(toProblem(error).detail);
    } finally {
      this.loading.set(false);
    }
  }

  onSubmit(event: Event): void {
    event.preventDefault();
    if (!this.data.canEdit || this.stale()) return;
    void submit(this.reviewForm, async () => {
      this.busy.set(true);
      this.error.set('');
      try {
        const value = this.model();
        this.dialog.close(
          await this.data.save(value.decision as EnergyDecision, value.justification.trim()),
        );
      } catch (error) {
        const problem = toProblem(error);
        this.error.set(problem.detail);
        if (problem.code === 'CONCURRENT_MODIFICATION') {
          this.stale.set(true);
          this.data.stale();
        }
      } finally {
        this.busy.set(false);
      }
    });
  }
}
