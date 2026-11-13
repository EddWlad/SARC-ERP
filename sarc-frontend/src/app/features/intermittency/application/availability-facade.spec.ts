import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Role } from '../../../core/auth/role';
import { SessionStore } from '../../../core/auth/session-store';
import { FileSaver } from '../../../core/files/file-saver';
import { Availability } from '../domain/intermittency-availability';
import { AvailabilityFacade } from './availability-facade';
import { DashboardFacade } from './dashboard-facade';

const SUMMARY_URL = '/api/v1/intermittency/summary';
const AVAILABILITY_URL = '/api/v1/intermittency/availability';

const AVAILABILITY: Availability = {
  period: {
    preset: 'SECOND_HALF',
    from: '2026-10-16',
    to: '2026-11-01',
    cutOff: '2026-10-20T15:00:00-05:00',
    timeZone: 'America/Guayaquil',
  },
  aggregate: { percent: 99.5, evaluatedMinutes: 14400, offlineMinutes: 72 },
  coverage: { coveredDays: 1, daysInScope: 4 },
  blockedDays: [],
  points: [],
  exclusions: [],
  labels: { estimated: true, test: true, origin: 'Registro manual de intermitencias (Kibana)' },
  limitations: [],
};

describe('AvailabilityFacade', () => {
  let http: HttpTestingController;

  function create(roles: readonly Role[]): {
    availability: AvailabilityFacade;
    dashboard: DashboardFacade;
  } {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-20T15:00:00-05:00'));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: FileSaver, useValue: { download: vi.fn() } },
        {
          provide: SessionStore,
          useValue: {
            hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles.includes(role)),
          },
        },
        DashboardFacade,
        AvailabilityFacade,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    return {
      availability: TestBed.inject(AvailabilityFacade),
      dashboard: TestBed.inject(DashboardFacade),
    };
  }

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('con el permiso pide la disponibilidad con los mismos filtros del tablero', () => {
    const { availability, dashboard } = create(['ANALISTA']);
    dashboard.choosePoints([
      { id: 'p1', name: 'Punto A', assignedCode: '900001', adminStatus: 'ACTIVE' },
    ]);
    TestBed.tick();

    http.expectOne((candidate) => candidate.url === SUMMARY_URL).flush({});
    const request = http.expectOne((candidate) => candidate.url === AVAILABILITY_URL);
    expect(request.request.params.get('preset')).toBe('SECOND_HALF');
    expect(request.request.params.get('month')).toBe('2026-10');
    expect(request.request.params.getAll('equipmentIds')).toEqual(['p1']);
    request.flush(AVAILABILITY);

    expect(availability.allowed).toBe(true);
  });

  it('sin el permiso ni siquiera crea la peticion de disponibilidad', () => {
    for (const roles of [
      ['GERENTE'],
      ['SUPERVISOR'],
      ['ASISTENTE'],
      ['GERENTE', 'SUPERVISOR', 'ASISTENTE'],
    ] as Role[][]) {
      const { availability } = create(roles);
      TestBed.tick();

      http.expectOne((candidate) => candidate.url === SUMMARY_URL).flush({});
      http.expectNone((candidate) => candidate.url === AVAILABILITY_URL);
      expect(availability.allowed).toBe(false);
      expect(availability.data()).toBeUndefined();
      http.verify();
      vi.useRealTimers();
    }
  });

  it('quien combina un rol de consulta con Analista o Administrador la recibe', () => {
    for (const roles of [
      ['ANALISTA', 'GERENTE'],
      ['ADMINISTRADOR', 'ASISTENTE'],
    ] as Role[][]) {
      const { availability } = create(roles);
      TestBed.tick();

      http.expectOne((candidate) => candidate.url === SUMMARY_URL).flush({});
      http.expectOne((candidate) => candidate.url === AVAILABILITY_URL).flush(AVAILABILITY);
      expect(availability.allowed).toBe(true);
      http.verify();
      vi.useRealTimers();
    }
  });

  it('sin un rango consultable no consulta ni muestra las cifras del periodo anterior', async () => {
    const { availability, dashboard } = create(['ANALISTA']);
    TestBed.tick();
    http.expectOne((candidate) => candidate.url === SUMMARY_URL).flush({});
    http.expectOne((candidate) => candidate.url === AVAILABILITY_URL).flush(AVAILABILITY);
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();
    expect(availability.data()?.aggregate.percent).toBe(99.5);

    dashboard.choosePreset('CUSTOM');
    TestBed.tick();

    http.expectNone((candidate) => candidate.url === AVAILABILITY_URL);
    expect(availability.data()).toBeUndefined();
  });

  it('con un error muestra el mensaje del servidor', async () => {
    const { availability } = create(['ANALISTA']);
    TestBed.tick();
    http.expectOne((candidate) => candidate.url === SUMMARY_URL).flush({});

    http
      .expectOne((candidate) => candidate.url === AVAILABILITY_URL)
      .flush(
        {
          code: 'AVAILABILITY_RESTRICTED',
          detail: 'La disponibilidad estimada no está disponible para tus roles',
        },
        { status: 403, statusText: 'Forbidden' },
      );
    await vi.advanceTimersByTimeAsync(0);
    TestBed.tick();

    expect(availability.error()).toBe(
      'La disponibilidad estimada no está disponible para tus roles',
    );
  });
});
