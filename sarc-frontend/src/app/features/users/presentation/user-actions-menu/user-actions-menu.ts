import { Component, input, output, OutputEmitterRef } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';
import { LucideEllipsisVertical, LucideKeyRound, LucidePencil } from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { User } from '../../domain/user';

/** Menu de acciones de una fila del listado de usuarios. */
@Component({
  selector: 'sarc-user-actions-menu',
  imports: [MatButtonModule, MatMenuModule, LucideEllipsisVertical, LucidePencil, LucideKeyRound],
  template: `
    <button
      matIconButton
      type="button"
      [matMenuTriggerFor]="menu"
      #trigger="matMenuTrigger"
      [attr.aria-label]="'Acciones para ' + user().fullName"
    >
      <svg lucideEllipsisVertical [size]="20"></svg>
    </button>
    <mat-menu #menu="matMenu" xPosition="before">
      <button mat-menu-item type="button" (click)="choose(trigger, edit)">
        <svg lucidePencil [size]="18"></svg>
        <span>Editar</span>
      </button>
      <button mat-menu-item type="button" (click)="choose(trigger, resetPassword)">
        <svg lucideKeyRound [size]="18"></svg>
        <span>Restablecer contraseña</span>
      </button>
    </mat-menu>
  `,
})
export class UserActionsMenu {
  readonly user = input.required<User>();
  readonly edit = output<User>();
  readonly resetPassword = output<User>();

  /** Con el menu ya cerrado, el dialogo devuelve el foco al disparador y no a un item destruido. */
  protected async choose(trigger: MatMenuTrigger, action: OutputEmitterRef<User>): Promise<void> {
    await firstValueFrom(trigger.menuClosed);
    action.emit(this.user());
  }
}
