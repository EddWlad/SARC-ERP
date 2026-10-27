import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { EquipmentImportsApi } from './equipment-imports-api';

describe('EquipmentImportsApi', () => {
  let api: EquipmentImportsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(EquipmentImportsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('filters the rows of a batch only by a chosen classification', () => {
    expect(api.rowsRequest('b-1', '', 0, 50).params).toEqual({ page: 0, size: 50 });
    expect(api.rowsRequest('b-1', 'CONFLICT', 2, 25)).toEqual({
      url: '/api/v1/equipment/imports/b-1/rows',
      params: { classification: 'CONFLICT', page: 2, size: 25 },
    });
  });

  it('uploads the template as multipart without a source', () => {
    const file = new File(['x'], 'plantilla.xlsx');
    void firstValueFrom(api.upload(file));

    const request = http.expectOne('/api/v1/equipment/imports');
    const body = request.request.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.has('source')).toBe(false);
    request.flush({});
  });

  it('approves rows with their ids', async () => {
    const response = firstValueFrom(api.decide('b-1', ['r-1'], { approved: true }));

    const request = http.expectOne('/api/v1/equipment/imports/b-1/rows/decision');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ rowIds: ['r-1'], approved: true });
    request.flush([]);
    await expect(response).resolves.toEqual([]);
  });

  it('downloads the template as a file with its name', async () => {
    const response = firstValueFrom(api.downloadTemplate());

    const request = http.expectOne('/api/v1/equipment/imports/template');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['x']), {
      headers: { 'Content-Disposition': 'attachment; filename="plantilla-carga-equipos.xlsx"' },
    });
    expect((await response).headers.get('Content-Disposition')).toContain('plantilla');
  });
});
