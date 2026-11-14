import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { form, FormField, submit, validate } from '@angular/forms/signals';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LucideTriangleAlert, LucideX } from '@lucide/angular';

import { EquipmentSummaryResponse } from '../../../../core/api/generated';
import { toProblem } from '../../../../core/http/problem';
import { OPERATING_UTC_OFFSET } from '../../../../core/time/local-day';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import { EquipmentOptions } from '../../application/equipment-options';
import {
  REVISION_KIND_LABELS,
  Session,
  SESSION_STATE_LABELS,
  SessionChanges,
  SessionDetail,
  SOURCE_LABELS,
} from '../../domain/consumption';
import {
  SOURCE_DATE_TIME,
  sourceEndUtcLabel,
  sourceEndUtcOffset,
  sourceLocalInput,
  sourceUtcLabel,
  sourceUtcOffset,
  withSourceOffset,
} from '../../domain/source-time';

const MAX_REASON = 500;

export type SessionAction = 'view' | 'edit' | 'delete';

export interface SessionActionDialogData {
  readonly action: SessionAction;
  readonly session: Session;
  readonly detail: () => Promise<SessionDetail>;
  readonly edit: (changes: SessionChanges, reason: string) => Promise<Session>;
  readonly delete: (reason: string) => Promise<Session>;
  readonly stale: () => void;
}

interface EditModel {
  start: string;
  end: string;
  energy: string;
  equipment: string;
  reason: string;
}

/**
 * Ver, editar o dar de baja una sesion (Feature 009, US3). La edicion y la baja exigen motivo y se
 * guardan sobre la version que abrio el dialogo: un conflicto conserva lo escrito y ofrece recargar.
 * Las horas se escriben en la hora de la fuente, con el desplazamiento de cada extremo.
 */
@Component({
  selector: 'sarc-session-action-dialog',
  imports: [
    DatePipe,
    FormField,
    MatAutocompleteModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    LucideTriangleAlert,
    LucideX,
  ],
  providers: [EquipmentOptions],
  templateUrl: './session-action-dialog.html',
  styleUrl: './session-action-dialog.scss',
})
export class SessionActionDialog {
  readonly data = inject<SessionActionDialogData>(MAT_DIALOG_DATA);
  private readonly dialog = inject<MatDialogRef<SessionActionDialog, Session>>(MatDialogRef);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly equipmentOptions = inject(EquipmentOptions);
  protected readonly maxLength = MAX_REASON;
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly stateLabels = SESSION_STATE_LABELS;
  protected readonly kindLabels = REVISION_KIND_LABELS;
  protected readonly sourceDateTime = SOURCE_DATE_TIME;
  protected readonly operatingOffset = OPERATING_UTC_OFFSET;
  protected readonly sourceOffset = sourceUtcOffset;
  protected readonly sourceLabel = sourceUtcLabel;
  protected readonly endOffset = sourceEndUtcOffset;
  protected readonly endLabel = sourceEndUtcLabel;

  /** Zona en que se escribe cada extremo: la que declaró la fuente, o la de Ecuador si no declaró. */
  protected readonly startZone =
    sourceUtcLabel(this.data.session.sourceUtcOffsetMinutes) ?? 'hora de Ecuador';
  protected readonly endZone =
    sourceEndUtcLabel(
      this.data.session.sourceUtcOffsetMinutes,
      this.data.session.sourceEndUtcOffsetMinutes,
    ) ?? this.startZone;
  protected readonly title = {
    view: 'Detalle de la sesión',
    edit: 'Editar sesión',
    delete: 'Eliminar sesión',
  }[this.data.action];

  private readonly initial: EditModel = initialModel(this.data.session);
  readonly model = signal<EditModel>({ ...this.initial });
  /** Equipo elegido en el autocompletado; `undefined` conserva el vigente. */
  readonly equipmentId = signal<string | null | undefined>(undefined);
  readonly actionForm = form(this.model, (path) => {
    validate(path.reason, ({ value }) => {
      const text = value().trim();
      if (text.length === 0) {
        return { kind: 'required', message: 'Ingrese el motivo' };
      }
      return text.length > MAX_REASON
        ? { kind: 'maxLength', message: `Máximo ${MAX_REASON} caracteres sin espacios exteriores` }
        : undefined;
    });
    validate(path.start, ({ value }) =>
      this.data.action === 'edit' && value() === ''
        ? { kind: 'required', message: 'Ingrese el inicio' }
        : undefined,
    );
    validate(path.energy, ({ value }) => {
      const text = value().trim();
      return text !== '' && !(Number(text.replace(',', '.')) >= 0)
        ? { kind: 'min', message: 'Ingrese una energía de 0 kWh o más, o déjela vacía' }
        : undefined;
    });
  });
  protected readonly remaining = computed(() => MAX_REASON - this.model().reason.trim().length);
  /** Esc, clic fuera o Cancelar no descartan un cambio escrito sin confirmarlo. */
  protected readonly unsaved = guardUnsavedChanges(
    () => this.data.action !== 'view' && this.actionForm().dirty(),
  );

