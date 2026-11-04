# Línea base de Consumo (T002)

Tomada antes de modificar código de Consumo, el 2026-11-01, sobre base desechable de Testcontainers. La base de Compose no se tocó.

## Cifras y exportaciones

- Arnés: `Feature005ConsumptionBaseline`, con `Feature004Fixtures` y filtros fijos `from=2026-10-01`, `to=2026-11-01`.
- Comando: `./mvnw test -Dtest=Feature005ConsumptionBaseline -Dsarc.qa.baseline=before`.
- Salida: `sarc-bakend/target/consumption-before.txt`, con resumen JSON, texto de PDF y celdas de Excel de `GERENTE` y `ANALISTA`. Se excluyen líneas de corte, generación y paginación, que cambian en cada emisión.
- Resumen oficial: 10 sesiones, 2.240,000 kWh, 7 equipos, 1 sesión sin energía, promedio 248,889 kWh por sesión.
- «Aprobadas sobre umbral», antes del cambio: en el PDF y en el Resumen de Excel de ambos roles (1 sesión, 1.700 kWh).

T119 repite el comando con `-Dsarc.qa.baseline=after` y compara ambos volcados.

## Dashboard

Arnés: `Feature004BrowserSandbox`, rol `ANALISTA`, período «Mes anterior» (octubre de 2026). Medidas del bloque «Fuera de los indicadores oficiales». Son iguales en ambos temas, porque la geometría no depende del tema; se comprobó con la clase de tema activa.

| Ancho | Columnas | Bloque (px) | Filas (px de alto) | Scroll horizontal |
|---|---|---|---|---|
| 360 | 1 | 328 × 338 | 40, 64, 40, 40, 40 | No |
| 390 | 1 | 358 × 338 | 40, 64, 40, 40, 40 | No |
| 768 | 2 | 705 × 238 | 64, 64, 40, 40, 40 | No |
| 1024 | 2 | 705 × 238 | 64, 64, 40, 40, 40 | No |
| 1440 | 4 | 1121 × 198 | 64, 64, 64, 64, 40 | No |

## Defectos observados

- Lista plana sin agrupación: mezcla vinculación y aprobación con revisión energética.
- A 1440, cuatro columnas y una fila huérfana; la etiqueta y la cantidad se parten en dos líneas.
- A 768, «· 36,00 kWh» se separa de su cantidad.
- Marcado desigual: tres filas usan `.muted` para la energía y dos no, de modo que la energía cambia de color sin razón.
- Sin desbordes horizontales en ningún ancho.
