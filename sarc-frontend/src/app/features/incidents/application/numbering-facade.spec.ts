import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { NumberingResponse } from '../../../core/api/generated';
import { NumberingFacade, parseStartNumber } from './numbering-facade';

const URL = '/api/v1/incidents/numbering';

function numbering(overrides: Partial<NumberingResponse> = {}): NumberingResponse {
  return {
    configured: false,
    ticketsExist: false,
    expectedEnvironment: 'TEST',
    ...overrides,
  };
}

describe('NumberingFacade', () => {
  let http: HttpTestingController;
  let facade: NumberingFacade;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), NumberingFacade],
    });
    http = TestBed.inject(HttpTestingController);
    facade = TestBed.inject(NumberingFacade);
  });

  afterEach(() => http.verify());

  it('lee el estado de la numeracion sin configurar', async () => {
    const loading = facade.load();
    http.expectOne(URL).flush(numbering());
    await loading;

    expect(facade.state()?.configured).toBe(false);
    expect(facade.expectedEnvironment()).toBe('TEST');
    expect(facade.serverWithoutEnvironment()).toBe(false);
    expect(facade.error()).toBe('');
    expect(facade.loading()).toBe(false);
  });

  it('avisa cuando el servidor no tiene su ambiente configurado', async () => {
    const loading = facade.load();
    http.expectOne(URL).flush(numbering({ expectedEnvironment: undefined }));
    await loading;

    expect(facade.serverWithoutEnvironment()).toBe(true);
    expect(facade.expectedEnvironment()).toBeNull();
  });

  it('configura con el ambiente del servidor y la confirmacion sin espacios', async () => {
    http.expectNone(URL);
    const loading = facade.load();
    http.expectOne(URL).flush(numbering());
    await loading;

    const saving = facade.configure(9001, ' TICKET-9001 ');
    const request = http.expectOne(
      (candidate) => candidate.method === 'PUT' && candidate.url === URL,
    );
    expect(request.request.body).toEqual({
      startNumber: 9001,
      environment: 'TEST',
      confirmation: 'TICKET-9001',
    });
    request.flush(
      numbering({
        configured: true,
        startNumber: 9001,
        nextCode: 'TICKET-9001',
        environment: 'TEST',
      }),
    );

    expect(await saving).toBe(true);
    expect(facade.state()?.configured).toBe(true);
    expect(facade.state()?.nextCode).toBe('TICKET-9001');
    expect(facade.saving()).toBe(false);
  });

  it('muestra el mensaje del servidor si la confirmacion no coincide', async () => {
    const loading = facade.load();
    http.expectOne(URL).flush(numbering());
    await loading;

    const saving = facade.configure(9001, 'TICKET-9002');
    http
      .expectOne((candidate) => candidate.method === 'PUT')
      .flush(
        {
          code: 'TICKET_NUMBERING_CONFIRMATION_MISMATCH',
          detail: 'La confirmacion debe repetir TICKET-9001',
        },
        { status: 422, statusText: 'Unprocessable Content' },
      );

    expect(await saving).toBe(false);
    expect(facade.error()).toBe('La confirmacion debe repetir TICKET-9001');
    expect(facade.state()?.configured).toBe(false);
    expect(facade.saving()).toBe(false);
  });

  it('sin ambiente del servidor no envia nada', async () => {
    const loading = facade.load();
    http.expectOne(URL).flush(numbering({ expectedEnvironment: undefined }));
    await loading;

    expect(await facade.configure(9001, 'TICKET-9001')).toBe(false);
    http.expectNone((candidate) => candidate.method === 'PUT');
    expect(facade.error()).toContain('ambiente');
  });

  it('arma el codigo que se debe confirmar', () => {
    expect(facade.codeFor(86)).toBe('TICKET-086');
    expect(facade.codeFor(1000)).toBe('TICKET-1000');
  });

  it('el numero inicial es un entero de 1 a 999.999.999', () => {
    expect(parseStartNumber('9001')).toBe(9001);
    expect(parseStartNumber(' 86 ')).toBe(86);
    expect(parseStartNumber('999999999')).toBe(999_999_999);
    for (const invalid of ['', '0', '-5', '12.5', 'abc', '1000000000', '9 001']) {
      expect(parseStartNumber(invalid)).toBeNull();
    }
  });
});
