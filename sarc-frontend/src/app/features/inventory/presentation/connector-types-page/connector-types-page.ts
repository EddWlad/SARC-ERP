import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { LucideArrowLeft, LucidePencil, LucidePlus } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { toProblem } from '../../../../core/http/problem';
import { Notifier } from '../../../../core/notifications/notifier';
import { ConnectorTypesFacade } from '../../application/connector-types-facade';
import { ConnectorType } from '../../domain/connector-type';
import {
  ConnectorTypeDialog,
  ConnectorTypeDialogData,
} from '../connector-type-dialog/connector-type-dialog';

/**
 * Catalogo de tipos de conector (RF-002). Un tipo usado en historicos no se elimina: se inactiva y
 * deja de ofrecerse para equipos nuevos, pero los conectores que ya lo usan lo conservan.
 */
@Component({
  selector: 'sarc-connector-types-page',
  imports: [
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatProgressBarModule,
    LucideArrowLeft,
    LucidePlus,
    LucidePencil,
  ],
  templateUrl: './connector-types-page.html',
  styleUrl: './connector-types-page.scss',
})
export class ConnectorTypesPage {
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly facade = inject(ConnectorTypesFacade);
  protected readonly columns = ['code', 'name', 'status', 'actions'];

  protected async create(): Promise<void> {
    const data: ConnectorTypeDialogData = { save: (value) => this.facade.create(value) };
    if (await this.openDialog(data)) {
      this.notifier.success('Tipo de conector creado.');
    }
  }

  protected async rename(type: ConnectorType): Promise<void> {
    const data: ConnectorTypeDialogData = {
      connectorType: type,
      save: (value) => this.facade.rename(type, value.name),
    };
    if (await this.openDialog(data)) {
      this.notifier.success('Tipo de conector actualizado.');
    }
  }

  protected async toggle(type: ConnectorType): Promise<void> {
    const activating = type.status !== 'ACTIVE';
    try {
      await this.facade.changeStatus(type, activating ? 'ACTIVE' : 'INACTIVE');
      this.notifier.success(activating ? 'Tipo activado.' : 'Tipo inactivado.');
    } catch (error) {
      this.notifier.error(toProblem(error).detail);
    }
  }

  private async openDialog(data: ConnectorTypeDialogData): Promise<boolean> {
    const result = await firstValueFrom(
      this.dialog
        .open<ConnectorTypeDialog, ConnectorTypeDialogData, ConnectorType>(ConnectorTypeDialog, {
          data,
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
    return result !== undefined;
  }
}
