import {
  addDays,
  currentFortnight,
  daysBetween,
  durationText,
  formatInstant,
  localParts,
  minutesBetween,
  minutesLabel,
  operatingInstant,
  periodLabel,
  periodRange,
  sourceCellText,
  splitByDay,
  todayInOperatingZone,
} from './intermittency-time';

describe('intermittency-time', () => {
  describe('duraciones', () => {
    it('muestra horas acumuladas y minutos', () => {
      expect(durationText(2202)).toBe('36:42');
      expect(durationText(109)).toBe('1:49');
      expect(durationText(5)).toBe('0:05');
      expect(durationText(0)).toBe('0:00');
    });

    it('muestra los minutos con separador de miles', () => {
      expect(minutesLabel(2202)).toBe('2.202 min');
      expect(minutesLabel(83)).toBe('83 min');
      expect(minutesLabel(1331)).toBe('1.331 min');
    });
  });

  describe('instantes y minutos entre fechas', () => {
    it('arma el instante con el desfase de la zona operativa', () => {
      expect(operatingInstant('2026-10-15', '23:50')).toBe('2026-10-15T23:50:00-05:00');
      expect(operatingInstant('2026-10-15', '00:20', 1)).toBe('2026-10-16T00:20:00-05:00');
      expect(operatingInstant('2026-10-31', '00:20', 1)).toBe('2026-11-01T00:20:00-05:00');
    });

    it('calcula los minutos de una ventana que cruza la medianoche', () => {
      expect(minutesBetween('2026-10-15', '23:50', '2026-10-16', '00:20')).toBe(30);
      expect(minutesBetween('2026-10-12', '20:03', '2026-10-13', '12:22')).toBe(979);
      expect(minutesBetween('2026-10-15', '10:00', '2026-10-15', '09:00')).toBe(-60);
    });

    it('reparte una ventana entre los dias que toca', () => {
      expect(splitByDay('2026-10-15', '23:50', '2026-10-16', '00:20')).toEqual([
        { day: '2026-10-15', minutes: 10 },
        { day: '2026-10-16', minutes: 20 },
      ]);
      expect(splitByDay('2026-10-12', '20:03', '2026-10-13', '12:22')).toEqual([
        { day: '2026-10-12', minutes: 237 },
        { day: '2026-10-13', minutes: 742 },
      ]);
      expect(
        splitByDay('2026-10-10', '12:00', '2026-10-13', '06:00').map((slice) => slice.minutes),
      ).toEqual([720, 1440, 1440, 360]);
      expect(splitByDay('2026-10-15', '10:00', '2026-10-15', '09:00')).toEqual([]);
    });

    it('no calcula con datos incompletos o mal escritos', () => {
      expect(minutesBetween('', '10:00', '2026-10-15', '11:00')).toBeNull();
      expect(minutesBetween('2026-10-15', '25', '2026-10-15', '11:00')).toBeNull();
    });
  });

  describe('periodos', () => {
    const today = '2026-10-21';

    it('la primera quincena es [1, 16) y la segunda [16, dia 1 del mes siguiente)', () => {
      expect(periodRange({ preset: 'FIRST_HALF', month: '2026-10', today })).toEqual({
        from: '2026-10-01',
        to: '2026-10-16',
      });
      expect(periodRange({ preset: 'SECOND_HALF', month: '2026-10', today })).toEqual({
        from: '2026-10-16',
        to: '2026-11-01',
      });
    });

    it('la segunda quincena de febrero y de diciembre cierran en el mes y el anio siguientes', () => {
      expect(periodRange({ preset: 'SECOND_HALF', month: '2026-02', today })).toEqual({
        from: '2026-02-16',
        to: '2026-03-01',
      });
      expect(periodRange({ preset: 'SECOND_HALF', month: '2026-12', today })).toEqual({
        from: '2026-12-16',
        to: '2027-01-01',
      });
    });

    it('los ultimos 15 dias van de hoy menos 14 a hoy mas 1', () => {
      expect(periodRange({ preset: 'LAST_15_DAYS', today })).toEqual({
        from: '2026-10-07',
        to: '2026-10-22',
      });
      expect(
        daysBetween(
          periodRange({ preset: 'LAST_15_DAYS', today })!.from,
          periodRange({ preset: 'LAST_15_DAYS', today })!.to,
        ),
      ).toBe(15);
    });

    it('el rango personalizado incluye el ultimo dia elegido y admite hasta 366 dias', () => {
      expect(
        periodRange({ preset: 'CUSTOM', from: '2026-10-01', lastDay: '2026-10-15', today }),
      ).toEqual({ from: '2026-10-01', to: '2026-10-16' });
      expect(
        periodRange({ preset: 'CUSTOM', from: '2026-01-01', lastDay: '2027-01-01', today }),
      ).toEqual({ from: '2026-01-01', to: '2027-01-02' });
      expect(
        periodRange({ preset: 'CUSTOM', from: '2026-01-01', lastDay: '2027-01-02', today }),
      ).toBeNull();
    });

    it('rechaza un rango personalizado incompleto o invertido', () => {
      expect(periodRange({ preset: 'CUSTOM', from: '2026-10-01', today })).toBeNull();
      expect(
        periodRange({ preset: 'CUSTOM', from: '2026-10-10', lastDay: '2026-10-01', today }),
      ).toBeNull();
      expect(periodRange({ preset: 'FIRST_HALF', today })).toBeNull();
    });

    it('rotula el periodo con el tipo y el fin exclusivo', () => {
      expect(periodLabel('FIRST_HALF', { from: '2026-10-01', to: '2026-10-16' })).toBe(
        'Primera quincena · 01/10/2026–16/10/2026 (fin exclusivo)',
      );
    });

    it('por defecto es la quincena en curso', () => {
      expect(currentFortnight('2026-10-15')).toEqual({ preset: 'FIRST_HALF', month: '2026-10' });
      expect(currentFortnight('2026-10-16')).toEqual({ preset: 'SECOND_HALF', month: '2026-10' });
    });
  });

  describe('fechas', () => {
    it('suma dias cruzando mes, anio y anio bisiesto', () => {
      expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
      expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
      expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
      expect(addDays('2026-10-21', -14)).toBe('2026-10-07');
    });

    it('muestra un instante en la zona operativa', () => {
      expect(localParts('2026-10-16T04:50:00Z')).toEqual({ date: '2026-10-15', time: '23:50' });
      expect(formatInstant('2026-10-16T05:00:00Z')).toBe('2026-10-16 00:00');
    });

    it('hoy se calcula en la zona operativa y no en UTC', () => {
      // 2026-10-16T03:00Z son las 22:00 del dia 15 en Guayaquil.
      expect(todayInOperatingZone(new Date('2026-10-16T03:00:00Z'))).toBe('2026-10-15');
      expect(todayInOperatingZone(new Date('2026-10-16T05:00:00Z'))).toBe('2026-10-16');
    });
  });

  describe('sourceCellText', () => {
    it('shows only the time of an Excel time-only cell', () => {
      expect(sourceCellText('1899-12-31 09:05:00')).toBe('09:05:00');
      expect(sourceCellText('1899-12-30 16:28:00')).toBe('16:28:00');
    });

    it('keeps any other value and marks a missing one', () => {
      expect(sourceCellText('2026-11-13 09:05:00')).toBe('2026-11-13 09:05:00');
      expect(sourceCellText('19:41')).toBe('19:41');
      expect(sourceCellText(null)).toBe('—');
      expect(sourceCellText('')).toBe('—');
    });
  });
});
