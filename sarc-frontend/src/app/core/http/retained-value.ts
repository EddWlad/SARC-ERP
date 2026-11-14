import { computed, linkedSignal, Signal, untracked } from '@angular/core';

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

/** Último valor de un recurso y si corresponde a la consulta vigente. */
export interface RetainedQuery<T> {
  /** Como {@link retainedValue}: el último valor recibido, también durante una consulta nueva. */
  readonly value: Signal<T | undefined>;
  /**
   * El valor llegó para la consulta actual. Un refresco fallido de la misma consulta no lo
   * invalida: la pantalla lo sigue mostrando y avisa del error sin vaciarse (Feature 009, US4).
   * Con otra consulta (filtro, periodo, página) deja de ser vigente hasta su respuesta.
   */
  readonly current: Signal<boolean>;
}

/**
 * {@link retainedValue} que recuerda la consulta de cada valor. `key` es la petición del recurso;
 * dos claves con el mismo contenido son la misma consulta.
 */
export function retainedQuery<T>(resource: ValueSource<T>, key: () => unknown): RetainedQuery<T> {
  const currentKey = computed(() => JSON.stringify(key()));
  const retained = linkedSignal<
    { value: T; key: string } | undefined,
    { value: T; key: string } | undefined
  >({
    source: () =>
      resource.hasValue() ? { value: resource.value(), key: untracked(currentKey) } : undefined,
    computation: (current, previous) => current ?? previous?.value,
  });
  return {
    value: computed(() => retained()?.value),
    current: computed(() => retained() !== undefined && retained()?.key === currentKey()),
  };
}
