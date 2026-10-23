import { ExternalIdentityRequest, ExternalIdentityResponse } from '../../../core/api/generated';

/** Identidad del equipo en una plataforma externa, usada para conciliar importaciones (RF-002.10). */
export type ExternalIdentity = ExternalIdentityResponse;
export type ExternalIdentityInput = ExternalIdentityRequest;
export type ExternalPlatform = ExternalIdentity['platform'];

export const PLATFORM_LABELS: Readonly<Record<ExternalPlatform, string>> = {
  WALLBOX: 'Wallbox',
  ENERLINK: 'Enerlink',
  OCPP: 'OCPP',
};

export const PLATFORMS = Object.keys(PLATFORM_LABELS) as readonly ExternalPlatform[];
