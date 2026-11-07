import { Component, inject, signal } from '@angular/core';
import { form, FormField, required, submit, validate } from '@angular/forms/signals';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert, LucideX } from '@lucide/angular';

import { toProblemFromBlob } from '../../../../core/http/problem';
import { ReportFormat } from '../../../../shared/reports/report-format';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';
import {
  NETWORK_LABELS,
  NETWORKS,
  TYPE_LABELS,
  TYPES,
} from '../../../../shared/vocabulary/charging-infrastructure';
import { IssuedReportsFacade } from '../../application/issued-reports-facade';
import {
  GeneratedReportInfo,
  IssuedReport,
  REPORT_MAX_DAYS,
  REPORT_SOURCE_LABELS,
  REPORT_SOURCES,
  REPORT_TIME_ZONE,
  reportDayCount,
  ReportEquipmentOption,
  shiftReportDay,
} from '../../domain/issued-report';

export interface ManagerialReportDialogData {
  readonly report: IssuedReport;
}

/**
 * Nueva emision con el contenido que autorizan los roles actuales. Solo precarga los filtros que el
 * original guardo de forma estructurada: el alcance legible de un legado nunca se interpreta.
 */
@Component({
  selector: 'sarc-managerial-report-dialog',
  imports: [
    FormField,
    MatAutocompleteModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    LucideTriangleAlert,
    LucideX,
  ],
  templateUrl: './managerial-report-dialog.html',
  styleUrl: './managerial-report-dialog.scss',
})
export class ManagerialReportDialog {
  protected readonly data = inject<ManagerialReportDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<ManagerialReportDialog, GeneratedReportInfo>>(MatDialogRef);
  private readonly facade = inject(IssuedReportsFacade);
  /** Esc, clic fuera o Cancelar no descartan cambios sin confirmarlo. */
  protected readonly unsaved = guardUnsavedChanges(() => this.reportForm().dirty());

  protected readonly networks = NETWORKS;
  protected readonly networkLabels = NETWORK_LABELS;
  protected readonly types = TYPES;
  protected readonly typeLabels = TYPE_LABELS;
  protected readonly sources = REPORT_SOURCES;
  protected readonly sourceLabels = REPORT_SOURCE_LABELS;

  private readonly criteria = this.data.report.criteria ?? null;
  protected readonly legacy = this.criteria === null;
  readonly model = signal({
    from: this.criteria?.['from'] ?? '',
    to: this.criteria?.['to'] ? shiftReportDay(this.criteria['to'], -1) : '',
    network: this.criteria?.['network'] ?? '',
    type: this.criteria?.['type'] ?? '',
    source: this.criteria?.['source'] ?? '',
    equipmentId: this.criteria?.['equipmentId'] ?? '',
    format: this.data.report.format as ReportFormat,
  });
  readonly reportForm = form(this.model, (path) => {
    required(path.from, { message: 'Seleccione la fecha inicial' });
    required(path.to, { message: 'Seleccione la fecha final' });
    validate(path.to, ({ value, valueOf }) => {
      const days = reportDayCount(valueOf(path.from), value());
      if (days === null) {
        return undefined;
      }
      if (days < 1) {
        return { kind: 'range', message: 'La fecha final no puede ser anterior a la inicial' };
      }
      return days > REPORT_MAX_DAYS
        ? { kind: 'range', message: `El período admite hasta ${REPORT_MAX_DAYS} días` }
        : undefined;
    });
  });

  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly equipmentOptions = signal<readonly ReportEquipmentOption[]>([]);
  protected readonly equipmentLabel = signal('');

  constructor() {
    const equipmentId = this.model().equipmentId;
    if (equipmentId !== '') {
      void this.loadEquipment(equipmentId);
    }
  }

  protected equipmentName(option: ReportEquipmentOption): string {
    return option.assignedCode ? `${option.name} · ${option.assignedCode}` : option.name;
  }

  protected async onEquipmentText(event: Event): Promise<void> {
    const text = (event.target as HTMLInputElement).value;
    this.equipmentLabel.set(text);
    this.model.update((value) => ({ ...value, equipmentId: '' }));
    try {
      this.equipmentOptions.set(await this.facade.searchEquipment(text));
    } catch {
      this.equipmentOptions.set([]);
    }
  }

  protected onEquipment(event: MatAutocompleteSelectedEvent): void {
    const equipment = event.option.value as ReportEquipmentOption;
    this.equipmentLabel.set(this.equipmentName(equipment));
    this.model.update((value) => ({ ...value, equipmentId: equipment.id }));
  }

  protected clearEquipment(): void {
    this.equipmentLabel.set('');
    this.model.update((value) => ({ ...value, equipmentId: '' }));
  }

  onSubmit(event: Event): void {
    event.preventDefault();
    // Texto sin elegir de la lista: emitir ignoraria el filtro que el campo aparenta aplicar.
    if (this.equipmentLabel().trim() !== '' && this.model().equipmentId === '') {
      this.error.set('Elija un equipo de la lista o borre el texto del filtro');
      return;
    }
    void submit(this.reportForm, async () => {
      this.busy.set(true);
      this.error.set('');
      const { from, to, network, type, source, equipmentId, format } = this.model();
      try {
        this.dialogRef.close(
          await this.facade.generateConsumptionReport(
            {
              from,
              to: shiftReportDay(to, 1),
              timeZone: REPORT_TIME_ZONE,
              network,
              type,
              source,
              equipmentId,
            },
            format,
          ),
        );
      } catch (error) {
        this.error.set((await toProblemFromBlob(error)).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }

  private async loadEquipment(id: string): Promise<void> {
    try {
      this.equipmentLabel.set(this.equipmentName(await this.facade.findEquipment(id)));
    } catch {
      // Equipo ya no consultable: el filtro exacto sigue vigente, pero el identificador interno
      // no se muestra a la persona.
      this.equipmentLabel.set('Equipo no disponible');
    }
  }
}