  readonly detail = signal<SessionDetail | undefined>(undefined);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly stale = signal(false);
  readonly error = signal('');

  constructor() {
    void this.loadDetail();
  }

  async loadDetail(): Promise<void> {
    this.loading.set(true);
    try {
      this.detail.set(await this.data.detail());
    } catch (error) {
      this.error.set(toProblem(error).detail);
    } finally {
      this.loading.set(false);
    }
  }

  protected onEquipmentType(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.model.update((model) => ({ ...model, equipment: text }));
    this.equipmentOptions.query.set(text);
  }

  protected onEquipmentSelect(event: MatAutocompleteSelectedEvent): void {
    const equipment = event.option.value as EquipmentSummaryResponse;
    this.equipmentId.set(equipment.id);
    this.model.update((model) => ({ ...model, equipment: equipmentLabel(equipment) }));
  }

  protected unlinkEquipment(): void {
    this.equipmentId.set(null);
    this.model.update((model) => ({ ...model, equipment: '' }));
  }

  /** Solo viajan los campos que cambiaron: el servidor conserva los demas. */
  changes(): SessionChanges {
    const session = this.data.session;
    const value = this.model();
    const changes: { -readonly [K in keyof SessionChanges]: SessionChanges[K] } = {};
    if (value.start !== this.initial.start) {
      changes.startedAt = withSourceOffset(value.start, session.sourceUtcOffsetMinutes);
    }
    if (value.end !== this.initial.end) {
      const endMinutes = session.sourceEndUtcOffsetMinutes ?? session.sourceUtcOffsetMinutes;
      changes.endedAt = value.end === '' ? null : withSourceOffset(value.end, endMinutes);
    }
    if (value.energy.trim() !== this.initial.energy) {
      const text = value.energy.trim().replace(',', '.');
      changes.energyKwh = text === '' ? null : Number(text);
    }
    const equipment = this.equipmentId();
    if (equipment !== undefined && equipment !== (session.equipment?.id ?? null)) {
      changes.equipmentId = equipment;
    }
    return changes;
  }

  onSubmit(event: Event): void {
    event.preventDefault();
    if (this.data.action === 'view' || this.stale()) return;
    void submit(this.actionForm, async () => {
      const reason = this.model().reason.trim();
      if (this.data.action === 'edit' && Object.keys(this.changes()).length === 0) {
        this.error.set('No cambió ningún dato de la sesión.');
        return;
      }
      this.busy.set(true);
      this.error.set('');
      try {
        this.dialog.close(
          this.data.action === 'edit'
            ? await this.data.edit(this.changes(), reason)
            : await this.data.delete(reason),
        );
      } catch (error) {
        const problem = toProblem(error);
        this.error.set(problem.detail);
        if (problem.code === 'CONCURRENT_MODIFICATION') {
          this.stale.set(true);
        }
      } finally {
        this.busy.set(false);
      }
    });
  }

  /** Recarga el historico; el dialogo se cierra para reabrirse sobre la version vigente. */
  protected reload(): void {
    this.data.stale();
    this.dialog.close();
  }
}

function initialModel(session: Session): EditModel {
  return {
    start: sourceLocalInput(session.startedAt, session.sourceUtcOffsetMinutes),
    end: session.endedAt
      ? sourceLocalInput(
          session.endedAt,
          session.sourceEndUtcOffsetMinutes ?? session.sourceUtcOffsetMinutes,
        )
      : '',
    energy: session.energyKwh == null ? '' : String(session.energyKwh),
    equipment: session.equipment ? equipmentLabel(session.equipment) : '',
    reason: '',
  };
}

function equipmentLabel(equipment: { name: string; assignedCode?: string | null }): string {
  return equipment.assignedCode ? `${equipment.name} · ${equipment.assignedCode}` : equipment.name;
}
