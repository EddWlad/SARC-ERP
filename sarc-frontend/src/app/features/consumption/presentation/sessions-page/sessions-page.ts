import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import {
  EnergyReviewDialog,
  EnergyReviewDialogData,
} from '../energy-review-dialog/energy-review-dialog';
import {
  SessionAction,
  SessionActionDialog,
  SessionActionDialogData,
} from '../session-action-dialog/session-action-dialog';
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
import {
  SOURCE_DATE_TIME,
  SOURCE_TIME,
  sourceEndUtcLabel,
  sourceEndUtcOffset,
  sourceUtcLabel,
  sourceUtcOffset,
} from '../../domain/source-time';
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
  private readonly dialog = inject(MatDialog);

  protected readonly facade = inject(SessionsFacade);
  protected readonly quantities = inject(QuantityFormat);
  private readonly sessionStore = inject(SessionStore);
  protected get canEdit(): boolean {
    return this.sessionStore.hasAnyRole(CONSUMPTION_EDITORS);
  }
  protected readonly isHandset = injectIsHandset();
  protected readonly linking = signal(false);
  protected readonly columns = ['start', 'equipment', 'source', 'energy', 'state', 'actions'];
  protected readonly pageSizeOptions = [10, 20, 50, 100];
  /** Un lector gerencial solo consulta oficiales: el servidor rechaza los estados operativos. */
  protected get states(): readonly SessionState[] {
    return this.canEdit ? SESSION_STATES : ['VALIDATED'];
  }
  protected readonly stateLabels = SESSION_STATE_LABELS;
  protected readonly linkIssueLabels = LINK_ISSUE_LABELS;
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly networkLabels = NETWORK_LABELS;
  /** Hora tal como la escribio la fuente (Feature 009): misma base que la fecha de conteo. */
  protected readonly sourceOffset = sourceUtcOffset;
  protected readonly sourceLabel = sourceUtcLabel;
  protected readonly endOffset = sourceEndUtcOffset;
  protected readonly endLabel = sourceEndUtcLabel;
  protected readonly sourceDateTime = SOURCE_DATE_TIME;
  protected readonly sourceTime = SOURCE_TIME;

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
  /** Un refresco conserva las filas (foco, menu abierto) y solo actualiza sus datos (US4). */
  protected readonly trackSession = (_: number, session: Session) => session.id;

  protected sessionOf(row: Session): Session {
    return row;
  }

  protected energyAction(session: Session): string {
    if (!this.canEdit) {
      return 'Justificación aprobada';
    }
    return session.energyReview ? 'Revisar energía' : 'Historial energético';
  }

  protected openEnergyReview(session: Session): void {
    const data: EnergyReviewDialogData = {
      session,
      canEdit: this.canEdit && session.energyReview != null,
      history: () => this.facade.energyReviews(session.id),
      save: (decision, reason) => this.facade.decideEnergy(session, decision, reason),
      stale: () => this.facade.reload(),
    };
    this.dialog.open(EnergyReviewDialog, { data, maxWidth: '95vw', width: '600px' });
  }

  /** Ver, editar o eliminar (Feature 009, US3); al guardar se recarga la consulta vigente. */
  protected openAction(session: Session, action: SessionAction): void {
    const data: SessionActionDialogData = {
      action,
      session,
      detail: () => this.facade.detail(session.id),
      edit: (changes, reason) => this.facade.edit(session, changes, reason),
      delete: (reason) => this.facade.delete(session, reason),
      stale: () => this.facade.reload(),
    };
    this.dialog
      .open<SessionActionDialog, SessionActionDialogData, Session>(SessionActionDialog, {
        data,
        maxWidth: '95vw',
        width: '640px',
      })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.notifier.success(
            action === 'delete'
              ? 'Sesión eliminada. Ya no cuenta en los indicadores.'
              : 'Sesión actualizada. El estado refleja los controles vigentes.',
          );
        }
      });
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
      this.notifier.success(
        'Fuente aprobada. El estado de la sesión refleja los controles restantes.',
      );
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
        result.linked + result.awaitingApproval + result.awaitingEnergyReview === 0
          ? 'Ninguna sesión se pudo vincular'
          : amount(result.linked, 'sesión vinculada', 'sesiones vinculadas');
      const awaiting =
        result.awaitingApproval === 0
          ? ''
          : `; ${amount(result.awaitingApproval, 'queda', 'quedan')} por aprobar (fuente no preferente)`;
      const pending = amount(result.stillPending, 'sigue pendiente', 'siguen pendientes');
      this.notifier.success(
        `${linked}${awaiting}; ${result.awaitingEnergyReview} vinculadas con revisión energética; ${pending}.`,
      );
    } catch (error) {
      this.notifier.error(toProblem(error).detail);
    } finally {
      this.linking.set(false);
    }
  }
}
