import { linkedSignal, Signal } from '@angular/core';

/** Lo mínimo que se usa de un `httpResource`: si ya tiene un valor y cuál es. */
interface ValueSource<T> {
  hasValue(): boolean;
  value(): T;
}

/**
 * Último valor recibido de un recurso mientras llega el siguiente. Un `httpResource` vacía su valor
 * al cambiar los parámetros (filtro, página): la lista quedaba vacía durante la consulta, la página
 * se encogía, parpadeaba y el desplazamiento volvía al inicio. Con esto la pantalla conserva lo que
 * ya mostraba hasta que lleguen los datos nuevos; el indicador de carga avisa de la consulta.
 * Devuelve `undefined` solo si nunca hubo un valor.
 */
export function retainedValue<T>(resource: ValueSource<T>): Signal<T | undefined> {
  return linkedSignal<T | undefined, T | undefined>({
    source: () => (resource.hasValue() ? resource.value() : undefined),
    computation: (current, previous) => current ?? previous?.value,
  });
}
