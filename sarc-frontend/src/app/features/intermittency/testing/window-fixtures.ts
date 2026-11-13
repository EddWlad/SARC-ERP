import {
  WindowDetailResponse,
  WindowPageResponse,
  WindowResponse,
} from '../../../core/api/generated';

/** Datos de prueba de las ventanas guardadas: lo que el backend devolveria, con codigos ficticios. */
export function windowResponse(overrides: Partial<WindowResponse> = {}): WindowResponse {
  return {
    id: 'w1',
    version: 2,
    equipmentId: 'p1',
    pointLabel: 'Punto A (900001)',
    startAt: '2026-10-05T13:00:00Z',
    endAt: '2026-10-05T13:30:00Z',
    durationMinutes: 30,
    durationText: '0:30',
    state: 'CONFIRMED',
    reviewReasons: [],
    comment: 'Sin enlace',
    source: 'MANUAL',
    revision: 1,
    ...overrides,
  };
}

export function windowPage(
  content: WindowResponse[],
  overrides: Partial<WindowPageResponse> = {},
): WindowPageResponse {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, ...overrides };
}

export function windowDetail(overrides: Partial<WindowDetailResponse> = {}): WindowDetailResponse {
  return {
    window: windowResponse(),
    revisions: [
      {
        revision: 2,
        change: 'CORRECTED',
        startAt: '2026-10-05T13:00:00Z',
        endAt: '2026-10-05T13:30:00Z',
        durationMinutes: 30,
        state: 'CONFIRMED',
        reviewReasons: [],
        comment: 'Sin enlace',
        justification: 'Se corrigió el fin según el reporte',
        actorId: 'u1',
        at: '2026-10-06T14:00:00Z',
      },
      {
        revision: 1,
        change: 'CREATED',
        startAt: '2026-10-05T13:00:00Z',
        endAt: '2026-10-05T13:20:00Z',
        durationMinutes: 20,
        state: 'CONFIRMED',
        reviewReasons: [],
        actorId: 'u1',
        at: '2026-10-05T23:00:00Z',
      },
    ],
    ...overrides,
  };
}
