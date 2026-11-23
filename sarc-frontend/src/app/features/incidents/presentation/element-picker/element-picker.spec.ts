import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IncidentAssetResponse } from '../../../../core/api/generated';
import { TicketCreateFacade } from '../../application/ticket-create-facade';
import { ElementPicker } from './element-picker';

const BASE = '/api/v1/incidents';

const STATION: IncidentAssetResponse = {
  id: 'eq-1',
  name: 'KIA Norte',
  assignedCode: '910002',
  network: 'KIA',
  infrastructureType: 'CHARGING_STATION',
  location: 'Vía 1',
  mapsUrl: 'https://maps.example.test/x',
  connectors: [
    { id: 'c1', position: 1, typeLabel: 'CCS2', active: true },
    { id: 'c2', position: 2, typeLabel: 'Tipo 2', active: true },
    { id: 'c3', position: 3, typeLabel: 'CCS2', active: false },
  ],
};

describe('ElementPicker', () => {
  let fixture: ComponentFixture<ElementPicker>;
  let facade: TicketCreateFacade;
  let http: HttpTestingController;
  const root = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TicketCreateFacade],
    });
    http = TestBed.inject(HttpTestingController);
    facade = TestBed.inject(TicketCreateFacade);
    fixture = TestBed.createComponent(ElementPicker);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  async function choose(asset: IncidentAssetResponse): Promise<void> {
    const choosing = facade.selectAsset(asset);
    http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`).flush([]);
    await choosing;
    fixture.detectChanges();
  }

  it('sin equipo elegido solo muestra el campo de busqueda', () => {
    expect(root().querySelector('input[type="search"]')).not.toBeNull();
    expect(root().querySelector('.asset')).toBeNull();
  });

  it('al elegir un equipo muestra sus datos del inventario sin escribirlos', async () => {
    await choose(STATION);
    const text = root().textContent?.replace(/\s+/g, ' ') ?? '';
    expect(text).toContain('KIA');
    expect(text).toContain('Electrolinera');
    expect(text).toContain('910002');
    expect(text).toContain('Vía 1');
    const link = root().querySelector('a.maps') as HTMLAnchorElement;
    expect(link.href).toBe('https://maps.example.test/x');
    expect(link.rel).toContain('noopener');
  });

  it('deja «Equipo completo» marcado y ofrece solo los conectores activos', async () => {
    await choose(STATION);
    const boxes = Array.from(root().querySelectorAll('mat-checkbox'));
    expect(boxes).toHaveLength(3);
    expect(boxes[0].textContent).toContain('Equipo completo');
    expect(boxes[0].querySelector('input')?.checked).toBe(true);
    expect(boxes[1].textContent).toContain('Conector 1');
    expect(boxes[2].textContent).toContain('Conector 2');
    expect(root().textContent).not.toContain('Conector 3');
  });

  it('elegir un conector desmarca el equipo completo, y volver a marcarlo limpia los conectores', async () => {
    await choose(STATION);
    (root().querySelectorAll('mat-checkbox input')[2] as HTMLInputElement).click();
    fixture.detectChanges();
    expect(facade.connectorIds()).toEqual(['c2']);
    expect((root().querySelectorAll('mat-checkbox input')[0] as HTMLInputElement).checked).toBe(
      false,
    );

    (root().querySelectorAll('mat-checkbox input')[0] as HTMLInputElement).click();
    fixture.detectChanges();
    expect(facade.wholeEquipment()).toBe(true);
  });

  it('un equipo sin ubicacion ni enlace de Maps lo dice sin romper nada', async () => {
    await choose({ ...STATION, location: null, mapsUrl: null });
    expect(root().textContent).toContain('Sin ubicación en el inventario');
    expect(root().querySelector('a.maps')).toBeNull();
  });

  it('quitar el equipo vuelve al campo vacio', async () => {
    await choose(STATION);
    (root().querySelector('button[aria-label="Quitar el equipo"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    // Al quitar el equipo el foco vuelve al campo y este consulta de nuevo los equipos.
    http.match((r) => r.url === `${BASE}/assets`).forEach((r) => r.flush([]));
    expect(facade.asset()).toBeNull();
    expect(root().querySelector('.asset')).toBeNull();
  });

  describe('clase de elemento', () => {
    const labels = () =>
      Array.from(root().querySelectorAll('mat-form-field mat-label')).map(
        (l) => l.textContent?.trim() ?? '',
      );

    async function switchTo(elementClass: 'CHATBOT_SERVICE' | 'SITE_ELEMENT'): Promise<void> {
      facade.setElementClass(elementClass);
      if (elementClass === 'CHATBOT_SERVICE') {
        http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`).flush([]);
      }
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    }

    it('ofrece las tres clases y empieza en equipo', () => {
      const toggles = Array.from(root().querySelectorAll('mat-button-toggle')).map((t) =>
        t.textContent?.trim(),
      );
      expect(toggles).toEqual(['Equipo', 'Servicio de chatbot', 'Elemento del sitio']);
      expect(facade.elementClass()).toBe('EQUIPMENT');
    });

    it('el chatbot no pide equipo ni conectores y deja el alcance como opcional', async () => {
      await switchTo('CHATBOT_SERVICE');

      expect(root().querySelector('input[type="search"]')).toBeNull();
      expect(root().querySelector('.connectors')).toBeNull();
      expect(labels()).toEqual(['Alcance de la falla']);
      expect(root().textContent).toContain('No se asocia a ningún equipo, conector ni red');
      expect(root().textContent).toContain('Opcional');
    });

    it('un elemento del sitio pide clase, nombre y lugar, con el equipo de referencia sin conectores', async () => {
      await switchTo('SITE_ELEMENT');
      expect(labels()).toEqual([
        'Clase de elemento',
        'Nombre del elemento',
        'Equipo de referencia del sitio',
      ]);

      const choosing = facade.selectAsset(STATION);
      http.expectOne((r) => r.url === `${BASE}/tickets/open-matches`).flush([]);
      await choosing;
      fixture.detectChanges();

      expect(root().textContent).toContain('Vía 1');
      expect(root().querySelector('.connectors')).toBeNull();
      // La red y el tipo del equipo de referencia no se muestran: el ticket no los hereda.
      const terms = Array.from(root().querySelectorAll('dt')).map((dt) => dt.textContent?.trim());
      expect(terms).not.toContain('Red');
      expect(terms).not.toContain('Tipo');
      expect(terms).toContain('Ubicación');
      expect(root().textContent).toContain('El ticket no es del equipo');
    });

    it('el sitio escrito a mano pide nombre, ubicacion y un Maps opcional', async () => {
      await switchTo('SITE_ELEMENT');
      facade.setSiteLocationMode('MANUAL');
      fixture.detectChanges();

      expect(labels()).toEqual([
        'Clase de elemento',
        'Nombre del elemento',
        'Nombre del sitio',
        'Ubicación',
        'Enlace de Google Maps',
      ]);
      expect(root().querySelector('input[type="search"]')).toBeNull();
      const maps = Array.from(root().querySelectorAll('mat-form-field')).find((f) =>
        f.textContent?.includes('Enlace de Google Maps'),
      );
      expect(maps?.textContent).toContain('Opcional');
    });

    it('el nombre del elemento es obligatorio solo para «Otro elemento»', async () => {
      await switchTo('SITE_ELEMENT');
      const nameInput = () =>
        Array.from(root().querySelectorAll('mat-form-field'))
          .find((f) => f.textContent?.includes('Nombre del elemento'))
          ?.querySelector('input') as HTMLInputElement;

      expect(nameInput().required).toBe(false);
      facade.siteKind.set('OTHER');
      fixture.detectChanges();
      expect(nameInput().required).toBe(true);
      expect(root().textContent).toContain('Obligatorio');
    });

    it('volver a equipo muestra otra vez la busqueda vacia', async () => {
      await choose(STATION);
      await switchTo('CHATBOT_SERVICE');
      facade.setElementClass('EQUIPMENT');
      fixture.detectChanges();

      expect(root().querySelector('input[type="search"]')).not.toBeNull();
      expect(root().querySelector('.asset')).toBeNull();
    });
  });
});
