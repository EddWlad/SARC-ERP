import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Role } from '../../../../core/auth/role';
import { Notifier } from '../../../../core/notifications/notifier';
import { SessionStore } from '../../../../core/auth/session-store';
import { ChartView } from '../../../../shared/ui/chart/chart-view';
import { ShareChart } from '../../../../shared/ui/share-chart/share-chart';
import { TopEquipment } from '../../../../shared/ui/top-equipment/top-equipment';
import { DashboardFacade } from '../../application/dashboard-facade';
import { IntermittencySummary } from '../../domain/intermittency-summary';
import { summary } from '../../testing/summary-fixtures';
import { IntermittencyFilterBar } from '../intermittency-filter-bar/intermittency-filter-bar';
import { AvailabilitySection } from '../availability-section/availability-section';
import { OutsideDetails } from '../outside-details/outside-details';
import { IntermittencyDashboardPage } from './dashboard-page';

interface PageInternals {
  byBand(): { label: string; value: number; valueText: string }[];
  concentration(): { label: string; value: number }[];
  recurrence(): { label: string; value: number }[];
  topRows(): { position: number; name: string; valueText: string; detail: string }[];
  days(): string[];
  dailyHours(): { values: (number | null)[] };
  toReview(): number;
}

describe('IntermittencyDashboardPage', () => {
  const facade = {
    preset: signal('FIRST_HALF'),
    month: signal('2026-10'),
    firstDay: signal(''),
    lastDay: signal(''),
    points: signal([]),
    downloading: signal<'PDF' | 'XLSX' | undefined>(undefined),
    downloadReport: vi.fn(),
    rangeError: signal(''),
    today: signal('2026-10-20'),
    summary: signal<IntermittencySummary | undefined>(undefined),
    loading: signal(false),
    error: signal(''),
    reload: vi.fn(),
    choosePreset: vi.fn(),
    chooseMonth: vi.fn(),
    chooseRange: vi.fn(),
    choosePoints: vi.fn(),
    clearFilters: vi.fn(),
  };

  function render(data: IntermittencySummary | undefined, roles: readonly Role[] = ['GERENTE']) {
    TestBed.resetTestingModule();
    facade.summary.set(data);
    TestBed.configureTestingModule({
      imports: [IntermittencyDashboardPage],
      providers: [
        provideRouter([]),
        { provide: Notifier, useValue: notifier },
        {
          provide: SessionStore,
          useValue: {
            hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles.includes(role)),
          },
        },
      ],
    });
    TestBed.overrideComponent(IntermittencyDashboardPage, {
      set: { providers: [{ provide: DashboardFacade, useValue: facade }] },
    });
    TestBed.overrideComponent(IntermittencyDashboardPage, {
      remove: {
        imports: [
          AvailabilitySection,
          ChartView,
          IntermittencyFilterBar,
          OutsideDetails,
          ShareChart,
          TopEquipment,
        ],
      },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    const fixture = TestBed.createComponent(IntermittencyDashboardPage);
    fixture.detectChanges();
    return {
      root: fixture.nativeElement as HTMLElement,
      page: fixture.componentInstance as unknown as PageInternals,
    };
  }

  const notifier = { error: vi.fn(), success: vi.fn() };

  const text = (root: HTMLElement) => root.textContent?.replace(/\s+/g, ' ') ?? '';

  beforeEach(() => {
    facade.error.set('');
    facade.reload.mockReset();
    facade.downloadReport.mockReset().mockResolvedValue(undefined);
    facade.downloading.set(undefined);
    notifier.error.mockReset();
  });

  const reportButtons = (root: HTMLElement) =>
    Array.from(root.querySelectorAll<HTMLButtonElement>('.header-actions button'));

  it('ofrece el informe en PDF y en Excel con los filtros vigentes', () => {
    const { root } = render(summary());

    const buttons = reportButtons(root);
    expect(buttons.map((button) => button.textContent?.trim())).toEqual(['Informe PDF', 'Excel']);
    buttons[0].click();
    buttons[1].click();
    expect(facade.downloadReport.mock.calls).toEqual([['PDF'], ['XLSX']]);
  });

  it('mientras genera uno muestra su estado y no deja pedir otro', () => {
    facade.downloading.set('PDF');
    const { root } = render(summary());

    const buttons = reportButtons(root);
    expect(buttons.map((button) => button.textContent?.trim())).toEqual(['Generando...', 'Excel']);
    expect(buttons.every((button) => button.disabled)).toBe(true);
  });

  it('sin cifras todavia no deja descargar', () => {
    const { root } = render(undefined);

    expect(reportButtons(root).every((button) => button.disabled)).toBe(true);
  });

  it('si el informe falla avisa con el mensaje del servidor', async () => {
    facade.downloadReport.mockRejectedValue({ detail: 'El informe supera 50000 ventanas.' });
    const { root } = render(summary());

    reportButtons(root)[0].click();
    await Promise.resolve();
    await Promise.resolve();

    expect(notifier.error).toHaveBeenCalledWith('El informe supera 50000 ventanas.');
  });

  it('si el error no trae mensaje avisa con uno por defecto', async () => {
    facade.downloadReport.mockRejectedValue(new Error('fallo local'));
    const { root } = render(summary());

    reportButtons(root)[1].click();
    await Promise.resolve();
    await Promise.resolve();

    expect(notifier.error).toHaveBeenCalledWith('No se pudo generar el informe.');
  });

  it('muestra los indicadores oficiales con sus unidades', () => {
    const { root } = render(summary());

    const clean = (selector: string) =>
      Array.from(root.querySelectorAll(selector), (node) =>
        node.textContent?.replace(/\s+/g, ' ').trim(),
      );
    const values = clean('.kpi-value');
    expect(values.slice(0, 5)).toEqual(['38:02 h', '2', '5', '4', '1']);
    // El separador decimal depende del idioma de la aplicacion.
    expect(values[5]).toMatch(/^456[.,]4 min$/);
    expect(clean('.kpi-label')).toEqual([
      'Tiempo confirmado fuera de línea',
      'Cargadores afectados',
      'Ventanas completas',
      'Días-equipo',
      'Equipos recurrentes (3 o más días)',
      'Promedio por ventana',
    ]);
  });

  it('rotula el periodo con su rango real y la fecha de calculo', () => {
    const { root } = render(summary());

    expect(text(root)).toContain('Primera quincena · 01/10/2026 al 15/10/2026');
    expect(text(root)).toContain('Calculado 2026-10-20 10:00');
  });

  it('lo que no es oficial va aparte, con la duracion provisional rotulada', () => {
    const { root, page } = render(summary());

    const excluded = text(root.querySelector('.excluded') as HTMLElement);
    expect(excluded).toContain('Fuera de los indicadores oficiales');
    // Por revisar = ventanas por revisar + filas en conflicto + filas por vincular.
    expect(page.toReview()).toBe(4);
    expect(excluded).toContain('Ventanas por revisar (4 h o más)2');
    expect(excluded).toContain('Filas en conflicto1');
    expect(excluded).toContain('Filas por vincular1');
    expect(excluded).toContain('Eventos abiertos1 · 2:15 h provisional');
    expect(excluded).toContain('Ventanas de prueba3');
    expect(excluded).toContain('no suman en los totales');
  });

  it('las tres graficas de pastel suman el tiempo oficial', () => {
    const { page } = render(summary());

    for (const slices of [page.byBand(), page.concentration(), page.recurrence()]) {
      expect(slices.reduce((sum, slice) => sum + slice.value, 0)).toBe(2282);
    }
    expect(page.byBand().map((slice) => slice.label)).toEqual([
      'Menos de 5 min',
      '5 a 14 min',
      '15 a 59 min',
      '1 h a menos de 4 h',
      '4 h o más',
    ]);
    expect(page.byBand()[4].valueText).toBe('36:42 h · 4 ventanas');
  });

  it('el Top 10 trae posicion compartida, tiempo en h:mm y ventanas y dias', () => {
    const { page } = render(
      summary({
        top: [
          { equipmentId: 'a', code: '1', name: 'A', minutes: 100, windows: 2, days: 2 },
          { equipmentId: 'b', code: '2', name: 'B', minutes: 100, windows: 1, days: 1 },
          { equipmentId: 'c', code: '3', name: 'C', minutes: 50, windows: 1, days: 1 },
        ],
      }),
    );

    expect(page.topRows().map((row) => row.position)).toEqual([1, 1, 3]);
    expect(page.topRows()[0].valueText).toBe('1:40 h');
    expect(page.topRows()[0].detail).toBe('2 ventanas · 2 días');
    expect(page.topRows()[1].detail).toBe('1 ventana · 1 día');
  });

  it('la serie diaria va en horas decimales y con los dias del periodo', () => {
    const { page } = render(summary());

    expect(page.days()).toEqual(['05/10', '06/10', '07/10']);
    expect(page.dailyHours().values).toEqual([20, 0, 1082 / 60]);
  });

  it('avisa que una ventana que cruza la medianoche cuenta en cada dia solo cuando pasa', () => {
    expect(text(render(summary()).root)).not.toContain('cruza la medianoche');
    expect(text(render(summary({ dailyWindowsMayExceedTotal: true })).root)).toContain(
      'Una ventana que cruza la medianoche cuenta en cada día que toca',
    );
  });

  it('sin tiempo confirmado dice que no hay intermitencias y no dibuja graficos', () => {
    const { root } = render(
      summary({
        official: { minutes: 0, affectedPoints: 0, windows: 0, pointDays: 0, recurrentPoints: 0 },
        top: [],
        daily: [],
        byDurationBand: [],
      }),
    );

    expect(text(root)).toContain('No hay intermitencias confirmadas en el período.');
    expect(root.querySelector('sarc-chart-view')).toBeNull();
    expect(root.querySelector('sarc-share-chart')).toBeNull();
    // Los indicadores y lo que no es oficial se siguen mostrando.
    expect(root.querySelectorAll('.kpi-tile')).toHaveLength(6);
    expect(text(root)).toContain('—');
  });

  it('el detalle de lo que no es oficial solo se ve cuando el servidor lo envia', () => {
    const details = { windows: [], pendingRows: [], truncated: false };
    const base = summary();

    const reader = render(base, ['GERENTE']);
    expect(reader.root.querySelector('sarc-outside-details')).toBeNull();
    expect(reader.root.textContent).not.toContain('Ir a Revisión');

    const editor = render({ ...base, outsideOfficial: { ...base.outsideOfficial, details } }, [
      'ANALISTA',
    ]);
    expect(editor.root.querySelector('sarc-outside-details')).not.toBeNull();
    expect(editor.root.textContent).toContain('Ir a Revisión');
  });

  it('un editor sin detalle del servidor no ve el enlace a Revision', () => {
    const { root } = render(summary(), ['ANALISTA']);

    expect(root.querySelector('sarc-outside-details')).toBeNull();
    expect(root.textContent).not.toContain('Ir a Revisión');
  });

  it('la disponibilidad estimada es una seccion propia solo para Analista o Administrador, solos o combinados', () => {
    for (const roles of [
      ['ANALISTA'],
      ['ADMINISTRADOR'],
      ['ANALISTA', 'GERENTE'],
      ['ADMINISTRADOR', 'ASISTENTE'],
    ] as Role[][]) {
      expect(
        render(summary(), roles).root.querySelector('sarc-availability-section'),
      ).not.toBeNull();
    }
  });

  it('sin el permiso la seccion de disponibilidad ni se monta, con uno o varios roles de consulta', () => {
    for (const roles of [
      ['GERENTE'],
      ['SUPERVISOR'],
      ['ASISTENTE'],
      ['GERENTE', 'SUPERVISOR', 'ASISTENTE'],
    ] as Role[][]) {
      expect(render(summary(), roles).root.querySelector('sarc-availability-section')).toBeNull();
    }
  });

  it('un error se muestra con un reintento', () => {
    facade.error.set('El rango personalizado admite hasta 366 dias');
    const { root } = render(summary());

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('hasta 366 dias');
    (root.querySelector('.state button') as HTMLButtonElement).click();
    expect(facade.reload).toHaveBeenCalledOnce();
  });
});
