/** Las dos opciones adicionales del mensaje para WhatsApp, desactivadas cada vez que se abre (FR-056a). */
export interface MessageOptions {
  readonly includeDetection: boolean;
  readonly includeActions: boolean;
}
