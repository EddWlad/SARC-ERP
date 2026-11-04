import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UptimeImportRowResponse } from '../../../../core/api/generated';
import { PendingLinksPage } from './pending-links-page';

const BASE = '/api/v1/uptime';
const option = (connectorId: string, ordinal: number, connectorType: string) => ({
  connectorId,
  ordinal,
  connectorType,
  equipmentId: 'e1',
  equipmentName: 'KIA CITY MALL',
});
const page = (content: unknown[]) => ({
  content,
  page: 0,
  size: 100,
  totalElements: content.length,
  totalPages: 1,
});

describe('PendingLinksPage', () => {
  let http: HttpTestingController;

  async function render(row: Partial<UptimeImportRowResponse>) {
    TestBed.configureTestingModule({
      imports: [PendingLinksPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PendingLinksPage);
    fixture.detectChanges();
    http
      .expectOne((call) => call.url === `${BASE}/pending-links`)
      .flush(page([{ id: 'r1', version: 1, equipmentId: 'e1', issues: [], ...row }]));
    http.expectOne((call) => call.url === `${BASE}/connector-aliases`).flush(page([]));
    http
      .expectOne((call) => call.url === `${BASE}/scope/connectors`)
      .flush([option('c1', 1, 'CCS2'), option('c2', 2, 'CCS1')]);
    const element = fixture.nativeElement as HTMLElement;
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(element.querySelector('mat-select')).not.toBeNull();
    });
    await fixture.whenStable();
    return element;
  }

  const linkButton = (element: HTMLElement) =>
    [...element.querySelectorAll('button')].find((button) =>
      button.textContent?.includes('Vincular'),
    ) as HTMLButtonElement;

  afterEach(() => http.verify());

  it('deja propuesto el conector del mismo tipo y la persona solo confirma', async () => {
    const element = await render({
      sourceConnector: 'Conector 2 - CCS2',
      suggestedConnectorId: 'c1',
    });

    expect(element.querySelector('mat-select')?.textContent).toContain('Conector 1');
    expect(element.textContent).toContain('Propuesto por tipo de conector');
    expect(linkButton(element).disabled).toBe(false);

    linkButton(element).click();
    const request = http.expectOne(`${BASE}/pending-links/r1/link`);
    expect(request.request.body).toEqual({ version: 1, connectorId: 'c1', rememberLabel: true });
  });

  it('sin propuesta no elige nada por la persona', async () => {
    const element = await render({ sourceConnector: 'Conector A - PUESTO 6' });

    expect(element.textContent).not.toContain('Propuesto por tipo de conector');
    expect(linkButton(element).disabled).toBe(true);
  });
});
