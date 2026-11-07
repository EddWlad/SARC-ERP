import { TestBed } from '@angular/core/testing';
import { ChartConfiguration } from 'chart.js';

import { ChartColors } from './chart-theme';
import { ChartView } from './chart-view';

describe('ChartView', () => {
  const colors = {
    series: {
      'series-1': 'rgb(1, 2, 3)',
      'series-2': 'rgb(1, 2, 3)',
      'series-3': 'rgb(1, 2, 3)',
      'series-4': 'rgb(4, 5, 6)',
    },
    grid: 'rgb(0, 0, 0)',
    mutedText: 'rgb(0, 0, 0)',
    surface: 'rgb(0, 0, 0)',
    fontFamily: 'sans-serif',
  } as unknown as ChartColors;

  function configuration(values: readonly (number | null)[]): ChartConfiguration {
    const fixture = TestBed.createComponent(ChartView);
    fixture.componentRef.setInput('kind', 'line');
    fixture.componentRef.setInput('labels', values.map(String));
    fixture.componentRef.setInput('description', 'QA');
    fixture.componentRef.setInput('series', { label: 'kWh', values, colors: 'series-1' });
    return (
      fixture.componentInstance as unknown as {
        configuration(colors: ChartColors): ChartConfiguration;
      }
    ).configuration(colors);
  }

  it('keeps unknown readings as real gaps and a known zero as zero', () => {
    const dataset = configuration([5, null, 0]).data.datasets[0];
    expect(dataset.data).toEqual([5, null, 0]);
    expect((dataset as { spanGaps?: boolean }).spanGaps).toBe(false);
  });
  it('conserva el cuarto color por posición en una dona', () => {
    const fixture = TestBed.createComponent(ChartView);
    fixture.componentRef.setInput('kind', 'doughnut');
    fixture.componentRef.setInput('labels', [
      'Disponible',
      'Ocupado',
      'Con falla',
      'Fuera de línea',
    ]);
    fixture.componentRef.setInput('description', 'Estados');
    fixture.componentRef.setInput('series', {
      label: 'Minutos',
      values: [10, null, 0, 2],
      colors: ['series-1', 'series-2', 'series-3', 'series-4'],
    });
    const config = (
      fixture.componentInstance as unknown as {
        configuration(colors: ChartColors): ChartConfiguration;
      }
    ).configuration(colors);
    expect(config.data.datasets[0].backgroundColor).toEqual([
      'rgb(1, 2, 3)',
      'rgb(1, 2, 3)',
      'rgb(1, 2, 3)',
      'rgb(4, 5, 6)',
    ]);
  });
});
