import { inject, Service, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { KiaPointResponse } from '../../../core/api/generated';
import { IntermittencyApi } from '../data-access/intermittency-api';

const SEARCH_DEBOUNCE_MS = 300;

/** Opciones del selector de punto: busca puntos KIA por nombre o codigo mientras el usuario escribe. */
@Service({ autoProvided: false })
export class PointOptions {
  private readonly api = inject(IntermittencyApi);

  readonly query = signal('');
  readonly options = toSignal(
    toObservable(this.query).pipe(
      debounceTime(SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
      switchMap((text) => this.api.searchPoints(text).pipe(catchError(() => of([])))),
    ),
    { initialValue: [] as KiaPointResponse[] },
  );
}
