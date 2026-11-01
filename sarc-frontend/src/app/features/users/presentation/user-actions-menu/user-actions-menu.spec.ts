import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatMenuTrigger } from '@angular/material/menu';
import { By } from '@angular/platform-browser';
import { describe, expect, it } from 'vitest';

import { User } from '../../domain/user';
import { UserActionsMenu } from './user-actions-menu';

describe('UserActionsMenu', () => {
  it('emite la accion cuando el menu ya devolvio el foco a su disparador', async () => {
    const fixture: ComponentFixture<UserActionsMenu> = TestBed.createComponent(UserActionsMenu);
    fixture.componentRef.setInput('user', { fullName: 'Ana' } as User);
    document.body.append(fixture.nativeElement);
    fixture.detectChanges();
    const trigger = fixture.debugElement
      .query(By.directive(MatMenuTrigger))
      .injector.get(MatMenuTrigger);
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    button.focus();
    trigger.openMenu();
    fixture.detectChanges();

    const focusedOnEmit = new Promise<Element | null>((resolve) =>
      fixture.componentInstance.edit.subscribe(() => resolve(document.activeElement)),
    );
    const item = document.querySelector<HTMLButtonElement>('[mat-menu-item]');
    item?.click();

    expect(await focusedOnEmit).toBe(button);
    fixture.nativeElement.remove();
  });
});
