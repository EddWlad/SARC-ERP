import { inject, Injector, Service } from '@angular/core';
import type { MatSnackBar, MatSnackBarConfig } from '@angular/material/snack-bar';

/**
 * Avisos breves al usuario (snackbar), anunciados a lectores de pantalla. El snackbar se carga con
 * el primer aviso: importarlo de forma estatica metia el overlay del CDK en el bundle inicial.
 */
@Service()
export class Notifier {
  private readonly injector = inject(Injector);
  private snackBar?: Promise<MatSnackBar>;

  success(message: string): void {
    void this.show(message, { duration: 4000, politeness: 'polite' });
  }

  error(message: string): void {
    void this.show(message, { duration: 8000, politeness: 'assertive' });
  }

  private async show(message: string, config: MatSnackBarConfig): Promise<void> {
    this.snackBar ??= import('@angular/material/snack-bar').then((module) =>
      this.injector.get(module.MatSnackBar),
    );
    (await this.snackBar).open(message, 'Cerrar', config);
  }
}
