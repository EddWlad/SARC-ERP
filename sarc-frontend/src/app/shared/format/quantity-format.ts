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

  /** Energia con dos decimales, sin unidad. */
  kwh(value: number): string {
    return this.energy.format(value);
  }

  /** Participacion de {@code part} en {@code total}; 0 % si no hay total. */
  percent(part: number, total: number): string {
    return this.share.format(total > 0 ? part / total : 0);
  }
}
