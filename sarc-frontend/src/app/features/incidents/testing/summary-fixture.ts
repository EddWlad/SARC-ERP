import { IncidentSummaryResponse } from '../../../core/api/generated';

/** Una respuesta de `GET /summary` con cifras de ejemplo, para las pruebas del tablero. */
export function summaryResponse(
  overrides: Partial<IncidentSummaryResponse> = {},
): IncidentSummaryResponse {
  return {
    cutOff: '2026-11-22T20:30:00Z',
    from: '2026-11-01',
    to: '2026-11-22',
    openTicketsAtCutOff: 1,
    totals: {
      uniqueTickets: 5,
      episodes: 5,
      openEpisodes: 1,
      closedEpisodes: 3,
      reopened: 0,
      critical: 0,
      excluded: 1,
    },
    measures: [
      { pair: 'T1-T0', averageMinutes: 4.3, n: 3, notApplicable: 0, noData: 1, open: 0 },
      { pair: 'T2-T0', averageMinutes: 15, n: 2, notApplicable: 1, noData: 0, open: 1 },
    ],
    validation: { n: 3, within: 2, percent: 66.7 },
    references: [
      { measure: 'RECOVERY', type: 'DISCONNECTION', within: 1, outside: 1, notEvaluable: 0 },
    ],
    withoutReference: ['CHATBOT_SERVICE_FAILURE'],
    deviations: [
      {
        code: 'TICKET-9001',
        episode: 1,
        type: 'DISCONNECTION',
        measure: 'RECOVERY',
        justification: 'Falla del proveedor',
      },
    ],
    byDay: [{ key: '2026-11-05', count: 5 }],
    byNetwork: [{ key: 'KIA', count: 4 }],
    byType: [{ key: 'DISCONNECTION', count: 4 }],
    byPriority: [{ key: 'HIGH', count: 4 }],
    byClass: [{ key: 'EQUIPMENT', count: 4 }],
    byEquipment: [{ key: 'Equipo Norte', count: 4 }],
    byOrigin: [{ key: 'PLATFORM', count: 4 }],
    visits: { requested: 2, confirmed: 1, cancelledOrPostponed: 1, executed: 1 },
    repeaters: [{ key: 'Equipo Norte', count: 4 }],
    referenceVersions: 'V1',
    ...overrides,
  };
}
