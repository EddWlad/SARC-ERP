# Quickstart: validación

Guía para comprobar la feature una vez implementada. Detalles en [data-model.md](data-model.md) y
[contracts/api-changes.md](contracts/api-changes.md).

## Requisitos

- Docker en marcha para Testcontainers.
- Probar V009 sobre PostgreSQL desechable; no sobre la base del `compose` hasta terminar.
- Al acabar, detener lo levantado por la prueba en 8080, 4200 y 5432.

## Pruebas automatizadas

```bash
cd sarc-bakend && ./mvnw test
```

```bash
cd sarc-frontend && npm run build && npm test
```

Si cambió el contrato:

```bash
cd sarc-bakend && ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true
```

```bash
cd sarc-frontend && npm run api:types
```

## Escenario del incidente

Usar los mismos archivos del 2026-10-30.

1. Descartar el lote de sesiones `eba003e1`, en estado `READY`. Sus 83 inválidas no guardaron lectura.
2. Cargar de nuevo la plantilla de inventario.
3. Comprobar en la vista previa cerca de 95 filas «Completan identidad» y que ninguna pide aprobación
   si solo completa identidad. Revisar aparte las que sigan como «Modificados».
4. Confirmar. «Identidades externas» muestra el par; código, serie, conectores y `version` no cambian.
5. Cargar `Transacciones_01-10-2026_30-10-2026.xlsx`.

Resultado esperado de la vista previa:

| Medida | Antes | Después |
| --- | --- | --- |
| Filas totales | 1.724 | 1.724 |
| Pendientes por identidad no registrada | 1.583 | 0 entre las 1.641 ya clasificadas |
| Nuevas entre esas 1.641 | 58 | 1.641 |
| Rechazadas por energía o término vacíos | 83 | 0 |

Las 83 antiguas quedarán nuevas o pendientes según su par, que hoy se desconoce. Sus motivos deben ser
específicos. La energía conocida de las 1.641 suma 21.892,460 kWh; el total confirmado solo aumenta si
alguna de las 83 trae energía, lo que no ocurre en este archivo.

6. Confirmar y reimportar dos veces: cero sesiones nuevas y mismos indicadores.

## Matriz A–E

Con identidad verificada, importar la matriz de US3. Tablero, resumen del histórico, PDF y Excel con mismo
filtro muestran 5 sesiones, 3,750 kWh conocidos y 2 sesiones sin energía. El caso C aparece como 0 kWh, los
casos A y B como «No informado».

## Solo sin energía

Con un período que solo tenga sesiones sin energía: «Sin datos», no 0 kWh. El equipo sale del Top 5 y
la nota lo cuenta.

## Identidades

| Prueba | Esperado |
| --- | --- |
| Par distinto en equipo con identidad verificada | Conflicto; equipo sin cambios |
| Solo «Enerlink cargador» | Fila inválida; ni equipo ni identidad |
| Identidad más otro cambio, sin aprobar | Advertencia al confirmar; fila omitida, sin identidad |
| Alta manual Enerlink sin ámbito | El diálogo no permite guardar |
| Mismo par con otras tildes o espacios | Sin duplicado |
| Dos equipos con par equivalente (fixture SQL) | Sesión pendiente, «Identidad Enerlink ambigua» |

## Reimportación

| Prueba | Esperado |
| --- | --- |
| Sesión vacía llega completa | `MODIFIED`, aprobada conserva revisión |
| Reimportación vacía sobre dato conocido | Exige aprobación y nombra el campo |
| Otro par en sesión con equipo | `EQUIPMENT_CHANGED`; aprobar no basta |
| Pendiente con par ya resoluble | `LINKABLE`; se vincula al confirmar con auditoría |
| Inventario cambia antes de confirmar | No aplica vínculo obsoleto; queda pendiente |

## Regresión

- Wallbox con energía o término vacíos sigue inválido.
- KIA punto por Enerlink sigue en «Por aprobar».
- Reportes archivados conservan su archivo y huella.
