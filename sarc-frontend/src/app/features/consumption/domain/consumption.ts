import {
  ConsumptionSummaryResponse,
  PageResultSessionResponse,
  SessionResponse,
} from '../../../core/api/generated';
import {
  ChargingNetwork,
  InfrastructureType,
} from '../../../shared/vocabulary/charging-infrastructure';

/** Contrato del backend (generado desde docs/api/openapi.json). */
export type ConsumptionSummary = ConsumptionSummaryResponse;
export type EnergyTotals = ConsumptionSummary['official'];
export type DailyEnergy = ConsumptionSummary['daily'][number];
export type RankedEquipment = ConsumptionSummary['top'][number];
export type Session = SessionResponse;
export type SessionPage = PageResultSessionResponse;
export type SessionSource = Session['source'];
export type SessionState = Session['state'];

export const SOURCE_LABELS: Readonly<Record<SessionSource, string>> = {
  ENERLINK: 'Enerlink',
  WALLBOX: 'Wallbox',
};

export const SESSION_STATE_LABELS: Readonly<Record<SessionState, string>> = {
  VALIDATED: 'Validada',
  PENDING_LINK: 'Pendiente de vincular',
  TEST: 'Prueba',
};

/** Que significa cada estado para los indicadores (PRD 3.3). */
export const SESSION_STATE_HINTS: Readonly<Record<SessionState, string>> = {
  VALIDATED: 'Suma en los indicadores oficiales',
  PENDING_LINK: 'Equipo sin identificar; no suma hasta vincularse',
  TEST: 'Transacción de prueba; se conserva pero no suma',
};

export const SOURCES = Object.keys(SOURCE_LABELS) as readonly SessionSource[];
export const SESSION_STATES = Object.keys(SESSION_STATE_LABELS) as readonly SessionState[];

/**
 * Filtros comunes del tablero, el historico y los reportes (estado de pantalla); '' es "todos".
 * El periodo va aparte, en {@link DateRange}.
 */
export interface ConsumptionFilters {
  readonly network: ChargingNetwork | '';
  readonly type: InfrastructureType | '';
  readonly source: SessionSource | '';
  readonly equipmentId: string;
}

export const NO_CONSUMPTION_FILTERS: ConsumptionFilters = {
  network: '',
  type: '',
  source: '',
  equipmentId: '',
};
