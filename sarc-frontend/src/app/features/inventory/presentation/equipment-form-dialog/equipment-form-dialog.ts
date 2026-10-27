import { Component, computed, inject, signal } from '@angular/core';
import {
  disabled,
  form,
  FormField,
  hidden,
  maxLength,
  pattern,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { LucideTriangleAlert } from '@lucide/angular';

import { Problem, toProblem } from '../../../../core/http/problem';
import { ConnectorTypesFacade } from '../../application/connector-types-facade';
import {
  BRAND_LABELS,
  BRANDS,
  Equipment,
  NETWORK_LABELS,
  NETWORKS,
  TYPE_LABELS,
  TYPES,
  USAGE_STATUS_HINTS,
  USAGE_STATUS_LABELS,
  USAGE_STATUSES,
} from '../../domain/equipment';
import {
  isPoint,
  KIA_POINT_CODE,
  maxConnectors,
  usesSerialAsCode,
} from '../../domain/equipment-rules';
import {
  EquipmentDraft,
  EquipmentFormValue,
  initialFormValue,
  toEquipmentDraft,
} from './equipment-form';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';

export interface EquipmentFormDialogData {
  /** Equipo a editar: red y tipo no cambian. */
  readonly equipment?: Equipment;
  /** Equipo que se reemplaza fisicamente: el nuevo hereda red, tipo, nombre y ubicacion. */
  readonly replaces?: Equipment;
  /** Persiste el formulario. Si falla, el dialogo sigue abierto y muestra el error. */
  readonly save: (draft: EquipmentDraft) => Promise<Equipment>;
}

const MAX_TEXT = 80;

@Component({
  selector: 'sarc-equipment-form-dialog',
  imports: [
    FormField,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    LucideTriangleAlert,
  ],
  templateUrl: './equipment-form-dialog.html',
  styleUrl: './equipment-form-dialog.scss',
})
export class EquipmentFormDialog {
  private readonly data = inject<EquipmentFormDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<EquipmentFormDialog, Equipment>>(MatDialogRef);
  /** Esc, clic fuera o Cancelar no descartan cambios sin confirmarlo. */
  protected readonly unsaved = guardUnsavedChanges(() => this.equipmentForm().dirty());
  private readonly router = inject(Router);
  private readonly connectorTypes = inject(ConnectorTypesFacade);

  protected readonly editing = this.data.equipment !== undefined;
  protected readonly replaces = this.data.replaces;
  /** Red y tipo quedan fijos al editar o al registrar un reemplazo. */
  private readonly classificationFixed = this.editing || this.replaces !== undefined;

  protected readonly networks = NETWORKS;
  protected readonly networkLabels = NETWORK_LABELS;
  protected readonly types = TYPES;
  protected readonly typeLabels = TYPE_LABELS;
  protected readonly brands = BRANDS;
  protected readonly brandLabels = BRAND_LABELS;
  protected readonly usageStatuses = USAGE_STATUSES;
  protected readonly usageLabels = USAGE_STATUS_LABELS;
  protected readonly usageHints = USAGE_STATUS_HINTS;

  protected readonly model = signal<EquipmentFormValue>(
    initialFormValue(this.data.equipment, this.data.replaces),
  );

  protected readonly point = computed(() => {
    const type = this.model().type;
    return type !== '' && isPoint(type);
  });
  protected readonly station = computed(() => this.model().type === 'CHARGING_STATION');
  protected readonly ownCode = computed(() => {
    const { network, type } = this.model();
    return network !== '' && type !== '' && !usesSerialAsCode(network, type);
  });

  /** Tipos activos mas los que ya tiene el equipo, aunque luego se hayan inactivado. */
  protected readonly connectorOptions = computed(() => {
    const current = this.data.equipment?.connectors ?? [];
    const active = this.connectorTypes.active();
    const retained = current
      .filter((connector) => !active.some((type) => type.id === connector.connectorTypeId))
      .map((connector) => ({
        id: connector.connectorTypeId,
        code: connector.connectorTypeCode,
        name: `${connector.connectorTypeName} (inactivo)`,
      }));
    return [...active, ...retained];
  });

  protected readonly equipmentForm = form(this.model, (path) => {
    required(path.network, { message: 'Seleccione la red' });
    required(path.type, { message: 'Seleccione el tipo' });
    disabled(path.network, { when: () => this.classificationFixed });
    disabled(path.type, { when: () => this.classificationFixed });
    required(path.brand, { message: 'Seleccione la marca' });
    required(path.name, { message: 'Ingrese el nombre' });
    maxLength(path.name, 150, { message: 'Máximo 150 caracteres' });
    required(path.serial, { message: 'Ingrese la serie' });
    maxLength(path.serial, MAX_TEXT, { message: `Máximo ${MAX_TEXT} caracteres` });

    hidden(path.assignedCode, { when: () => !this.ownCode() });
    required(path.assignedCode, {
      message: 'Ingrese el código asignado',
      when: () => this.ownCode(),
    });
    pattern(path.assignedCode, KIA_POINT_CODE, {
      message: '6 dígitos y no puede empezar en 0',
      when: () => this.ownCode(),
    });

    required(path.uidPuk, {
      message: 'El UID/PUK es obligatorio en los puntos de carga',
      when: () => this.point(),
    });
    maxLength(path.uidPuk, MAX_TEXT, { message: `Máximo ${MAX_TEXT} caracteres` });

    hidden(path.usageStatus, { when: () => !this.point() });
    required(path.usageStatus, {
      message: 'Seleccione el estado de uso',
      when: () => this.point(),
    });

    maxLength(path.location, 300, { message: 'Máximo 300 caracteres' });
    maxLength(path.mapsUrl, 500, { message: 'Máximo 500 caracteres' });
    pattern(path.mapsUrl, /^$|^https:\/\/\S+$/i, { message: 'Debe empezar con https://' });
    maxLength(path.ocppUrl, 500, { message: 'Máximo 500 caracteres' });
    pattern(path.ocppUrl, /^$|^(wss?|https?):\/\/\S+$/i, {
      message: 'Debe empezar con ws://, wss://, http:// o https://',
    });

    hidden(path.pointConnector, { when: () => !this.point() });
    required(path.pointConnector, {
      message: 'Seleccione el tipo de conector',
      when: () => this.point(),
    });
    hidden(path.stationConnectors, { when: () => !this.station() });
    validate(path.stationConnectors, ({ value }) => {
      const count = value().length;
      if (!this.station() || (count >= 1 && count <= maxConnectors('CHARGING_STATION'))) {
        return undefined;
      }
      return { kind: 'connectorCount', message: 'Seleccione entre 1 y 3 tipos de conector' };
    });
  });

  protected readonly problem = signal<Problem | undefined>(undefined);
  /** Equipo que ya usa los identificadores (conflicto 409), para ir a verlo o recuperarlo. */
  protected readonly conflictingId = computed(() => {
    const id = this.problem()?.properties['equipmentId'];
    return typeof id === 'string' ? id : undefined;
  });
  protected readonly busy = signal(false);

  protected title(): string {
    if (this.editing) {
      return 'Editar equipo';
    }
    return this.replaces ? 'Registrar reemplazo' : 'Nuevo equipo';
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.equipmentForm, async () => {
      this.busy.set(true);
      this.problem.set(undefined);
      try {
        this.dialogRef.close(await this.data.save(toEquipmentDraft(this.model())));
      } catch (error) {
        this.problem.set(toProblem(error));
      } finally {
        this.busy.set(false);
      }
    });
  }

  protected async openConflicting(id: string): Promise<void> {
    this.dialogRef.close();
    await this.router.navigate(['/inventario', id]);
  }
}
