/** datetime-local en zona operativa; conserva los segundos proporcionados. */
export function toOperatingInstant(local: string): string {
  return (local.length === 16 ? local + ':00' : local) + '-05:00';
}

/** Inicio del dia (00:00) de una fecha aaaa-mm-dd en la zona operativa. */
export function dayStartInstant(date: string): string {
  return `${date}T00:00:00-05:00`;
}

/** Fin inclusivo de una fecha aaaa-mm-dd: 00:00 del dia siguiente en la zona operativa. */
export function dayEndInstant(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  const next = new Date(Date.UTC(year, month - 1, day + 1));
  return `${next.toISOString().slice(0, 10)}T00:00:00-05:00`;
}
