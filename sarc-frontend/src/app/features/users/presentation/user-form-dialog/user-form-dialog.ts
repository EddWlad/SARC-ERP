import { Component, inject, signal } from '@angular/core';
import {
  disabled,
  email,
  form,
  FormField,
  maxLength,
  minLength,
  required,
  submit,
} from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideTriangleAlert } from '@lucide/angular';

import { Role, ROLE_LABELS, ROLES } from '../../../../core/auth/role';
import { toProblem } from '../../../../core/http/problem';
import { User, USER_STATUS_LABELS, USER_STATUSES, UserStatus } from '../../domain/user';
import { guardUnsavedChanges } from '../../../../shared/ui/unsaved-changes';

export interface UserFormValue {
  email: string;
  fullName: string;
  roles: Role[];
  status: UserStatus;
}

export interface UserFormDialogData<T> {
  /** Usuario a editar; sin el, el dialogo crea uno nuevo. */
  readonly user?: User;
  /** Persiste el formulario. Si falla, el dialogo sigue abierto y muestra el error. */
  readonly save: (value: UserFormValue) => Promise<T>;
}

@Component({
  selector: 'sarc-user-form-dialog',
  imports: [
    FormField,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    LucideTriangleAlert,
  ],
  templateUrl: './user-form-dialog.html',
  styleUrl: './user-form-dialog.scss',
})
export class UserFormDialog<T> {
  private readonly data = inject<UserFormDialogData<T>>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<UserFormDialog<T>, T>>(MatDialogRef);
  /** Esc, clic fuera o Cancelar no descartan cambios sin confirmarlo. */
  protected readonly unsaved = guardUnsavedChanges(() => this.userForm().dirty());

  protected readonly editing = this.data.user !== undefined;
  protected readonly roles = ROLES;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly statuses = USER_STATUSES;
  protected readonly statusLabels = USER_STATUS_LABELS;

  protected readonly model = signal<UserFormValue>({
    email: this.data.user?.email ?? '',
    fullName: this.data.user?.fullName ?? '',
    roles: [...(this.data.user?.roles ?? [])],
    status: this.data.user?.status ?? 'ACTIVE',
  });
  protected readonly userForm = form(this.model, (path) => {
    required(path.email, { message: 'Ingrese el correo' });
    email(path.email, { message: 'El correo no es válido' });
    maxLength(path.email, 254, { message: 'Máximo 254 caracteres' });
    disabled(path.email, () => this.editing);
    required(path.fullName, { message: 'Ingrese el nombre' });
    maxLength(path.fullName, 150, { message: 'Máximo 150 caracteres' });
    minLength(path.roles, 1, { message: 'Seleccione al menos un rol' });
  });
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    void submit(this.userForm, async () => {
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        this.dialogRef.close(await this.data.save(this.model()));
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
