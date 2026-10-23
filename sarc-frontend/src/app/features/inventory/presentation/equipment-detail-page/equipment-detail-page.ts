import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import {
  LucideArrowLeft,
  LucideCalendarClock,
  LucideChevronDown,
  LucideExternalLink,
  LucideEyeOff,
  LucidePencil,
  LucideReplace,
} from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { SessionStore } from '../../../../core/auth/session-store';
import { toProblem } from '../../../../core/http/problem';
import { Notifier } from '../../../../core/notifications/notifier';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../../shared/ui/confirm-dialog/confirm-dialog';
import { WIDE_DIALOG } from '../../../../shared/ui/dialog-sizes';
import { EquipmentDetailFacade } from '../../application/equipment-detail-facade';
import {
  AdministrativeStatus,
  BRAND_LABELS,
  Equipment,
  NETWORK_LABELS,
  TYPE_LABELS,
} from '../../domain/equipment';
import { INVENTORY_EDITORS } from '../../domain/inventory-permissions';
import { ExternalIdentity } from '../../domain/external-identity';
import { EquipmentBadges } from '../equipment-badges/equipment-badges';
import {
  EquipmentFormDialog,
  EquipmentFormDialogData,
} from '../equipment-form-dialog/equipment-form-dialog';
import { toUpdateInput } from '../equipment-form-dialog/equipment-form';
import { EquipmentImage } from '../equipment-image/equipment-image';
import { EquipmentSchedule } from '../equipment-schedule/equipment-schedule';
import { ExternalIdentities } from '../external-identities/external-identities';
import {
  ExternalIdentityDialog,
  ExternalIdentityDialogData,
} from '../external-identity-dialog/external-identity-dialog';
import { ScheduleDialog, ScheduleDialogData } from '../schedule-dialog/schedule-dialog';

interface StatusAction {
  readonly target: AdministrativeStatus;
  readonly label: string;
  readonly confirm: string;
  readonly done: string;
}

const ACTIVATE: StatusAction = {
  target: 'ACTIVE',
  label: 'Activar',
  confirm: 'El equipo volverá a estar en servicio.',
  done: 'Equipo activado.',
};
const INACTIVATE: StatusAction = {
  target: 'INACTIVE',
  label: 'Inactivar',
  confirm: 'El equipo queda fuera de servicio. Su histórico se conserva.',
  done: 'Equipo inactivado.',
};
const DELETE: StatusAction = {
  target: 'DELETED',
  label: 'Eliminar',
  confirm:
    'Es una baja lógica: el equipo deja de aparecer en el inventario vigente, pero conserva su identificador y su histórico, y se puede recuperar.',
  done: 'Equipo eliminado.',
};
const RESTORE: StatusAction = {
  target: 'ACTIVE',
  label: 'Recuperar',
  confirm: 'El equipo se recupera con el mismo identificador y todos sus vínculos históricos.',
  done: 'Equipo recuperado.',
};

/** Transiciones de estado disponibles desde cada estado administrativo. */
const STATUS_ACTIONS: Readonly<Record<AdministrativeStatus, readonly StatusAction[]>> = {
  ACTIVE: [INACTIVATE, DELETE],
  INACTIVE: [ACTIVATE, DELETE],
  DELETED: [RESTORE],
};

