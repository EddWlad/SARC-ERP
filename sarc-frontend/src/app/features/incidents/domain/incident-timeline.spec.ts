import { describe, expect, it } from 'vitest';

import { eventSummary } from './incident-timeline';

describe('eventSummary', () => {
  it('un hito registrado dice cual y en que estado, y uno corregido tambien el valor anterior', () => {
    expect(eventSummary('MILESTONE_RECORDED', { kind: 'T1', status: 'RECORDED' })).toEqual([
      'T1: Registrado',
    ]);
    expect(
      eventSummary('MILESTONE_CORRECTED', {
        kind: 'T3',
        status: 'NO_DATA',
        previousStatus: 'RECORDED',
        previousOccurredAt: '2026-11-21T15:00:00Z',
      }),
    ).toEqual(['T3: Sin dato', 'Antes: Registrado · 2026-11-21 10:00']);
  });

  it('una visita dice la etapa, el proveedor y la etapa anterior', () => {
    expect(
      eventSummary(
        'VISIT_STAGE',
        { stage: 'POSTPONED', previousStage: 'REQUESTED' },
        'Taller Norte',
      ),
    ).toEqual(['Aplazada · Taller Norte', 'Antes: Solicitada']);
    expect(eventSummary('VISIT_STAGE', { stage: 'REQUESTED' })).toEqual(['Solicitada']);
  });

  it('prioridad, responsable y reclasificacion muestran de donde a donde', () => {
    expect(eventSummary('PRIORITY_CHANGED', { previous: 'HIGH', new: 'CRITICAL' })).toEqual([
      'Prioridad: Alta → Crítica',
    ]);
    expect(eventSummary('RESPONSIBLE_ASSIGNED', { newName: 'Segundo Analista' })).toEqual([
      'Nuevo responsable: Segundo Analista',
    ]);
    expect(
      eventSummary('RECLASSIFIED', {
        previousType: 'DISCONNECTION',
        newType: 'CONNECTOR_STUCK',
        proposedPriority: 'CRITICAL',
      }),
    ).toEqual([
      'Tipo: Desconexión → Conector retenido en el vehículo',
      'Prioridad propuesta: Crítica',
    ]);
  });

  it('el relevo muestra saliente, entrante y los cuatro datos', () => {
    expect(
      eventSummary('SHIFT_HANDOVER', {
        outgoingResponsibleName: 'Ana',
        incomingResponsibleName: 'Luis',
        currentStatus: 'En observación',
        pending: 'Confirmar estabilidad',
        nextCheckAt: '2026-11-21T20:00:00Z',
        closureCriterion: 'Dos horas estable',
      }),
    ).toEqual([
      'De Ana a Luis',
      'Estado actual: En observación',
      'Pendientes: Confirmar estabilidad',
      'Próxima verificación: 2026-11-21 15:00',
      'Criterio de cierre: Dos horas estable',
    ]);
  });

  it('una correccion muestra el valor anterior y el nuevo, con el origen en español', () => {
    expect(
      eventSummary('DATA_CORRECTED', { field: 'DESCRIPTION', previous: 'Antes', new: 'Después' }),
    ).toEqual(['Descripción: «Antes» → «Después»']);
    expect(
      eventSummary('DATA_CORRECTED', {
        field: 'REPORT_ORIGIN',
        previous: 'PLATFORM',
        new: 'CUSTOMER',
      }),
    ).toEqual(['Origen del aviso: «Plataforma de monitoreo» → «Cliente»']);
  });

  it('una evidencia muestra formato, tamaño y uso', () => {
    expect(
      eventSummary('EVIDENCE_ADDED', {
        format: 'PDF',
        sizeBytes: 2 * 1024 * 1024,
        purpose: 'FINAL',
      }),
    ).toEqual(['PDF · 2,0 MB · Final']);
  });

  it('los tipos que ya explica su texto no agregan lineas, y sin datos no inventa nada', () => {
    expect(eventSummary('OBSERVATION', {})).toEqual([]);
    expect(eventSummary('TICKET_CREATED', {})).toEqual([]);
    expect(eventSummary('MILESTONE_RECORDED', {})).toEqual(['Hito: —']);
  });

  it('una comunicacion dice el tipo, el canal y, si es otro, cual', () => {
    expect(
      eventSummary('COMMUNICATION_RECORDED', { channel: 'KIA_ELECTROLINERAS', kind: 'INITIAL' }),
    ).toEqual(['Aviso inicial · Grupo KIA electrolineras']);
    expect(
      eventSummary('COMMUNICATION_RECORDED', {
        channel: 'OTHER',
        channelDetail: 'Llamada al gerente',
        kind: 'CLOSURE',
      }),
    ).toEqual(['Cierre · Otro: Llamada al gerente']);
  });

  it('un cierre dice el resultado y la causa, y uno por duplicado nombra al original', () => {
    expect(eventSummary('TICKET_CLOSED', { result: 'RECOVERED', causeKind: 'CONFIRMED' })).toEqual([
      'Resultado: Recuperado',
      'Causa: Confirmada',
    ]);
    expect(
      eventSummary('TICKET_CLOSED', {
        result: 'DUPLICATE',
        causeKind: 'UNDETERMINED',
        duplicateOfCode: 'TICKET-9002',
      }),
    ).toEqual(['Resultado: Duplicado de otro ticket de TICKET-9002', 'Causa: No determinada']);
  });

  it('una reapertura dice el episodio nuevo', () => {
    expect(eventSummary('TICKET_REOPENED', { episode: 2 })).toEqual(['Episodio 2']);
    expect(eventSummary('TICKET_REOPENED', {})).toEqual([]);
  });
});
