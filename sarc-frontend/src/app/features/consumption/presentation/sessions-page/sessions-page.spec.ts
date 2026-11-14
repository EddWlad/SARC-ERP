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
            refreshFailed: signal(false),
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

describe('SessionsPage source time (Feature 009)', () => {
  for (const handset of [false, true]) {
    it(`shows each end in its own source offset with seconds (handset=${handset})`, () => {
      const enerlink = {
        id: 'e',
        externalSessionId: '1211',
        startedAt: '2026-11-11T00:30:00Z',
        endedAt: '2026-11-11T02:10:05Z',
        sourceUtcOffsetMinutes: 0,
        sourceEndUtcOffsetMinutes: -300,
        startDate: '2026-11-11',
        source: 'ENERLINK',
        energyKwh: 8,
        state: 'VALIDATED',
        canApproveSource: false,
        version: 1,
        revision: 1,
      } as Session;
      const wallbox = {
        id: 'w',
        externalSessionId: 'W-77',
        startedAt: '2026-11-11T04:59:11Z',
        endedAt: '2026-11-11T05:30:00Z',
        sourceUtcOffsetMinutes: -300,
        startDate: '2026-11-10',
        source: 'WALLBOX',
        energyKwh: 5,
        state: 'VALIDATED',
        canApproveSource: false,
        version: 1,
        revision: 1,
      } as Session;
      const facade = {
        sessions: signal([enerlink, wallbox]),
        summary: signal(undefined),
        filters: signal({}),
        state: signal(''),
        energyReview: signal(''),
        text: signal(''),
        hasOwnFilters: signal(false),
        loading: signal(false),
        failed: signal(false),
        refreshFailed: signal(false),
        totalElements: signal(2),
        pageIndex: signal(0),
        pageSize: signal(20),
        reload: vi.fn(),
      };
      TestBed.configureTestingModule({
        imports: [SessionsPage],
        providers: [
          { provide: SessionStore, useValue: { hasAnyRole: () => false } },
          { provide: Notifier, useValue: { success: vi.fn(), error: vi.fn() } },
          { provide: MatDialog, useValue: { open: vi.fn() } },
          { provide: BreakpointObserver, useValue: { observe: () => of({ matches: handset }) } },
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
      const text = (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';

      // Enerlink: inicio en UTC+00:00 y fin declarado en UTC-05:00, sin depender de la zona del navegador.
      expect(text).toContain('11/11/2026 00:30:00 (UTC+00:00)');
      expect(text).toMatch(/21:10:05 \(UTC-05:00\)/);
      // Wallbox: hora de Ecuador, sin etiqueta y con el fin en el mismo desplazamiento.
      expect(text).toContain('10/11/2026 23:59:11');
      expect(text).toContain('00:30:00');
      expect(text).not.toContain('23:59:11 (UTC');
      fixture.destroy();
    });
  }
});

describe('SessionsPage session actions (Feature 009, US3)', () => {
  for (const handset of [false, true]) {
    for (const editor of [false, true]) {
      it(`offers Ver to everyone and Editar/Eliminar only to editors (handset=${handset}, editor=${editor})`, async () => {
        const session = {
          id: 's',
          externalSessionId: 'QA-9',
          startedAt: '2026-10-21T15:00:00Z',
          sourceUtcOffsetMinutes: -300,
          startDate: '2026-10-21',
          source: 'WALLBOX',
          energyKwh: 8,
          state: 'VALIDATED',
          canApproveSource: false,
          version: 3,
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
          refreshFailed: signal(false),
          totalElements: signal(1),
          pageIndex: signal(0),
          pageSize: signal(20),
          detail: vi.fn(),
          edit: vi.fn(),
          delete: vi.fn(),
          reload: vi.fn(),
        };
        const open = vi.fn<
          (component: unknown, config: { data: { action: string; session: Session } }) => unknown
        >(() => ({ afterClosed: () => of(undefined) }));
        const dialog = { open };
        TestBed.configureTestingModule({
          imports: [SessionsPage],
          providers: [
            { provide: SessionStore, useValue: { hasAnyRole: () => editor } },
            { provide: Notifier, useValue: { success: vi.fn(), error: vi.fn() } },
            { provide: MatDialog, useValue: dialog },
            { provide: BreakpointObserver, useValue: { observe: () => of({ matches: handset }) } },
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
        const view = host.querySelector<HTMLButtonElement>(
          'button[aria-label="Ver la sesión QA-9"]',
        );
        expect(view).not.toBeNull();
        view?.click();
        expect(open.mock.lastCall?.[1].data.action).toBe('view');

        if (!handset && editor) {
          host.querySelector<HTMLButtonElement>('button[aria-label^="Acciones"]')?.click();
          fixture.detectChanges();
          await fixture.whenStable();
        }
        const root = handset ? host : document.body;
        const labels = Array.from(root.querySelectorAll('button'), (b) => b.textContent?.trim());
        expect(labels.some((label) => label?.startsWith('Editar'))).toBe(editor);
        expect(labels.some((label) => label?.startsWith('Eliminar'))).toBe(editor);
        if (editor) {
          Array.from(root.querySelectorAll('button'))
            .find((b) => b.textContent?.trim().startsWith('Eliminar'))
            ?.click();
          expect(open.mock.lastCall?.[1].data.action).toBe('delete');
          expect(open.mock.lastCall?.[1].data.session.version).toBe(3);
        }
        fixture.destroy();
      });
    }
  }
});

describe('SessionsPage background refresh (Feature 009, US4)', () => {
  const row = (id: string, energyKwh: number) =>
    ({
      id,
      externalSessionId: id,
      startedAt: '2026-11-11T14:00:00Z',
      sourceUtcOffsetMinutes: -300,
      source: 'ENERLINK',
      energyKwh,
      state: 'VALIDATED',
      canApproveSource: false,
      version: 1,
      revision: 1,
    }) as Session;

  function render() {
    const facade = {
      sessions: signal([row('A', 5), row('B', 8)]),
      summary: signal(undefined),
      filters: signal({}),
      state: signal(''),
      energyReview: signal(''),
      text: signal(''),
      hasOwnFilters: signal(false),
      loading: signal(false),
      failed: signal(false),
      refreshFailed: signal(false),
      totalElements: signal(2),
      pageIndex: signal(0),
      pageSize: signal(20),
      reload: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [SessionsPage],
      providers: [
        { provide: SessionStore, useValue: { hasAnyRole: () => true } },
        { provide: Notifier, useValue: { success: vi.fn(), error: vi.fn() } },
        { provide: MatDialog, useValue: { open: vi.fn() } },
        { provide: BreakpointObserver, useValue: { observe: () => of({ matches: false }) } },
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
    return { fixture, facade, host: fixture.nativeElement as HTMLElement };
  }

  it('keeps the rows and the focus when a refresh brings the same sessions', () => {
    const { fixture, facade, host } = render();
    const rows = Array.from(host.querySelectorAll('tr.mat-mdc-row'));
    const view = host.querySelector<HTMLButtonElement>('button[aria-label="Ver la sesión A"]')!;
    view.focus();

    facade.sessions.set([row('A', 5), row('B', 9)]);
    fixture.detectChanges();

    const after = Array.from(host.querySelectorAll('tr.mat-mdc-row'));
    expect(after).toEqual(rows);
    expect(document.activeElement).toBe(view);
    expect(after[1].textContent).toContain('9');
  });

  it('keeps the list visible and warns when only the refresh failed', () => {
    const { fixture, facade, host } = render();

    facade.refreshFailed.set(true);
    fixture.detectChanges();

    expect(host.querySelector('[role="status"]')?.textContent).toContain(
      'No se pudo actualizar el histórico',
    );
    expect(host.querySelectorAll('tr.mat-mdc-row')).toHaveLength(2);
    expect(host.textContent).not.toContain('No se pudo cargar el histórico');
  });
});
