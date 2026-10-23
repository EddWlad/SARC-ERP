import { Component, computed, input } from '@angular/core';

import {
  ADMIN_STATUS_LABELS,
  AdministrativeStatus,
  USAGE_STATUS_HINTS,
  USAGE_STATUS_LABELS,
  UsageStatus,
} from '../../domain/equipment';

/** Estado administrativo y, en puntos de carga, estado de uso. */
@Component({
  selector: 'sarc-equipment-badges',
  template: `
    <span
      class="badge"
      [class.badge-off]="adminStatus() === 'INACTIVE'"
      [class.badge-warn]="adminStatus() === 'DELETED'"
      >{{ adminLabel() }}</span
    >
    @if (usageStatus(); as usage) {
      <span
        class="badge"
        [class.badge-accent]="usage === 'FREE'"
        [class.badge-info]="usage === 'BLOCKED'"
        [class.badge-warn]="usage === 'DISCONNECTED'"
        [title]="usageHints[usage]"
        >{{ usageLabels[usage] }}</span
      >
    }
  `,
  styles: `
    @use '../../../../shared/ui/badges';

    :host {
      display: inline-flex;
      flex-wrap: wrap;
      gap: var(--sarc-space-1);
    }
  `,
})
export class EquipmentBadges {
  readonly adminStatus = input.required<AdministrativeStatus>();
  readonly usageStatus = input<UsageStatus | undefined>();

  protected readonly adminLabel = computed(() => ADMIN_STATUS_LABELS[this.adminStatus()]);
  protected readonly usageLabels = USAGE_STATUS_LABELS;
  protected readonly usageHints = USAGE_STATUS_HINTS;
}
