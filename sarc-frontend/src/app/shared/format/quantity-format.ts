import { inject, LOCALE_ID, Service } from '@angular/core';

/**
 * Cifras con el formato del idioma de la app (es-EC: "1.234,56"). Se usa Intl y no los pipes de
 * `@angular/common`, que el empaquetador lleva al bundle inicial compartido (ver README).
 */
@Service()
export class QuantityFormat {
  private readonly locale = inject(LOCALE_ID);
  private readonly integer = new Intl.NumberFormat(this.locale, { maximumFractionDigits: 0 });
  private readonly energy = new Intl.NumberFormat(this.locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  private readonly share = new Intl.NumberFormat(this.locale, {
    style: 'percent',
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  });

  count(value: number): string {
    return this.integer.format(value);
  }

  /** Cantidad con su sustantivo en singular o plural: "1 fila", "3 filas". */
  amount(value: number, singular: string, plural: string): string {
    return `${this.count(value)} ${value === 1 ? singular : plural}`;
  }

  /** Energia con dos decimales, sin unidad. */
  kwh(value: number): string {
    return this.energy.format(value);
  }

  /** Total de energia, sin unidad: sin lecturas es "Sin datos", nunca 0. */
  knownKwh(value: number | null | undefined): string {
    return value === null || value === undefined ? 'Sin datos' : this.kwh(value);
  }

  /** Total de energia con su unidad: sin lecturas es "Sin datos", nunca 0 kWh. */
  knownEnergy(value: number | null | undefined): string {
    return value === null || value === undefined ? 'Sin datos' : `${this.kwh(value)} kWh`;
  }

  /** Lectura de una sesion, sin unidad: ausente es "No informado", distinto de 0. */
  reportedKwh(value: number | null | undefined): string {
    return value === null || value === undefined ? 'No informado' : this.kwh(value);
  }

  /** Lectura de una sesion con su unidad: ausente es "No informado", distinto de 0 kWh. */
  reportedEnergy(value: number | null | undefined): string {
    return value === null || value === undefined ? 'No informado' : `${this.kwh(value)} kWh`;
  }

  /** Participacion de {@code part} en {@code total}; 0 % si no hay total. */
  percent(part: number, total: number): string {
    return this.share.format(total > 0 ? part / total : 0);
  }
}
