import { BreakpointObserver } from '@angular/cdk/layout';
import { inject, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

/** Ancho de telefono: las listas cambian de tabla a tarjetas. */
const HANDSET_QUERY = '(max-width: 767.98px)';

/** True mientras la pantalla tiene ancho de telefono. Llamar en un contexto de inyeccion. */
export function injectIsHandset(): Signal<boolean> {
  return toSignal(
    inject(BreakpointObserver)
      .observe(HANDSET_QUERY)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
}
