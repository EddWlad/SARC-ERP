import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ScopeConnectorResponse } from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { UptimeApi } from '../data-access/uptime-api';
import { UptimeImportsApi } from '../data-access/uptime-imports-api';
import { ConnectorAlias, UptimeImportRow } from '../domain/uptime-import';

const PAGE_SIZE = 100;

/** Filas sin conector y alias aprobados. Cambiar un alias solo afecta cargas posteriores. */
@Injectable()
export class PendingLinksFacade {
  private readonly api = inject(UptimeImportsApi);
  private readonly uptime = inject(UptimeApi);

  readonly rows = signal<UptimeImportRow[]>([]);
  readonly total = signal(0);
  readonly aliases = signal<ConnectorAlias[]>([]);
  readonly options = signal<ScopeConnectorResponse[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  private readonly query = signal('');

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [pending, aliases, options] = await Promise.all([
        firstValueFrom(this.api.pendingLinks(0, PAGE_SIZE)),
        firstValueFrom(this.api.aliases(0, PAGE_SIZE)),
        firstValueFrom(this.uptime.scope(this.query())),
      ]);
      this.rows.set(pending.content);
      this.total.set(pending.totalElements);
      this.aliases.set(aliases.content);
      this.options.set(options);
    } catch (error) {
      this.error.set(toProblem(error).detail);
    } finally {
      this.loading.set(false);
    }
  }

  async search(text: string): Promise<void> {
    this.query.set(text);
    try {
      this.options.set(await firstValueFrom(this.uptime.scope(text)));
    } catch (error) {
      this.error.set(toProblem(error).detail);
    }
  }

  /** Conectores elegibles: los del equipo identificado, o los de la busqueda si no lo hay. */
  optionsFor(equipmentId: string | null | undefined): ScopeConnectorResponse[] {
    const options = this.options();
    return equipmentId ? options.filter((option) => option.equipmentId === equipmentId) : options;
  }

  link(row: UptimeImportRow, connectorId: string, rememberLabel: boolean): Promise<boolean> {
    return this.change(() => firstValueFrom(this.api.link(row, connectorId, rememberLabel)));
  }

  updateAlias(alias: ConnectorAlias, connectorId: string, active: boolean): Promise<boolean> {
    return this.change(() => firstValueFrom(this.api.updateAlias(alias, connectorId, active)));
  }

  /** Tras un error tambien recarga: la version que se envio pudo quedar vieja. */
  private async change(request: () => Promise<unknown>): Promise<boolean> {
    this.busy.set(true);
    this.error.set('');
    try {
      await request();
      await this.load();
      return true;
    } catch (error) {
      const detail = toProblem(error).detail;
      await this.load();
      this.error.set(detail);
      return false;
    } finally {
      this.busy.set(false);
    }
  }
}
