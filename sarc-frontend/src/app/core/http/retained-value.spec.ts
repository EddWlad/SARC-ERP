import { signal } from '@angular/core';
import { describe, expect, it } from 'vitest';

import { retainedQuery, retainedValue } from './retained-value';

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

describe('retainedQuery (Feature 009, US4)', () => {
  function setUp() {
    const value = signal<string[] | undefined>(undefined);
    const key = signal({ from: '2026-11-01', network: '' });
    const query = retainedQuery(
      { hasValue: () => value() !== undefined, value: () => value()! },
      () => key(),
    );
    return { value, key, query };
  }

  it('marca como vigente el valor que llegó para la consulta actual', () => {
    const { value, query } = setUp();
    expect(query.value()).toBeUndefined();
    expect(query.current()).toBe(false);

    value.set(['a']);

    expect(query.value()).toEqual(['a']);
    expect(query.current()).toBe(true);
  });

  it('un refresco fallido de la misma consulta conserva el valor como vigente', () => {
    const { value, query } = setUp();
    value.set(['a']);
    expect(query.value()).toEqual(['a']);

    // El recurso pierde su valor al fallar el refresco.
    value.set(undefined);

    expect(query.value()).toEqual(['a']);
    expect(query.current()).toBe(true);
  });

  it('con una consulta nueva el valor anterior deja de ser vigente hasta su respuesta', () => {
    const { value, key, query } = setUp();
    value.set(['a']);
    expect(query.current()).toBe(true);

    key.set({ from: '2026-11-01', network: 'KIA' });
    value.set(undefined);

    expect(query.value()).toEqual(['a']);
    expect(query.current()).toBe(false);

    value.set(['k']);
    expect(query.value()).toEqual(['k']);
    expect(query.current()).toBe(true);
  });

  it('una clave con el mismo contenido sigue siendo la misma consulta', () => {
    const { value, key, query } = setUp();
    value.set(['a']);

    key.set({ from: '2026-11-01', network: '' });

    expect(query.current()).toBe(true);
  });
});
