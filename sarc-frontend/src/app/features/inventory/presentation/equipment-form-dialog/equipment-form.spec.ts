import { Equipment } from '../../domain/equipment';
import {
  EquipmentFormValue,
  initialFormValue,
  toEquipmentDraft,
  toUpdateInput,
} from './equipment-form';

const BASE: EquipmentFormValue = {
  network: 'KIA',
  type: 'CHARGING_POINT',
  brand: 'WALLBOX',
  name: '  Punto Quito  ',
  serial: ' sm-1 ',
  assignedCode: '123456',
  uidPuk: ' uid-1 ',
  location: '',
  mapsUrl: '',
  ocppUrl: 'wss://ocpp.example/x',
  usageStatus: 'FREE',
  pointConnector: 'type2',
  stationConnectors: ['ccs2', 'chademo'],
};

describe('equipment form', () => {
  it('sends the KIA point code, one connector and the usage status', () => {
    expect(toEquipmentDraft(BASE)).toEqual({
      network: 'KIA',
      type: 'CHARGING_POINT',
      brand: 'WALLBOX',
      name: 'Punto Quito',
      serial: 'sm-1',
      assignedCode: '123456',
      uidPuk: 'uid-1',
      location: undefined,
      mapsUrl: undefined,
      ocppUrl: 'wss://ocpp.example/x',
      usageStatus: 'FREE',
      connectorTypeIds: ['type2'],
    });
  });

  it('omits code and usage status on stations and sends their connectors in order', () => {
    const draft = toEquipmentDraft({ ...BASE, network: 'JAC', type: 'CHARGING_STATION' });

    expect(draft.assignedCode).toBeUndefined();
    expect(draft.usageStatus).toBeUndefined();
    expect(draft.connectorTypeIds).toEqual(['ccs2', 'chademo']);
  });

  it('omits the code on JAC points, where it is the serial', () => {
    expect(toEquipmentDraft({ ...BASE, network: 'JAC' }).assignedCode).toBeUndefined();
  });

  it('does not send network and type when editing', () => {
    const update = toUpdateInput(toEquipmentDraft(BASE));

    expect(update).not.toHaveProperty('network');
    expect(update).not.toHaveProperty('type');
    expect(update.serial).toBe('sm-1');
  });

  it('starts a replacement with the old classification, name and location only', () => {
    const old = {
      network: 'JAC',
      type: 'CHARGING_STATION',
      brand: 'BENNY',
      name: 'Electrolinera Norte',
      serial: 'ST-1',
      location: 'Quito',
      mapsUrl: 'https://maps.app.goo.gl/x',
      connectors: [{ connectorTypeId: 'ccs2' }],
    } as Equipment;

    const value = initialFormValue(undefined, old);

    expect(value).toMatchObject({
      network: 'JAC',
      type: 'CHARGING_STATION',
      name: 'Electrolinera Norte',
      location: 'Quito',
      brand: '',
      serial: '',
      stationConnectors: [],
    });
  });
});
