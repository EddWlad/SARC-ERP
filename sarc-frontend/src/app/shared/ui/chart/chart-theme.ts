import { DOCUMENT, computed, inject, Service, signal } from '@angular/core';

import { ThemeStore } from '../../../core/theme/theme-store';

/** Colores de serie disponibles (tokens `--sarc-series-*`), asignados en orden fijo por categoria. */
export type SeriesColor = 'series-1' | 'series-2' | 'series-3';

/** Colores ya resueltos (rgb) del tema activo, listos para un lienzo que no entiende variables CSS. */
export interface ChartColors {
  readonly series: Readonly<Record<SeriesColor, string>>;
  readonly text: string;
  readonly mutedText: string;
  readonly grid: string;
  readonly surface: string;
  readonly fontFamily: string;
}

const SERIES: readonly SeriesColor[] = ['series-1', 'series-2', 'series-3'];

/**
 * Traduce los tokens del tema a colores concretos para Chart.js. Los tokens usan light-dark(), que
 * solo el navegador resuelve, por eso se leen a traves de un elemento sonda. {@link revision}
 * cambia cuando cambia el tema (elegido o del sistema) y obliga a redibujar.
 */
@Service()
export class ChartTheme {
  private readonly document = inject(DOCUMENT);
  private readonly themeMode = inject(ThemeStore).mode;
  private readonly systemDark = signal(false);

  readonly revision = computed(() => `${this.themeMode()}-${this.systemDark()}`);

  constructor() {
    const query = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
    if (query) {
      this.systemDark.set(query.matches);
      query.addEventListener('change', (event) => this.systemDark.set(event.matches));
    }
  }

  /** Lee los colores del DOM; llamar en la fase de lectura de un efecto de render. */
  resolve(): ChartColors {
    const probe = this.document.createElement('span');
    probe.style.display = 'none';
    this.document.body.appendChild(probe);
    const read = (token: string): string => {
      probe.style.color = `var(${token})`;
      return getComputedStyle(probe).color;
    };
    const series = Object.fromEntries(
      SERIES.map((name) => [name, read(`--sarc-${name}`)]),
    ) as Record<SeriesColor, string>;
    const colors: ChartColors = {
      series,
      text: read('--mat-sys-on-surface'),
      mutedText: read('--mat-sys-on-surface-variant'),
      grid: read('--mat-sys-outline-variant'),
      surface: read('--mat-sys-surface'),
      fontFamily: getComputedStyle(this.document.body).fontFamily,
    };
    probe.remove();
    return colors;
  }
}
