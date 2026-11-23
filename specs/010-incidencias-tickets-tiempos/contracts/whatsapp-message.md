# Contrato: Mensaje para WhatsApp

**Feature**: `010-incidencias-tickets-tiempos` | **Requisitos**: FR-055 a FR-060, Q3 | **Endpoint**: `GET /api/v1/incidents/tickets/{id}/message` ([incidents-api.md](incidents-api.md))

Un solo compositor en el servidor (`TicketMessageComposer`, dominio puro) genera el texto. La interfaz lo muestra en un `textarea` editable y lo copia al portapapeles. Editar el texto o copiarlo no cambia el ticket y no registra ninguna comunicación (FR-058, FR-059).

## Plantilla base (opciones desactivadas)

```text
*{CÓDIGO}*
Cargador/Electrolinera: {ELEMENTO}
Ubicación: {UBICACIÓN}
Google Maps: {ENLACE}
Novedad: {DESCRIPCIÓN}
Estado: {Abierto|Cerrado}
```

| Marcador | Equipo / conectores | Servicio de chatbot | Elemento del sitio |
|----------|---------------------|---------------------|--------------------|
| Rótulo de la línea 2 | «Cargador/Electrolinera» | «Elemento» | «Elemento» |
| `{ELEMENTO}` | Nombre actual (D12); con conectores: «Nombre / Conectores 1 y 2» | «Servicio de chatbot» y, si se registró, el alcance («total» o «parcial») | «Sitio / Barrera vehicular» o «Sitio / {nombre del elemento}» |
| `{UBICACIÓN}` | Ubicación actual del inventario | Línea omitida (FR-060) | Ubicación conservada en T0 |
| `{ENLACE}` | `maps_url` actual del inventario | Línea omitida | Enlace conservado en T0 |
| `{DESCRIPCIÓN}` | Descripción actual del ticket | Igual | Igual |
| Estado | Estado actual | Igual | Igual |

**Reglas**:

- Sin enlace de Maps, la línea queda como `Google Maps: ` con el valor vacío. La respuesta incluye `missingFields: ["mapsUrl"]` y la interfaz muestra un aviso discreto. La copia no se bloquea (FR-057).
- Sin ubicación en el inventario, la línea queda `Ubicación: ` vacía, con la misma regla.
- El asterisco de la primera línea es negrita de WhatsApp. Es texto plano; no se usan emojis.
- Los saltos de línea son `\n`. La interfaz copia el texto tal cual, sin espacios al final de las líneas.

## Opciones (FR-056a), desactivadas cada vez que se abre el diálogo

| Opción | Parámetro | Línea añadida |
|--------|-----------|---------------|
| «Incluir hora de detección (T0)» | `includeDetection=true` | Antes de «Novedad»: `Detección: dd/MM/yyyy HH:mm` (T0 del episodio actual, `America/Guayaquil`) |
| «Incluir acciones realizadas» | `includeActions=true` | Después de «Novedad»: `Acciones:` y una línea por evento no anulado `CORRECTIVE_ACTION` o `ESCALATED` del episodio actual, en orden de `occurredAt`: `- HH:mm {texto}` (con la fecha si es de otro día) |

Si no hay acciones, la opción añade `Acciones: sin acciones registradas`; no se inventan acciones.

## Tipo de mensaje

`kind` (`INITIAL`, `UPDATE`, `CLOSURE`) solo preselecciona el tipo de la comunicación en «Registrar envío» y no cambia la plantilla. El estado ya distingue el aviso de cierre.

## Lo que el texto nunca incluye

- UID/PUK, URL OCPP ni número de serie (RNF-SEG-07).
- El nombre del responsable ni datos personales de clientes.
- Una afirmación de cumplimiento del Protocolo §4.1. La interfaz muestra una nota fija: «Agrega la fuente de validación, la solicitud concreta y la próxima verificación si el aviso lo requiere» (Q3).

## Diferencia con el aviso mínimo del protocolo (Q3, documentada)

El formato pedido por el responsable tiene seis datos: número, elemento, ubicación, Maps, novedad y estado. El Protocolo §4.1 pide además:

- la hora de detección;
- la fuente de validación;
- las acciones realizadas;
- la solicitud concreta;
- la próxima verificación.

SARC cubre la hora y las acciones como opciones; el resto lo escribe el analista. La diferencia queda en el «Cambios respecto del PRD» de la spec y en CC-PRD-010.

## Registrar envío

Después de copiar, el diálogo ofrece «Registrar envío» (secundario). Abre el formulario de comunicación con:

- canal;
- tipo, preseleccionado con `kind`;
- hora real del envío (por defecto, ahora; editable);
- justificación, si es retroactiva;
- evidencia opcional.

Llama a `POST /tickets/{id}/communications`. Cerrar el diálogo sin registrar no deja rastro.

## Pruebas del contrato

- Una prueba unitaria de `TicketMessageComposer` por clase de elemento, con y sin Maps, con y sin ubicación, con cada opción y con acciones anuladas excluidas.
- Una instantánea (snapshot) del texto exacto de un ticket de equipo con conectores.
- Comprobación de que la respuesta no contiene `uid`, `puk` ni `ocpp` en ningún caso.
