import { Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { LucideImageOff, LucideImageUp, LucideTrash2 } from '@lucide/angular';

/** TRD 16.1: JPEG, PNG o WebP de hasta 10 MiB. El backend vuelve a validar el contenido real. */
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_BYTES = 10 * 1024 * 1024;

/** Imagen del equipo. La URL es firmada y temporal; quien puede editar la cambia o la quita. */
@Component({
  selector: 'sarc-equipment-image',
  imports: [MatButtonModule, LucideImageUp, LucideImageOff, LucideTrash2],
  template: `
    <figure class="frame">
      @if (url(); as src) {
        <img [src]="src" [alt]="'Imagen de ' + name()" loading="lazy" />
      } @else {
        <div class="placeholder">
          <svg lucideImageOff [size]="40" aria-hidden="true"></svg>
          <span>Sin imagen</span>
        </div>
      }
    </figure>
    @if (editable()) {
      <div class="actions">
        <input
          #picker
          type="file"
          class="sarc-visually-hidden"
          [accept]="accepted"
          (change)="onPicked(picker)"
        />
        <button matButton="tonal" type="button" [disabled]="busy()" (click)="picker.click()">
          <svg lucideImageUp [size]="18"></svg>
          {{ busy() ? 'Subiendo...' : url() ? 'Cambiar imagen' : 'Subir imagen' }}
        </button>
        @if (url()) {
          <button matButton type="button" [disabled]="busy()" (click)="remove.emit()">
            <svg lucideTrash2 [size]="18"></svg>
            Quitar
          </button>
        }
      </div>
      @if (error(); as message) {
        <p class="error" role="alert">{{ message }}</p>
      }
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
    }

    .frame {
      margin: 0;
      overflow: hidden;
      aspect-ratio: 4 / 3;
      border-radius: var(--sarc-radius-md);
      background: var(--mat-sys-surface-container);
    }

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .placeholder {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: var(--sarc-space-2);
      height: 100%;
      color: var(--mat-sys-on-surface-variant);
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--sarc-space-2);
    }

    .error {
      margin: 0;
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class EquipmentImage {
  readonly url = input<string | null | undefined>();
  readonly name = input.required<string>();
  readonly editable = input(false);
  readonly busy = input(false);
  readonly upload = output<File>();
  readonly remove = output<void>();

  protected readonly accepted = ACCEPTED_TYPES.join(',');
  protected readonly error = signal<string | undefined>(undefined);

  protected onPicked(picker: HTMLInputElement): void {
    const file = picker.files?.item(0);
    picker.value = '';
    if (file == null) {
      return;
    }
    if (!ACCEPTED_TYPES.includes(file.type)) {
      this.error.set('La imagen debe ser JPEG, PNG o WebP.');
    } else if (file.size > MAX_BYTES) {
      this.error.set('La imagen no puede pesar más de 10 MB.');
    } else {
      this.error.set(undefined);
      this.upload.emit(file);
    }
  }
}
