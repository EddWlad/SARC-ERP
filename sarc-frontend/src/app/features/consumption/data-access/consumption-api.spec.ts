import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { NO_CONSUMPTION_FILTERS } from '../domain/consumption';
import { ConsumptionApi } from './consumption-api';
import { EnergyImportsApi } from './energy-imports-api';

describe('consumption data access', () => {
  const range = { from: '2026-10-01', to: '2026-11-01' };
  let consumptionApi: ConsumptionApi;
  let importsApi: EnergyImportsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    consumptionApi = TestBed.inject(ConsumptionApi);
    importsApi = TestBed.inject(EnergyImportsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks the summary for the period alone when no filter is set', () => {
    expect(consumptionApi.summaryRequest(range, NO_CONSUMPTION_FILTERS)).toEqual({
      url: '/api/v1/energy/summary',
      params: range,
    });
  });

  it('sends every chosen filter, the state and the trimmed text to the history', () => {
    const filters = {
      network: 'KIA',
      type: 'CHARGING_STATION',
      source: 'ENERLINK',
      equipmentId: 'eq-1',
    } as const;

    expect(
      consumptionApi.sessionsRequest({
        range,
        filters,
        state: 'PENDING_LINK',
        text: '  ses-9 ',
        page: 2,
        size: 20,
      }).params,
    ).toEqual({ ...range, ...filters, state: 'PENDING_LINK', text: 'ses-9', page: 2, size: 20 });
  });

  it('searches equipment only from two characters', () => {
    expect(consumptionApi.equipmentOptionsRequest(' k ')).toBeUndefined();
    expect(consumptionApi.equipmentOptionsRequest(' kia ')).toEqual({
      url: '/api/v1/equipment',
      params: { text: 'kia', size: 10 },
    });
  });

  it('filters the rows of a batch only by a chosen classification', () => {
    expect(importsApi.rowsRequest('b-1', '', 0, 50).params).toEqual({ page: 0, size: 50 });
    expect(importsApi.rowsRequest('b-1', 'MODIFIED', 1, 25)).toEqual({
      url: '/api/v1/energy/imports/b-1/rows',
      params: { classification: 'MODIFIED', page: 1, size: 25 },
    });
  });

  it('sends only the decision fields that change', async () => {
    const response = firstValueFrom(importsApi.decide('b-1', ['r-1', 'r-2'], { approved: true }));

    const request = http.expectOne('/api/v1/energy/imports/b-1/rows/decision');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ rowIds: ['r-1', 'r-2'], approved: true });
    request.flush([]);
    await expect(response).resolves.toEqual([]);
  });

  it('uploads the report as multipart with its source', () => {
    const file = new File(['x'], 'wallbox.xlsx');
    void firstValueFrom(importsApi.upload('WALLBOX', file));

    const request = http.expectOne('/api/v1/energy/imports');
    const body = request.request.body as FormData;
    expect(body.get('source')).toBe('WALLBOX');
    expect(body.get('file')).toBe(file);
    request.flush({});
  });
});
