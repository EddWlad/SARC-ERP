import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ReviewPage } from './review-page';
import { ReviewFacade } from '../../application/review-facade';

describe('ReviewPage temporal', () => {
  it('separa comprobación temporal de validación de valores', async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(ReviewPage);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http
      .expectOne((request) => request.url === '/api/v1/uptime/measurements')
      .flush({ content: [], totalPages: 0 });
    await fixture.whenStable();
    const facade = fixture.debugElement.injector.get(ReviewFacade);
    facade.queue.set('TEMPORAL_RECONCILIATION');
    const loading = facade.load();
    http
      .expectOne((request) => request.params.get('queue') === 'TEMPORAL_RECONCILIATION')
      .flush({
        content: [
          {
            id: 'temporal',
            version: 2,
            equipmentName: 'Equipo de prueba',
            ordinal: 1,
            observations: ['DURATION_INVALID'],
            temporalPrecision: 'REQUIRES_RECONCILIATION',
          },
        ],
        totalPages: 1,
      });
    await loading;
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Conciliación temporal');
    expect(fixture.nativeElement.textContent).toContain('Comprobar evidencia');
    expect(fixture.nativeElement.textContent).not.toContain('Validar selección');
    expect(facade.selectable()).toHaveLength(1);
    http.verify();
  });
});
