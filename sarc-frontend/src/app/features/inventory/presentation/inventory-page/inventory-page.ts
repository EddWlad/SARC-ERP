import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import {
  LucideCable,
  LucideFileSpreadsheet,
  LucideFileText,
  LucideFileUp,
  LucideFilterX,
  LucidePlus,
  LucideSearch,
} from '@lucide/angular';
import { firstValueFrom } from 'rxjs';

import { SessionStore } from '../../../../core/auth/session-store';
import { Problem } from '../../../../core/http/problem';
import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { Notifier } from '../../../../core/notifications/notifier';
import { WIDE_DIALOG } from '../../../../shared/ui/dialog-sizes';
import { InventoryFacade } from '../../application/inventory-facade';
import {
  ADMIN_STATUS_LABELS,
  ADMIN_STATUSES,
  BRAND_LABELS,
  BRANDS,
  Equipment,
  EquipmentFilters,
  EquipmentSummary,
  NETWORK_LABELS,
  NETWORKS,
  TYPE_LABELS,
  TYPES,
  USAGE_STATUS_LABELS,
  USAGE_STATUSES,
} from '../../domain/equipment';
import { INVENTORY_EDITORS } from '../../domain/inventory-permissions';
import { ReportFormat } from '../../../../shared/reports/report-format';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { EquipmentBadges } from '../equipment-badges/equipment-badges';
import {
  EquipmentFormDialog,
  EquipmentFormDialogData,
} from '../equipment-form-dialog/equipment-form-dialog';
import { InventoryKpis } from '../inventory-kpis/inventory-kpis';

@Component({
  selector: 'sarc-inventory-page',
  imports: [
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    LucidePlus,
    LucideSearch,
    LucideFileText,
    LucideFileUp,
    LucideFileSpreadsheet,
    LucideFilterX,
    LucideCable,
    EquipmentBadges,
    InventoryKpis,
  ],
  providers: [InventoryFacade, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  templateUrl: './inventory-page.html',
  styleUrl: './inventory-page.scss',
})
export class InventoryPage {
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);

  protected readonly facade = inject(InventoryFacade);
  protected readonly canEdit = inject(SessionStore).hasAnyRole(INVENTORY_EDITORS);
  protected readonly isHandset = injectIsHandset();
  protected readonly columns = ['equipment', 'network', 'type', 'status', 'connectors', 'location'];
  protected readonly pageSizeOptions = [10, 20, 50, 100];

  protected readonly networks = NETWORKS;
  protected readonly networkLabels = NETWORK_LABELS;
  protected readonly types = TYPES;
  protected readonly typeLabels = TYPE_LABELS;
  protected readonly brands = BRANDS;
  protected readonly brandLabels = BRAND_LABELS;
  protected readonly statuses = ADMIN_STATUSES;
  protected readonly statusLabels = ADMIN_STATUS_LABELS;
  protected readonly usageStatuses = USAGE_STATUSES;
  protected readonly usageLabels = USAGE_STATUS_LABELS;

  // Las filas de mat-table llegan sin tipo a la plantilla: estos metodos las tipan.
  protected networkOf(item: EquipmentSummary): string {
    return NETWORK_LABELS[item.network];
  }

  protected typeOf(item: EquipmentSummary): string {
    return TYPE_LABELS[item.type];
  }

  protected brandOf(item: EquipmentSummary): string {
    return BRAND_LABELS[item.brand];
  }

  protected onSearch(event: Event): void {
    this.facade.updateFilters({ text: (event.target as HTMLInputElement).value });
  }

  protected onFilter(changes: Partial<EquipmentFilters>): void {
    this.facade.updateFilters(changes);
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  protected async download(format: ReportFormat): Promise<void> {
    try {
      await this.facade.downloadReport(format);
    } catch (error) {
      this.notifier.error((error as Problem).detail);
    }
  }

  protected async create(): Promise<void> {
    const data: EquipmentFormDialogData = { save: (draft) => this.facade.create(draft) };
    const created = await firstValueFrom(
      this.dialog
        .open<EquipmentFormDialog, EquipmentFormDialogData, Equipment>(EquipmentFormDialog, {
          ...WIDE_DIALOG,
          data,
          autoFocus: 'first-tabbable',
        })
        .afterClosed(),
    );
    if (created !== undefined) {
      this.notifier.success('Equipo registrado.');
      await this.router.navigate(['/inventario', created.id]);
    }
  }
}
