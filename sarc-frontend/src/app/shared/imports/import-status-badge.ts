import { Component, input } from '@angular/core';

import { IMPORT_STATUS_LABELS, ImportStatus } from './import-batch';

/** Etapa de un lote de importacion. */
@Component({
  selector: 'sarc-import-status-badge',
  template: `
    <span
      class="badge"
      [class.badge-info]="status() === 'READY'"
      [class.badge-accent]="status() === 'COMPLETED'"
      [class.badge-warn]="status() === 'FAILED'"
      [class.badge-off]="status() === 'DISCARDED'"
      >{{ labels[status()] }}</span
    >
  `,
  styles: `
    @use '../ui/badges';
  `,
})
export class ImportStatusBadge {
  readonly status = input.required<ImportStatus>();

  protected readonly labels = IMPORT_STATUS_LABELS;
}
