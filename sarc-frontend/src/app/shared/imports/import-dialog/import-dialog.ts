import { Component, inject, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideFileUp, LucideTriangleAlert } from '@lucide/angular';

import { toProblem } from '../../../core/http/problem';
import { ImportBatchLike, MAX_IMPORT_BYTES } from '../import-batch';
import { guardUnsavedChanges } from '../../ui/unsaved-changes';

/** Opcion que el usuario elige antes de cargar (p. ej. la plataforma de origen). */
export interface ImportChoice {
  readonly label: string;
  readonly requiredMessage: string;
  readonly options: readonly {
    readonly value: string;
    readonly label: string;
    readonly hint: string;
  }[];
}

export interface ImportDialogData {
  /** Que archivo se espera, bajo el boton de elegir archivo. */
  readonly fileHint: string;
  /** Ayuda bajo el area de pegado. */
  readonly pasteHint: string;
  readonly choice?: ImportChoice;
  readonly upload: (file: File, choice: string) => Promise<ImportBatchLike>;
  readonly paste: (text: string, choice: string) => Promise<ImportBatchLike>;
}

interface ImportFormValue {
  choice: string;
  text: string;
}

const ACCEPTED = '.xlsx,.xls,.csv,.txt';

/**
 * Nueva importacion: un archivo (CSV/XLSX, sin transformarlo) o filas pegadas desde la hoja, con
 * su encabezado. El resultado es el lote recien creado; su vista previa se prepara en segundo plano.
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
  protected readonly data = inject<ImportDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ImportDialog, ImportBatchLike>>(MatDialogRef);
  /** Esc, clic fuera o Cancelar no descartan cambios sin confirmarlo. */
  protected readonly unsaved = guardUnsavedChanges(() => this.importForm().dirty());

  protected readonly accepted = ACCEPTED;
  protected readonly mode = signal<'file' | 'paste'>('file');
  protected readonly file = signal<File | undefined>(undefined);
  protected readonly fileError = signal<string | undefined>(undefined);
  protected readonly errorMessage = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);

  protected readonly model = signal<ImportFormValue>({ choice: '', text: '' });
  protected readonly importForm = form(this.model, (path) => {
    required(path.choice, {
      message: this.data.choice?.requiredMessage ?? '',
      when: () => this.data.choice !== undefined,
    });
    required(path.text, {
      message: 'Pegue las filas con su encabezado',
      when: () => this.mode() === 'paste',
    });
    maxLength(path.text, MAX_IMPORT_BYTES, { message: 'El texto supera 20 MiB' });
  });

  /** Ayuda de la opcion elegida (p. ej. las columnas que se esperan de esa plataforma). */
  protected choiceHint(value: string): string | undefined {
    return this.data.choice?.options.find((option) => option.value === value)?.hint;
  }

  protected onFile(input: HTMLInputElement): void {
    const file = input.files?.item(0) ?? undefined;
    input.value = '';
    if (file !== undefined && file.size > MAX_IMPORT_BYTES) {
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
      this.fileError.set('Elija el archivo que va a importar.');
    }
    void submit(this.importForm, async () => {
      const { choice, text } = this.model();
      const file = this.file();
      if (this.mode() === 'file' && file === undefined) {
        return;
      }
      this.busy.set(true);
      this.errorMessage.set(undefined);
      try {
        this.dialogRef.close(
          this.mode() === 'file' && file !== undefined
            ? await this.data.upload(file, choice)
            : await this.data.paste(text, choice),
        );
      } catch (error) {
        this.errorMessage.set(toProblem(error).detail);
      } finally {
        this.busy.set(false);
      }
    });
  }
}
