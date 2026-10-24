import { HttpResponse } from '@angular/common/http';
import { DOCUMENT, inject, Service } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';

import { toProblemFromBlob } from '../http/problem';

/** Entrega al navegador un archivo descargado por la API (reportes PDF/Excel). */
@Service()
export class FileSaver {
  private readonly document = inject(DOCUMENT);

  /**
   * Ejecuta la descarga y guarda el archivo. Si falla, rechaza con el `Problem` del backend (el
   * cuerpo del error llega como Blob en estas peticiones).
   */
  async download(request: Observable<HttpResponse<Blob>>, fallbackName: string): Promise<void> {
    try {
      this.save(await firstValueFrom(request), fallbackName);
    } catch (error) {
      throw await toProblemFromBlob(error);
    }
  }

  /** Usa el nombre de `Content-Disposition`; si no viene, `fallbackName`. */
  save(response: HttpResponse<Blob>, fallbackName: string): void {
    if (response.body === null) {
      return;
    }
    const url = URL.createObjectURL(response.body);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = filenameFrom(response.headers.get('Content-Disposition')) ?? fallbackName;
    link.click();
    URL.revokeObjectURL(url);
  }
}

export function filenameFrom(contentDisposition: string | null): string | undefined {
  const match = contentDisposition?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
  return match ? decodeURIComponent(match[1]) : undefined;
}
