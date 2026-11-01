import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { SessionStore } from '../../../../core/auth/session-store';
import { Notifier } from '../../../../core/notifications/notifier';
import { ChartView } from '../../../../shared/ui/chart/chart-view';
import { ConsumptionDashboardFacade } from '../../application/consumption-dashboard-facade';
import { ConsumptionFilterBar } from '../consumption-filter-bar/consumption-filter-bar';
import { DailyTable } from '../daily-table/daily-table';
import { ShareChart } from '../share-chart/share-chart';
import { TopEquipment } from '../top-equipment/top-equipment';
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
    return fixture.nativeElement as HTMLElement;
  }

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

  it('keeps zero operational categories visible to operators', () => {
    const rows = render(true).querySelectorAll('.excluded .metric-row');
    expect(rows).toHaveLength(5);
    expect(rows[1].textContent).toContain('0');
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
