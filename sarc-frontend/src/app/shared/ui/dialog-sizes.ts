import { MatDialogConfig } from '@angular/material/dialog';

/**
 * Dialogos con formularios de varias columnas. Material 3 limita los dialogos a 560px; estos
 * necesitan mas ancho en escritorio y ocupan casi toda la pantalla en movil.
 */
export const WIDE_DIALOG: MatDialogConfig = { width: '760px', maxWidth: '95vw' };
