import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Notifier } from '../../../../core/notifications/notifier';
import { ChartView } from '../../../../shared/ui/chart/chart-view';
import { ConsumptionDashboardFacade } from '../../application/consumption-dashboard-facade';
import { ConsumptionFilterBar } from '../consumption-filter-bar/consumption-filter-bar';
import { DailyTable } from '../daily-table/daily-table';
import { ShareChart } from '../share-chart/share-chart';
import { TopEquipment } from '../top-equipment/top-equipment';
import { DashboardPage } from './dashboard-page';

describe('DashboardPage', () => {
  it('renders exclusive backend totals and labels approved high readings as an official subset', () => {
    const totals = (sessions: number, energyKwh: number | null) => ({
      sessions,
      energyKwh,
      equipment: 1,
      sessionsWithoutEnergy: 0,
    });
    const facade = {
      summary: signal({
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
      }),
      loading: signal(false),
      error: signal(''),
      downloading: signal(undefined),
      filters: signal({}),
    };
    TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [{ provide: Notifier, useValue: {} }],
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
    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelectorAll('.kpi-value')[1].textContent?.trim()).toBe('5');
    expect(host.querySelectorAll('.kpi-label')[0].textContent).toContain('Energía conocida');
    const rows = Array.from(host.querySelectorAll('.metric-row'), (row) =>
      row.textContent?.replace(/\s+/g, ' '),
    );
    expect(rows.find((row) => row?.includes('Energía por revisar'))).toContain('2');
    expect(rows.find((row) => row?.includes('Energía rechazada'))).toContain('3');
    expect(host.textContent).toContain('incluidas en los indicadores oficiales');
  });
});
