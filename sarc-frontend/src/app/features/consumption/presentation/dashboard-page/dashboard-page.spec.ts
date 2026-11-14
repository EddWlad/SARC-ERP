import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { SessionStore } from '../../../../core/auth/session-store';
import { Notifier } from '../../../../core/notifications/notifier';
import { ChartView } from '../../../../shared/ui/chart/chart-view';
import { ConsumptionDashboardFacade } from '../../application/consumption-dashboard-facade';
import { ConsumptionFilterBar } from '../consumption-filter-bar/consumption-filter-bar';
import { DailyTable } from '../daily-table/daily-table';
import { ShareChart } from '../../../../shared/ui/share-chart/share-chart';
import { TopEquipment } from '../../../../shared/ui/top-equipment/top-equipment';
import { DashboardPage } from './dashboard-page';

describe('DashboardPage', () => {
  const OPERATIONAL = ['pendingLink', 'awaitingApproval', 'test', 'energyReview', 'energyRejected'];

  function render(operator: boolean, omit: readonly string[] = []) {
    const totals = (sessions: number, energyKwh: number | null) => ({
      sessions,
      energyKwh,
      equipment: 1,
      sessionsWithoutEnergy: 0,
    });
    const summary = {
      official: { ...totals(5, 34), sessionsWithoutEnergy: 1 },
      averageKwhPerSession: 8.5,
      pendingLink: totals(1, 5),
      awaitingApproval: totals(0, 0),
      test: totals(1, 1500),
      energyReview: totals(2, 3000),
      energyRejected: totals(3, 4500),
      approvedAboveThreshold: totals(1, 1500),
      top: [],
      daily: [],
      byNetwork: [],
      bySource: [],
      byType: [],
      equipmentWithoutEnergy: 0,
      generatedAt: '2026-10-21T15:00:00Z',
    } as Record<string, unknown>;
    omit.forEach((key) => delete summary[key]);
    const facade = {
      summary: signal(summary),
      loading: signal(false),
      error: signal(''),
      refreshError: signal<string | undefined>(undefined),
      downloading: signal(undefined),
      filters: signal({}),
    };
    TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        { provide: Notifier, useValue: {} },
        { provide: SessionStore, useValue: { hasAnyRole: () => operator } },
      ],
    });
    TestBed.overrideComponent(DashboardPage, {
      set: { providers: [{ provide: ConsumptionDashboardFacade, useValue: facade }] },
    });
    TestBed.overrideComponent(DashboardPage, {
      remove: { imports: [ChartView, ConsumptionFilterBar, DailyTable, ShareChart, TopEquipment] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;
    refresh = (message: string | undefined) => {
      facade.refreshError.set(message);
      fixture.detectChanges();
    };
    return host;
  }
  let host: HTMLElement;
  let refresh: (message: string | undefined) => void;

  it('keeps the indicators and charts when only a background refresh failed (Feature 009, US4)', () => {
    render(true);
    const kpis = host.querySelector('section[aria-label="Indicadores oficiales"]');
    const charts = Array.from(host.querySelectorAll('sarc-chart-view'));

    refresh('Servicio no disponible');

    expect(host.querySelector('[role="status"]')?.textContent).toContain(
      'No se pudieron actualizar las cifras; se muestran las anteriores. Servicio no disponible',
    );
    expect(host.querySelector('section[aria-label="Indicadores oficiales"]')).toBe(kpis);
    expect(Array.from(host.querySelectorAll('sarc-chart-view'))).toEqual(charts);
    expect(host.querySelector('[role="alert"]')).toBeNull();

    refresh(undefined);
    expect(host.querySelector('[role="status"]')).toBeNull();
    expect(host.querySelector('section[aria-label="Indicadores oficiales"]')).toBe(kpis);
  });

  it('renders exclusive backend totals and labels approved high readings as an official subset', () => {
    const host = render(true);
    expect(host.querySelectorAll('.kpi-value')[1].textContent?.trim()).toBe('5');
    expect(host.querySelectorAll('.kpi-label')[0].textContent).toContain('Energía conocida');
    const rows = Array.from(host.querySelectorAll('.metric-row'), (row) =>
      row.textContent?.replace(/\s+/g, ' '),
    );
    expect(rows.find((row) => row?.includes('Energía por revisar'))).toContain('2');
    expect(rows.find((row) => row?.includes('Energía rechazada'))).toContain('3');
    expect(host.textContent).toContain('incluidas en los indicadores oficiales');
  });

  it('states its calculation time in Ecuador time and its date criterion (Feature 009, FR-005)', () => {
    const note = render(true).querySelector('.generated')?.textContent?.replace(/\s+/g, ' ');
    expect(note).toContain('Calculado 21/10/2026 10:00 (hora de Ecuador)');
    expect(note).toContain('Fecha de inicio según la fuente');
  });

  it('keeps zero operational categories visible to operators', () => {
    const rows = render(true).querySelectorAll('.excluded .metric-row');
    expect(rows).toHaveLength(5);
    expect(rows[1].textContent).toContain('0');
  });

  it('groups the five categories in two titled blocks with the same quantity markup', () => {
    const host = render(true);
    const blocks = Array.from(host.querySelectorAll<HTMLElement>('.excluded .groups section'));

    expect(host.querySelector('#excluded-title')?.textContent).toContain(
      'Fuera de los indicadores oficiales',
    );
    expect(host.querySelector('.excluded')?.getAttribute('aria-labelledby')).toBe('excluded-title');
    expect(blocks.map((block) => block.querySelector('h3')?.textContent?.trim())).toEqual([
      'Vinculación y aprobación',
      'Revisión energética',
    ]);
    blocks.forEach((block) =>
      expect(host.querySelector(`#${block.getAttribute('aria-labelledby')}`)).not.toBeNull(),
    );
    expect(blocks.map((block) => block.querySelectorAll('.metric-row').length)).toEqual([3, 2]);
    host.querySelectorAll('.excluded .metric-row').forEach((row) => {
      expect(row.querySelector('.quantity strong')).not.toBeNull();
      expect(row.querySelector('.quantity .muted')?.textContent).toContain('kWh');
    });
    expect(host.querySelector('.excluded .dashboard-note')?.textContent).toContain(
      'no suman en los totales',
    );
  });

  it('hides the card from managerial readers and never paints omitted data as zero', () => {
    const host = render(false, OPERATIONAL);
    expect(host.querySelector('.excluded')).toBeNull();
    expect(host.textContent).not.toContain('Pendientes de vincular');
    expect(host.querySelectorAll('.kpi-value')[1].textContent?.trim()).toBe('5');
    expect(host.textContent).toContain('incluidas en los indicadores oficiales');
  });

  it('requires both the role and the data, so a lost session clears the card', () => {
    expect(render(false).querySelector('.excluded')).toBeNull();
    TestBed.resetTestingModule();
    expect(render(true, OPERATIONAL).querySelector('.excluded')).toBeNull();
  });
});
