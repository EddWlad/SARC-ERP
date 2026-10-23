import { Component, input } from '@angular/core';
import { LucideZap } from '@lucide/angular';

/** Marco comun de las pantallas fuera de la aplicacion (ingreso, cambio de clave). */
@Component({
  selector: 'sarc-auth-card',
  imports: [LucideZap],
  template: `
    <main class="auth-screen">
      <section class="auth-card sarc-fade-in" [attr.aria-labelledby]="headingId">
        <header class="auth-header">
          <span class="auth-brand"><svg lucideZap [size]="28"></svg> SARC</span>
          <h1 [id]="headingId">{{ heading() }}</h1>
          @if (subtitle(); as text) {
            <p>{{ text }}</p>
          }
        </header>
        <ng-content />
      </section>
    </main>
  `,
  styles: `
    .auth-screen {
      display: grid;
      place-items: center;
      min-height: 100dvh;
      padding: var(--sarc-space-4);
      background: var(--mat-sys-surface-container);
    }

    .auth-card {
      width: min(100%, 440px);
      padding: var(--sarc-space-6) var(--sarc-space-5);
      border-radius: var(--sarc-radius-lg);
      background: var(--mat-sys-surface);
      box-shadow: var(--mat-sys-level2);
    }

    .auth-header {
      margin-bottom: var(--sarc-space-5);

      h1 {
        margin: var(--sarc-space-4) 0 var(--sarc-space-1);
        font: var(--mat-sys-headline-small);
      }

      p {
        margin: 0;
        color: var(--mat-sys-on-surface-variant);
        font: var(--mat-sys-body-medium);
      }
    }

    .auth-brand {
      display: inline-flex;
      align-items: center;
      gap: var(--sarc-space-2);
      color: var(--mat-sys-primary);
      font: var(--mat-sys-title-large);
    }
  `,
})
export class AuthCard {
  readonly heading = input.required<string>();
  readonly subtitle = input<string>();

  protected readonly headingId = 'auth-card-heading';
}
