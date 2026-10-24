import {
  afterRenderEffect,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  viewChild,
} from '@angular/core';
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  ChartConfiguration,
  DoughnutController,
  Filler,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Scale,
  Tooltip,
} from 'chart.js';

import { ChartColors, ChartTheme, SeriesColor } from './chart-theme';

// Solo los controladores que usa SARC: Chart.js es modular y el resto no entra al bundle.
Chart.register(
  LineController,
  BarController,
  DoughnutController,
  LineElement,
  BarElement,
  ArcElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Filler,
  Tooltip,
);

export type ChartKind = 'line' | 'bar' | 'doughnut';

/**
 * Una sola serie por grafica: dos medidas de distinta escala van en graficas separadas, nunca en
 * doble eje. En un pastel, {@code colors} trae un color por porcion, en el orden de las etiquetas.
 */
export interface ChartSeries {
  readonly label: string;
  readonly values: readonly number[];
  readonly colors: SeriesColor | readonly SeriesColor[];
}

/**
 * Grafica Chart.js con los colores del tema (claro/oscuro) y tooltips. La leyenda y las cifras
 * visibles las pone quien la usa, en HTML, para que la identidad nunca dependa solo del color.
 */
@Component({
  selector: 'sarc-chart-view',
  template: `
    <div class="frame">
      <canvas #canvas role="img" [attr.aria-label]="description()"></canvas>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .frame {
      position: relative;
      height: var(--sarc-chart-height, 260px);
    }
  `,
})
export class ChartView {
  readonly kind = input.required<ChartKind>();
  readonly labels = input.required<readonly string[]>();
  readonly series = input.required<ChartSeries>();
  /** Barras horizontales (rankings con nombres largos). */
  readonly horizontal = input(false);
  /** Resumen para lectores de pantalla: que muestra la grafica. */
  readonly description = input.required<string>();
  /** Formato de los valores en ejes y tooltips. */
  readonly format = input<(value: number) => string>((value) => String(value));

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly theme = inject(ChartTheme);
  private chart?: Chart;
  private shape?: string;

  constructor() {
    afterRenderEffect({
      earlyRead: () => {
        this.theme.revision();
        return this.theme.resolve();
      },
      write: (colors) => this.render(colors()),
    });
    inject(DestroyRef).onDestroy(() => this.chart?.destroy());
  }

  private render(colors: ChartColors): void {
    const config = this.configuration(colors);
    const shape = `${this.kind()}-${this.horizontal()}`;
    if (this.chart && this.shape === shape) {
      this.chart.data = config.data;
      this.chart.options = config.options ?? {};
      this.chart.update();
      return;
    }
    this.chart?.destroy();
    this.chart = new Chart(this.canvas().nativeElement, config);
    this.shape = shape;
  }

  private configuration(colors: ChartColors): ChartConfiguration {
    const series = this.series();
    const paint = (color: SeriesColor) => colors.series[color];
    const fill = Array.isArray(series.colors)
      ? (series.colors as readonly SeriesColor[]).map(paint)
      : paint(series.colors as SeriesColor);
    const format = this.format();
    const doughnut = this.kind() === 'doughnut';
    const valueAxis = this.horizontal() ? 'x' : 'y';
    const scale = {
      grid: { color: colors.grid },
      border: { display: false },
      ticks: { color: colors.mutedText },
    };
    // Ambos ejes llevan callback: uno undefined anularia el de Chart.js y mostraria indices.
    const valueTick = (value: number | string) => format(Number(value));
    return {
      type: this.kind(),
      data: {
        labels: [...this.labels()],
        datasets: [
          {
            label: series.label,
            data: [...series.values],
            backgroundColor:
              this.kind() === 'line'
                ? withAlpha(fill as string, 0.15)
                : (fill as string | string[]),
            borderColor: doughnut ? colors.surface : fill,
            // Linea fina, separacion de 2px entre porciones y barras con extremo redondeado.
            borderWidth: 2,
            borderRadius: this.kind() === 'bar' ? 4 : 0,
            maxBarThickness: 36,
            fill: this.kind() === 'line',
            // Monotona: la curva nunca inventa picos ni valles entre dos dias.
            cubicInterpolationMode: 'monotone',
            pointRadius: this.labels().length > 45 ? 0 : 3,
            pointHoverRadius: 5,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        indexAxis: this.horizontal() ? 'y' : 'x',
        animation: { duration: 250 },
        interaction: doughnut
          ? { mode: 'nearest', intersect: true }
          : { mode: 'index', intersect: false },
        font: { family: colors.fontFamily },
        cutout: doughnut ? '62%' : undefined,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (item) => ` ${series.label}: ${format(Number(item.raw))}`,
            },
          },
        },
        scales: doughnut
          ? {}
          : {
              x: {
                ...scale,
                grid: { ...scale.grid, display: valueAxis === 'x' },
                ticks: {
                  ...scale.ticks,
                  maxRotation: 0,
                  autoSkip: true,
                  autoSkipPadding: 12,
                  callback: valueAxis === 'x' ? valueTick : categoryTick,
                },
              },
              y: {
                ...scale,
                beginAtZero: true,
                grid: { ...scale.grid, display: valueAxis === 'y' },
                ticks: {
                  ...scale.ticks,
                  callback: valueAxis === 'y' ? valueTick : categoryTick,
                },
              },
            },
      },
    } as ChartConfiguration;
  }
}

/** Etiqueta de categoria recortada, para nombres largos en rankings. */
function categoryTick(this: Scale, value: number | string): string {
  const text = this.getLabelForValue(Number(value));
  return text.length > 24 ? `${text.slice(0, 23)}…` : text;
}

/** Color con transparencia para el area bajo una linea. Acepta rgb() o rgba(). */
function withAlpha(color: string, alpha: number): string {
  const channels = color.match(/[\d.]+/g);
  return channels && channels.length >= 3
    ? `rgba(${channels[0]}, ${channels[1]}, ${channels[2]}, ${alpha})`
    : color;
}
