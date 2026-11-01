import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ChartView } from '../../../../shared/ui/chart/chart-view';
import { ShareChart, ShareItem } from './share-chart';

describe('ShareChart', () => {
  function render(items: readonly ShareItem[]): HTMLElement {
    TestBed.overrideComponent(ShareChart, { remove: { imports: [ChartView] } });
    TestBed.overrideComponent(ShareChart, { add: { schemas: [CUSTOM_ELEMENTS_SCHEMA] } });
    const fixture = TestBed.createComponent(ShareChart);
    fixture.componentRef.setInput('items', items);
    fixture.componentRef.setInput('title', 'Energía por red');
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('does not invent percentages for unknown energy or categories without sessions', () => {
    const text = render([
      { label: 'JAC', energyKwh: 10, sessions: 2 },
      { label: 'KIA', energyKwh: null, sessions: 1 },
      { label: 'CONECTZ', energyKwh: 0, sessions: 0 },
    ]).textContent;
    expect(text).toContain('Sin datos');
    expect(text).toContain('Sin sesiones');
    expect(text).toMatch(/100[.,]0\s*%/);
  });

  it('tells no sessions, no readings and a known zero apart when there is no total', () => {
    expect(render([{ label: 'JAC', energyKwh: 0, sessions: 0 }]).textContent).toContain(
      'Sin sesiones oficiales',
    );
    TestBed.resetTestingModule();
    expect(render([{ label: 'JAC', energyKwh: null, sessions: 2 }]).textContent).toContain(
      'Sin lecturas de energía',
    );
    TestBed.resetTestingModule();
    expect(render([{ label: 'JAC', energyKwh: 0, sessions: 2 }]).textContent).toContain(
      'Energía conocida: 0',
    );
  });
});