/** Ficha del equipo: datos, imagen, conectores, horario e identidades externas. */
@Component({
  selector: 'sarc-equipment-detail-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatMenuModule,
    MatProgressBarModule,
    LucideArrowLeft,
    LucidePencil,
    LucideCalendarClock,
    LucideReplace,
    LucideChevronDown,
    LucideExternalLink,
    LucideEyeOff,
    EquipmentBadges,
    EquipmentImage,
    EquipmentSchedule,
    ExternalIdentities,
  ],
  providers: [EquipmentDetailFacade],
  templateUrl: './equipment-detail-page.html',
  styleUrl: './equipment-detail-page.scss',
})
export class EquipmentDetailPage {
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);

  protected readonly facade = inject(EquipmentDetailFacade);
  protected readonly canEdit = inject(SessionStore).hasAnyRole(INVENTORY_EDITORS);
  protected readonly imageBusy = signal(false);
  protected readonly networkLabels = NETWORK_LABELS;
  protected readonly typeLabels = TYPE_LABELS;
  protected readonly brandLabels = BRAND_LABELS;

  protected readonly statusActions = computed(() => {
    const equipment = this.facade.equipment();
    return equipment === undefined ? [] : STATUS_ACTIONS[equipment.adminStatus];
  });
  protected readonly restore = RESTORE;

  protected async edit(equipment: Equipment): Promise<void> {
    const data: EquipmentFormDialogData = {
      equipment,
      save: (draft) => this.facade.update(toUpdateInput(draft)),
    };
    if (await this.openForm(data)) {
      this.notifier.success('Equipo actualizado.');
    }
  }

  protected async registerReplacement(equipment: Equipment): Promise<void> {
    const data: EquipmentFormDialogData = {
      replaces: equipment,
      save: (draft) => this.facade.registerReplacement(draft),
    };
    const created = await this.openForm(data);
    if (created !== undefined) {
      this.notifier.success('Reemplazo registrado.');
      await this.router.navigate(['/inventario', created.id]);
    }
  }

  protected async editSchedule(equipment: Equipment): Promise<void> {
    const data: ScheduleDialogData = {
      schedule: equipment.schedule,
      save: (input) => this.facade.defineSchedule(input),
    };
    const saved = await firstValueFrom(
      this.dialog
        .open<ScheduleDialog, ScheduleDialogData, Equipment>(ScheduleDialog, {
          ...WIDE_DIALOG,
          data,
        })
        .afterClosed(),
    );
    if (saved !== undefined) {
      this.notifier.success('Horario guardado.');
    }
  }

  protected async changeStatus(equipment: Equipment, action: StatusAction): Promise<void> {
    const confirmed = await this.confirm({
      title: `${action.label} ${equipment.name}`,
      message: action.confirm,
      confirmLabel: action.label,
    });
    if (confirmed) {
      await this.run(() => this.facade.changeStatus(action.target), action.done);
    }
  }

  protected async uploadImage(file: File): Promise<void> {
    this.imageBusy.set(true);
    try {
      await this.run(() => this.facade.attachImage(file), 'Imagen actualizada.');
    } finally {
      this.imageBusy.set(false);
    }
  }

  protected async removeImage(): Promise<void> {
    const confirmed = await this.confirm({
      title: 'Quitar imagen',
      message: 'La imagen se borrará del almacenamiento.',
      confirmLabel: 'Quitar',
    });
    if (confirmed) {
      await this.run(() => this.facade.removeImage(), 'Imagen quitada.');
    }
  }

  protected async addIdentity(): Promise<void> {
    const data: ExternalIdentityDialogData = {
      save: (input) => this.facade.registerIdentity(input),
    };
    const created = await firstValueFrom(
      this.dialog
        .open<ExternalIdentityDialog, ExternalIdentityDialogData, ExternalIdentity>(
          ExternalIdentityDialog,
          { data, autoFocus: 'first-tabbable' },
        )
        .afterClosed(),
    );
    if (created !== undefined) {
      this.notifier.success('Identidad externa agregada.');
    }
  }

  protected async verifyIdentity(identity: ExternalIdentity): Promise<void> {
    const confirmed = await this.confirm({
      title: 'Verificar identidad',
      message: `Confirme que ${identity.externalId} corresponde a este equipo. Una vez verificada se usará para conciliar importaciones y no se podrá quitar.`,
      confirmLabel: 'Verificar',
    });
    if (confirmed) {
      await this.run(() => this.facade.verifyIdentity(identity), 'Identidad verificada.');
    }
  }

  protected async removeIdentity(identity: ExternalIdentity): Promise<void> {
    const confirmed = await this.confirm({
      title: 'Quitar identidad',
      message: `Se quitará la identidad ${identity.externalId}.`,
      confirmLabel: 'Quitar',
    });
    if (confirmed) {
      await this.run(() => this.facade.removeIdentity(identity), 'Identidad quitada.');
    }
  }

  private openForm(data: EquipmentFormDialogData): Promise<Equipment | undefined> {
    return firstValueFrom(
      this.dialog
        .open<EquipmentFormDialog, EquipmentFormDialogData, Equipment>(EquipmentFormDialog, {
          ...WIDE_DIALOG,
          data,
        })
        .afterClosed(),
    );
  }

  private async confirm(data: ConfirmDialogData): Promise<boolean> {
    const result = await firstValueFrom(this.dialog.open(ConfirmDialog, { data }).afterClosed());
    return result === true;
  }

  /** Ejecuta una operacion y avisa el resultado; un conflicto de version recarga la ficha. */
  private async run(operation: () => Promise<unknown>, success: string): Promise<void> {
    try {
      await operation();
      this.notifier.success(success);
    } catch (error) {
      const problem = toProblem(error);
      this.notifier.error(problem.detail);
      if (problem.code === 'CONCURRENT_MODIFICATION') {
        this.facade.reload();
      }
    }
  }
}
