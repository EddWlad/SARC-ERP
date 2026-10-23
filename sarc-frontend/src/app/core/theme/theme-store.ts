import { DOCUMENT, effect, inject, Service, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'sarc.theme';
const MODES: readonly ThemeMode[] = ['system', 'light', 'dark'];

/** Modo claro/oscuro. 'system' sigue la preferencia del sistema operativo. */
@Service()
export class ThemeStore {
  private readonly document = inject(DOCUMENT);
  private readonly current = signal<ThemeMode>(this.readStored());

  readonly mode = this.current.asReadonly();

  constructor() {
    effect(() => {
      const mode = this.current();
      const root = this.document.documentElement.classList;
      root.toggle('theme-light', mode === 'light');
      root.toggle('theme-dark', mode === 'dark');
    });
  }

  setMode(mode: ThemeMode): void {
    this.current.set(mode);
    try {
      this.document.defaultView?.localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // Almacenamiento bloqueado (modo privado): el modo vale solo para esta visita.
    }
  }

  private readStored(): ThemeMode {
    try {
      const stored = this.document.defaultView?.localStorage.getItem(STORAGE_KEY);
      return MODES.find((mode) => mode === stored) ?? 'system';
    } catch {
      return 'system';
    }
  }
}
