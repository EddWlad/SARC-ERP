import { inject, Service } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Avisos breves al usuario (snackbar), anunciados a lectores de pantalla. */
@Service()
export class Notifier {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, 'Cerrar', { duration: 4000, politeness: 'polite' });
  }

  error(message: string): void {
    this.snackBar.open(message, 'Cerrar', { duration: 8000, politeness: 'assertive' });
  }
}
