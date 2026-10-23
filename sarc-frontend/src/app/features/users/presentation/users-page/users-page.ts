import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { LucideSearch, LucideUserPlus } from '@lucide/angular';
import { firstValueFrom, map } from 'rxjs';

import { Role, ROLE_LABELS, ROLES } from '../../../../core/auth/role';
import { toProblem } from '../../../../core/http/problem';
import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { Notifier } from '../../../../core/notifications/notifier';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../../../shared/ui/confirm-dialog/confirm-dialog';
import { UsersFacade } from '../../application/users-facade';
import {
  CreatedUser,
  User,
  USER_STATUS_LABELS,
  USER_STATUSES,
  UserStatus,
} from '../../domain/user';
import {
  TemporaryPasswordDialog,
  TemporaryPasswordDialogData,
} from '../temporary-password-dialog/temporary-password-dialog';
import { UserActionsMenu } from '../user-actions-menu/user-actions-menu';
import { UserBadges } from '../user-badges/user-badges';
import { UserFormDialog, UserFormDialogData } from '../user-form-dialog/user-form-dialog';

const HANDSET_QUERY = '(max-width: 767.98px)';

@Component({
  selector: 'sarc-users-page',
  imports: [
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    LucideUserPlus,
    LucideSearch,
    UserBadges,
    UserActionsMenu,
  ],
  providers: [UsersFacade, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  templateUrl: './users-page.html',
  styleUrl: './users-page.scss',
})
export class UsersPage {
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly facade = inject(UsersFacade);
  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe(HANDSET_QUERY)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly columns = ['user', 'roles', 'status', 'createdAt', 'actions'];
  protected readonly roles = ROLES;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly statuses = USER_STATUSES;
  protected readonly statusLabels = USER_STATUS_LABELS;
  protected readonly pageSizeOptions = [10, 20, 50, 100];

  protected onSearch(event: Event): void {
    this.facade.updateFilters({ text: (event.target as HTMLInputElement).value });
  }

  protected onRoleFilter(role: Role | ''): void {
    this.facade.updateFilters({ role });
  }

  protected onStatusFilter(status: UserStatus | ''): void {
    this.facade.updateFilters({ status });
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  protected roleNames(user: User): string {
    return user.roles.map((role) => this.roleLabels[role]).join(', ');
  }

  protected async create(): Promise<void> {
    const data: UserFormDialogData<CreatedUser> = {
      save: (value) =>
        this.facade.create({ email: value.email, fullName: value.fullName, roles: value.roles }),
    };
    const created = await firstValueFrom(
      this.dialog
        .open(UserFormDialog<CreatedUser>, { data, autoFocus: 'first-tabbable' })
        .afterClosed(),
    );
    if (created !== undefined) {
      this.notifier.success('Usuario creado.');
      this.showTemporaryPassword(created.user, created.temporaryPassword);
    }
  }

  protected async edit(user: User): Promise<void> {
    const data: UserFormDialogData<User> = {
      user,
      save: (value) =>
        this.facade.update(user.id, {
          version: user.version,
          fullName: value.fullName,
          roles: value.roles,
          status: value.status,
        }),
    };
    const updated = await firstValueFrom(
      this.dialog.open(UserFormDialog<User>, { data }).afterClosed(),
    );
    if (updated !== undefined) {
      this.notifier.success('Usuario actualizado.');
    }
  }

  protected async resetPassword(user: User): Promise<void> {
    const data: ConfirmDialogData = {
      title: 'Restablecer contraseña',
      message: `Se generará una contraseña temporal para ${user.fullName} y se cerrarán sus sesiones abiertas.`,
      confirmLabel: 'Restablecer',
    };
    const confirmed = await firstValueFrom(this.dialog.open(ConfirmDialog, { data }).afterClosed());
    if (confirmed !== true) {
      return;
    }
    try {
      this.showTemporaryPassword(user, await this.facade.resetPassword(user.id));
    } catch (error) {
      this.notifier.error(toProblem(error).detail);
    }
  }

  private showTemporaryPassword(user: User, temporaryPassword: string): void {
    const data: TemporaryPasswordDialogData = {
      fullName: user.fullName,
      email: user.email,
      temporaryPassword,
    };
    this.dialog.open(TemporaryPasswordDialog, { data, disableClose: true });
  }
}
