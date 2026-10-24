import { Component, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideFileUp, LucideTriangleAlert } from '@lucide/angular';

import { toProblem } from '../../../../core/http/problem';
import { SessionSource, SOURCE_LABELS, SOURCES } from '../../domain/consumption';
import { ImportBatch, SOURCE_COLUMNS } from '../../domain/energy-import';

export interface ImportDialogData {
  readonly upload: (source: SessionSource, file: File) => Promise<ImportBatch>;
  readonly paste: (source: SessionSource, text: string) => Promise<ImportBatch>;
}

interface ImportFormValue {
  source: SessionSource | '';
  text: string;
}

/** TRD 09.1: el backend rechaza originales de mas de 20 MiB; aqui se avisa antes de subir. */
const MAX_BYTES = 20 * 1024 * 1024;
const ACCEPTED = '.xlsx,.xls,.csv,.txt';

/**
 * Nueva importacion: el reporte original de la plataforma (CSV/XLSX, sin transformarlo) o filas
 * pegadas desde la hoja, con su encabezado (PRD RF-003.1).
 */
@Component({
  selector: 'sarc-import-dialog',
  imports: [
    FormField,
    MatButtonModule,
    MatButtonToggleModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    LucideFileUp,
    LucideTriangleAlert,
  ],
  templateUrl: './import-dialog.html',
  styleUrl: './import-dialog.scss',
})
export class ImportDialog {
  private readonly data = inject<ImportDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ImportDialog, ImportBatch>>(MatDialogRef);

  protected readonly sources = SOURCES;
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly columns = SOURCE_COLUMNS;
  protected readonly accepted = ACCEPTED;
  protected readonly mode = signal<'file' | 'paste'>('file');
  protected readonly file = signal<File | undefined>(undefined);
  protected readonly fileError = signal<string | undefined>(undefined);
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly model = signal<ImportFormValue>({ source: '', text: '' });
  protected readonly importForm = form(this.model, (path) => {
    required(path.source, { message: 'Seleccione la plataforma de origen' });
    required(path.text, {
      message: 'Pegue las filas con su encabezado',
      when: () => this.mode() === 'paste',
    });
    maxLength(path.text, MAX_BYTES, { message: 'El texto supera 20 MiB' });
  });

  protected onFile(input: HTMLInputElement): void {
    const file = input.files?.item(0) ?? undefined;
    input.value = '';
    if (file !== undefined && file.size > MAX_BYTES) {
      this.fileError.set('El archivo supera 20 MiB.');
      this.file.set(undefined);
      return;
    }
    this.fileError.set(undefined);
    this.file.set(file);
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    if (this.mode() === 'file' && this.file() === undefined) {
      this.fileError.set('Elija el archivo exportado de la plataforma.');
    }
    void submit(this.importForm, async () => {
      const { source, text } = this.model();
      const file = this.file();
      if (source === '' || (this.mode() === 'file' && file === undefined)) {
        return;
      }
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        this.dialogRef.close(
          this.mode() === 'file' && file !== undefined
            ? await this.data.upload(source, file)
            : await this.data.paste(source, text),
        );
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
