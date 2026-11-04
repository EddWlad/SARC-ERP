import { computed, linkedSignal, Signal } from '@angular/core';

/** Paginacion local de un listado que ya llego completo; vuelve al inicio si cambia el listado. */
export function clientPage<T>(items: Signal<readonly T[]>, initialSize = 10) {
  const index = linkedSignal({ source: items, computation: () => 0 });
  const size = linkedSignal<readonly T[], number>({
    source: items,
    computation: (_, previous) => previous?.value ?? initialSize,
  });
  return {
    index,
    size,
    visible: computed(() => items().slice(index() * size(), (index() + 1) * size())),
    change(event: { pageIndex: number; pageSize: number }): void {
      index.set(event.pageIndex);
      size.set(event.pageSize);
    },
  };
}
