import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NumberingResponse } from '../../../../core/api/generated';
import { NumberingPage } from './numbering-page';

const URL = '/api/v1/incidents/numbering';

function numbering(overrides: Partial<NumberingResponse> = {}): NumberingResponse {
  return { configured: false, ticketsExist: false, expectedEnvironment: 'TEST', ...overrides };
}

function type(element: HTMLElement, label: string, value: string): void {
  const field = [...element.querySelectorAll('mat-form-field')].find((candidate) =>
    candidate.querySelector('mat-label')?.textContent?.includes(label),
  );
  const input = field?.querySelector('input') as HTMLInputElement;
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('blur', { bubbles: true }));
}

describe('NumberingPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<NumberingPage>;

  async function open(): Promise<void> {
    fixture = TestBed.createComponent(NumberingPage);
    fixture.detectChanges();
    http.expectOne(URL).flush(numbering());
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [NumberingPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('configura con el numero y el codigo escritos', async () => {
    await open();
    const element = fixture.nativeElement as HTMLElement;

    type(element, 'Número inicial', '100');
    fixture.detectChanges();
    type(element, 'Confirmar el código', 'TICKET-100');
    fixture.detectChanges();
    (element.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const request = http.expectOne(
      (candidate) => candidate.method === 'PUT' && candidate.url === URL,
    );
    expect(request.request.body).toEqual({
      startNumber: 100,
      environment: 'TEST',
      confirmation: 'TICKET-100',
    });
    request.flush(
      numbering({
        configured: true,
        startNumber: 100,
        nextCode: 'TICKET-100',
        environment: 'TEST',
      }),
    );
  });

  it('acepta el codigo en minusculas y con espacios', async () => {
    await open();
    const element = fixture.nativeElement as HTMLElement;

    type(element, 'Número inicial', '100');
    fixture.detectChanges();
    type(element, 'Confirmar el código', ' ticket-100 ');
    fixture.detectChanges();
    (element.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http
      .expectOne((candidate) => candidate.method === 'PUT' && candidate.url === URL)
      .flush(numbering({ configured: true, startNumber: 100, environment: 'TEST' }));
  });

  it('no envia si el codigo no coincide y dice cual debe escribir', async () => {
    await open();
    const element = fixture.nativeElement as HTMLElement;

    type(element, 'Número inicial', '100');
    fixture.detectChanges();
    type(element, 'Confirmar el código', 'TICKET-101');
    fixture.detectChanges();
    (element.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();

    http.expectNone((candidate) => candidate.method === 'PUT');
    expect(element.textContent).toContain('TICKET-100');
  });
});
