import { TestBed } from '@angular/core/testing';

import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { RankedEquipment } from '../../domain/consumption';
import { energyShareEmpty, energyShareItem, rankedRow } from './consumption-shares';

describe('consumption-shares', () => {
  const quantities = () => TestBed.inject(QuantityFormat);

  it('does not invent values for unknown energy or categories without sessions', () => {
    expect(energyShareItem('JAC', { energyKwh: 10, sessions: 2 }, quantities())).toEqual({
      label: 'JAC',
      value: 10,
      valueText: expect.stringContaining('10'),
    });
    expect(energyShareItem('KIA', { energyKwh: null, sessions: 1 }, quantities())).toMatchObject({
      value: null,
      valueText: 'Sin datos',
    });
    expect(energyShareItem('CONECTZ', { energyKwh: 0, sessions: 0 }, quantities())).toMatchObject({
      value: 0,
      valueText: 'Sin sesiones',
    });
  });

  it('tells no sessions, no readings and a known zero apart when there is no total', () => {
    expect(energyShareEmpty([{ energyKwh: 0, sessions: 0 }], quantities())).toContain(
      'Sin sesiones oficiales',
    );
    expect(energyShareEmpty([{ energyKwh: null, sessions: 2 }], quantities())).toContain(
      'Sin lecturas de energía',
    );
    expect(energyShareEmpty([{ energyKwh: 0, sessions: 2 }], quantities())).toContain(
      'Energía conocida: 0',
    );
  });

  it('builds a ranking row with the equipment name, code, energy and sessions', () => {
    const item = {
      position: 2,
      sessions: 3,
      energyKwh: 12.5,
      equipment: {
        id: 'e1',
        name: 'Equipo 1',
        assignedCode: 'c1',
        network: 'JAC',
        type: 'CHARGING_STATION',
      },
    } satisfies RankedEquipment;

    expect(rankedRow(item, quantities())).toMatchObject({
      position: 2,
      name: 'Equipo 1',
      code: 'c1',
      value: 12.5,
      detail: '3 sesiones',
    });
    expect(
      rankedRow({ ...item, equipment: undefined } as unknown as RankedEquipment, quantities()).name,
    ).toBe('Sin nombre');
  });
});
