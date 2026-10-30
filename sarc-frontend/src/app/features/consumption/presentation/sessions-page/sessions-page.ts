import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { LucideEllipsisVertical, LucideLink, LucideSearch } from '@lucide/angular';

import { SessionStore } from '../../../../core/auth/session-store';
import { toProblem } from '../../../../core/http/problem';
import { SpanishPaginatorIntl } from '../../../../core/i18n/spanish-paginator-intl';
import { Notifier } from '../../../../core/notifications/notifier';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { NETWORK_LABELS } from '../../../../shared/vocabulary/charging-infrastructure';
import { injectIsHandset } from '../../../../shared/ui/handset';
import { SessionsFacade } from '../../application/sessions-facade';
import {
  Session,
  LINK_ISSUE_LABELS,
  SESSION_STATE_LABELS,
  SESSION_STATES,
  SessionState,
  SOURCE_LABELS,
} from '../../domain/consumption';
import { CONSUMPTION_EDITORS } from '../../domain/consumption-permissions';
import { ConsumptionFilterBar } from '../consumption-filter-bar/consumption-filter-bar';
import { SessionStateBadge } from '../session-state-badge/session-state-badge';

/** Historico de sesiones (PRD RF-003.11): ID de la fuente, procedencia, equipo, horario y kWh. */
@Component({
  selector: 'sarc-sessions-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatMenuModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    LucideEllipsisVertical,
    LucideLink,
    LucideSearch,
    ConsumptionFilterBar,
    SessionStateBadge,
  ],
  providers: [SessionsFacade, { provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  templateUrl: './sessions-page.html',
  styleUrl: './sessions-page.scss',
})
export class SessionsPage {
  private readonly notifier = inject(Notifier);

  protected readonly facade = inject(SessionsFacade);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly canEdit = inject(SessionStore).hasAnyRole(CONSUMPTION_EDITORS);
  protected readonly isHandset = injectIsHandset();
  protected readonly linking = signal(false);
  protected readonly columns = ['start', 'equipment', 'source', 'energy', 'state', 'actions'];
  protected readonly pageSizeOptions = [10, 20, 50, 100];
  protected readonly states = SESSION_STATES;
  protected readonly stateLabels = SESSION_STATE_LABELS;
  protected readonly linkIssueLabels = LINK_ISSUE_LABELS;
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly networkLabels = NETWORK_LABELS;

  protected onSearch(event: Event): void {
    this.facade.search((event.target as HTMLInputElement).value);
  }

  protected onState(state: SessionState | ''): void {
    this.facade.filterState(state);
  }

  protected onPage(event: PageEvent): void {
    this.facade.changePage(event.pageIndex, event.pageSize);
  }

  // Las filas de mat-table llegan sin tipo a la plantilla: estos metodos las tipan.
  protected sessionOf(row: Session): Session {
    return row;
  }

  protected async toggleTest(session: Session): Promise<void> {
    const marking = session.state !== 'TEST';
    try {
      await this.facade.markTest(session, marking);
      this.notifier.success(marking ? 'Sesión marcada como prueba.' : 'Sesión reincorporada.');
    } catch (error) {
      const problem = toProblem(error);
      this.notifier.error(problem.detail);
      if (problem.code === 'CONCURRENT_MODIFICATION') {
        this.facade.reload();
      }
    }
  }

  protected async approveSource(session: Session): Promise<void> {
    try {
      await this.facade.approveSource(session);
      this.notifier.success('Fuente aprobada; la sesión ya suma en los indicadores.');
    } catch (error) {
      const problem = toProblem(error);
      this.notifier.error(problem.detail);
      if (problem.code === 'CONCURRENT_MODIFICATION') {
        this.facade.reload();
      }
    }
  }

  protected async linkPending(): Promise<void> {
    this.linking.set(true);
    try {
      const result = await this.facade.linkPending();
      const amount = this.quantities.amount.bind(this.quantities);
      const linked =
        result.linked + result.awaitingApproval === 0
          ? 'Ninguna sesión se pudo vincular'
          : amount(result.linked, 'sesión vinculada', 'sesiones vinculadas');
      const awaiting =
        result.awaitingApproval === 0
          ? ''
          : `; ${amount(result.awaitingApproval, 'queda', 'quedan')} por aprobar (fuente no preferente)`;
      const pending = amount(result.stillPending, 'sigue pendiente', 'siguen pendientes');
      this.notifier.success(`${linked}${awaiting}; ${pending}.`);
    } catch (error) {
      this.notifier.error(toProblem(error).detail);
    } finally {
      this.linking.set(false);
    }
  }
}
