import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { NO_FILTERS } from '../domain/equipment';
import { EquipmentApi } from './equipment-api';
import { InventoryApi } from './inventory-api';

describe('inventory data access', () => {
  let equipmentApi: EquipmentApi;
  let inventoryApi: InventoryApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    equipmentApi = TestBed.inject(EquipmentApi);
    inventoryApi = TestBed.inject(InventoryApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists with pagination only when no filter is set', () => {
    expect(equipmentApi.listRequest({ ...NO_FILTERS, text: '  ', page: 0, size: 20 })).toEqual({
      url: '/api/v1/equipment',
      params: { page: 0, size: 20 },
    });
  });

  it('sends the trimmed text and every chosen filter', () => {
    const filters = {
      text: ' ccs ',
      network: 'KIA',
      type: 'CHARGING_POINT',
      brand: 'WALLBOX',
      status: 'DELETED',
      usageStatus: 'FREE',
    } as const;

    expect(equipmentApi.listRequest({ ...filters, page: 1, size: 50 }).params).toEqual({
      text: 'ccs',
      network: 'KIA',
      type: 'CHARGING_POINT',
      brand: 'WALLBOX',
      status: 'DELETED',
      usageStatus: 'FREE',
      page: 1,
      size: 50,
    });
    expect(inventoryApi.summaryRequest(filters).params).toEqual({
      text: 'ccs',
      network: 'KIA',
      type: 'CHARGING_POINT',
      brand: 'WALLBOX',
      status: 'DELETED',
      usageStatus: 'FREE',
    });
  });

  it('uploads the image as multipart with the version', async () => {
    const file = new File(['x'], 'foto.png', { type: 'image/png' });
    const response = firstValueFrom(equipmentApi.attachImage('eq-1', 3, file));

    const request = http.expectOne('/api/v1/equipment/eq-1/image');
    expect(request.request.method).toBe('PUT');
    const body = request.request.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('version')).toBe('3');
    request.flush({ id: 'eq-1' });
    await response;
  });

  it('downloads reports as files with the current filters', async () => {
    const response = firstValueFrom(
      inventoryApi.downloadReport('XLSX', { ...NO_FILTERS, network: 'JAC' }),
    );

    const request = http.expectOne(
      (req) => req.url === '/api/v1/inventory/report' && req.params.get('format') === 'XLSX',
    );
    expect(request.request.params.get('network')).toBe('JAC');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['xlsx']));
    expect((await response).body).toBeInstanceOf(Blob);
  });
});
