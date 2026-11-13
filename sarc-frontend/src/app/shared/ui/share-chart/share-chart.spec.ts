import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ChartView } from '../chart/chart-view';
import { ShareChart, ShareItem } from './share-chart';

describe('ShareChart', () => {
  function render(
    items: readonly ShareItem[],
    emptyMessage = 'Sin datos en este periodo.',
  ): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.overrideComponent(ShareChart, { remove: { imports: [ChartView] } });
    TestBed.overrideComponent(ShareChart, { add: { schemas: [CUSTOM_ELEMENTS_SCHEMA] } });
    const fixture = TestBed.createComponent(ShareChart);
    fixture.componentRef.setInput('items', items);
    fixture.componentRef.setInput('title', 'Energía por red');
    fixture.componentRef.setInput('seriesLabel', 'Energía (kWh)');
    fixture.componentRef.setInput('chartFormat', (value: number) => String(value));
    fixture.componentRef.setInput('emptyMessage', emptyMessage);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('does not invent percentages for unknown values', () => {
    const text = render([
      { label: 'JAC', value: 10, valueText: '10 kWh' },
      { label: 'KIA', value: null, valueText: 'Sin datos' },
      { label: 'CONECTZ', value: 0, valueText: 'Sin sesiones' },
    ]).textContent;

    expect(text).toContain('Sin datos');
    expect(text).toContain('Sin sesiones');
    expect(text).toMatch(/100[.,]0\s*%/);
  });

  it('shows the legend value text the caller wrote, with its unit', () => {
    const text = render([
      { label: 'Menos de 5 min', value: 30, valueText: '0:30 h' },
      { label: '4 h o más', value: 270, valueText: '4:30 h' },
    ]).textContent;

    expect(text).toContain('0:30 h');
    expect(text).toContain('4:30 h');
    expect(text).toMatch(/10[.,]0\s*%/);
    expect(text).toMatch(/90[.,]0\s*%/);
  });

  it('says what the caller says when there is no total, and draws no chart', () => {
    const host = render(
      [{ label: 'JAC', value: 0, valueText: 'Sin sesiones' }],
      'Sin sesiones oficiales.',
    );

    expect(host.textContent).toContain('Sin sesiones oficiales.');
    expect(host.querySelector('sarc-chart-view')).toBeNull();
  });
});
