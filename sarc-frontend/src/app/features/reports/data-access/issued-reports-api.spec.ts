import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { IssuedReportsApi } from './issued-reports-api';
describe('IssuedReportsApi', () => {
  let api: IssuedReportsApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(IssuedReportsApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('requests authenticated binary rather than reusable provider URL', async () => {
    const result = firstValueFrom(api.download('id-1'));
    const request = http.expectOne('/api/v1/reports/issued/id-1/download');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['QA']));
    expect((await result).body).toBeInstanceOf(Blob);
  });
  it('issues through own data access using existing backend contract', async () => {
    const result = firstValueFrom(
      api.generateConsumptionReport(
        { from: '2026-10-01', to: '2026-11-01', network: 'JAC', timeZone: 'America/Guayaquil' },
        'PDF',
      ),
    );
    const request = http.expectOne((r) => r.url === '/api/v1/energy/report');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('to')).toBe('2026-11-01');
    expect(request.request.params.get('network')).toBe('JAC');
    expect(request.request.params.get('format')).toBe('PDF');
    expect(request.request.params.has('audience')).toBe(false);
    expect(request.request.params.has('timeZone')).toBe(false);
    request.flush(new Blob(['QA']));
    await result;
  });
});
