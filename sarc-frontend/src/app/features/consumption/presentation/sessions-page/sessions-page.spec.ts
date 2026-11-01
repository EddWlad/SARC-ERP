import { BreakpointObserver } from '@angular/cdk/layout';
import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { SessionStore } from '../../../../core/auth/session-store';
import { Notifier } from '../../../../core/notifications/notifier';
import { SessionsFacade } from '../../application/sessions-facade';
import { Session } from '../../domain/consumption';
import { ConsumptionFilterBar } from '../consumption-filter-bar/consumption-filter-bar';
import { SessionsPage } from './sessions-page';

describe('SessionsPage', () => {
  for (const state of ['ENERGY_REVIEW', 'TEST'] as const) {
    for (const handset of [false, true]) {
      for (const editor of [false, true]) {
        it(`keeps source approval independent of main state (state=${state}, handset=${handset}, editor=${editor})`, async () => {
          const session = {
            id: 's',
            externalSessionId: 'QA',
            startedAt: '2026-10-21T15:00:00Z',
            source: 'WALLBOX',
            energyKwh: 1500,
            energyReview: editor ? 'PENDING' : 'APPROVED',
            state,
            canApproveSource: true,
            version: 7,
            revision: 1,
          } as Session;
          const facade = {
            sessions: signal([session]),
            summary: signal(undefined),
            filters: signal({}),
            state: signal(''),
            energyReview: signal(''),
            text: signal(''),
            hasOwnFilters: signal(false),
            loading: signal(false),
            failed: signal(false),
            totalElements: signal(1),
            pageIndex: signal(0),
            pageSize: signal(20),
            approveSource: vi.fn().mockResolvedValue({ ...session, state: 'ENERGY_REVIEW' }),
            energyReviews: vi.fn().mockResolvedValue([]),
            decideEnergy: vi.fn(),
            reload: vi.fn(),
          };
          const notifier = { success: vi.fn(), error: vi.fn() };
          const dialog = { open: vi.fn() };
          TestBed.configureTestingModule({
            imports: [SessionsPage],
            providers: [
              { provide: SessionStore, useValue: { hasAnyRole: () => editor } },
              { provide: Notifier, useValue: notifier },
              { provide: MatDialog, useValue: dialog },
              {
                provide: BreakpointObserver,
                useValue: { observe: () => of({ matches: handset }) },
              },
            ],
          });
          TestBed.overrideComponent(SessionsPage, {
            set: { providers: [{ provide: SessionsFacade, useValue: facade }] },
          });
          TestBed.overrideComponent(SessionsPage, {
            remove: { imports: [ConsumptionFilterBar] },
            add: { schemas: [NO_ERRORS_SCHEMA] },
          });
          const fixture = TestBed.createComponent(SessionsPage);
          fixture.detectChanges();
          const host: HTMLElement = fixture.nativeElement;
          if (!handset && editor) {
            (host.querySelector('button[aria-label^="Acciones"]') as HTMLButtonElement).click();
            fixture.detectChanges();
            await fixture.whenStable();
          }
          const root = handset ? host : document.body;
          const source = Array.from(root.querySelectorAll('button')).find((b) =>
            b.textContent?.includes('Aprobar fuente'),
          );
          expect(source !== undefined).toBe(editor);
          if (source) {
            source.click();
            await fixture.whenStable();
            expect(facade.approveSource).toHaveBeenCalledWith(session);
            expect(notifier.success.mock.calls[0][0]).not.toContain('ya suma');
          }
          const history = Array.from(host.querySelectorAll('button')).find((b) =>
            b.textContent?.includes(editor ? 'Revisar energía' : 'Justificación aprobada'),
          );
          history?.click();
          expect(dialog.open).toHaveBeenCalled();
          expect(dialog.open.mock.calls[0][1].data.canEdit).toBe(editor);
          fixture.destroy();
        });
      }
    }
  }
});
