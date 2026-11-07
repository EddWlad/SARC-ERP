import { signal } from '@angular/core';
import { describe, expect, it } from 'vitest';

import { retainedValue } from './retained-value';

describe('retainedValue', () => {
  function resource() {
    const value = signal<string[] | undefined>(undefined);
    return { value, source: { hasValue: () => value() !== undefined, value: () => value()! } };
  }

  it('no tiene valor mientras el recurso nunca lo tuvo', () => {
    const { source } = resource();

    expect(retainedValue(source)()).toBeUndefined();
  });

  it('conserva el último valor mientras el recurso carga de nuevo', () => {
    const { value, source } = resource();
    const latest = retainedValue(source);

    value.set(['a', 'b']);
    expect(latest()).toEqual(['a', 'b']);

    // Un filtro nuevo: el recurso vacía su valor hasta que llega la respuesta.
    value.set(undefined);
    expect(latest()).toEqual(['a', 'b']);

    value.set(['c']);
    expect(latest()).toEqual(['c']);
  });

  it('una respuesta vacía reemplaza al valor anterior', () => {
    const { value, source } = resource();
    const latest = retainedValue(source);

    value.set(['a']);
    value.set([]);

    expect(latest()).toEqual([]);
  });
});
