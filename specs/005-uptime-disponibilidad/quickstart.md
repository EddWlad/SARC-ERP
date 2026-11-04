# Quickstart: cierre y validación Feature-005

La evidencia anterior documenta escenarios de 005; no certifica los cambios futuros de 006. Reglas en spec.md, modelo/data-model.md, contratos en contracts/. Los 48 escenarios originales y su cobertura declarada están en evidence/quickstart-results.md; allí se identifican seis recorridos parciales, no se declaran ejecutados manualmente.

## Aislamiento

PostgreSQL 18 en Testcontainers, almacenamiento simulado y Compose desactivado; no migrar BD dev ni cargar datos reales en ella. Fixtures de inventario/sesiones por rol, conectores de uno/dos/tres e identidades verificadas/ambiguas. Archivos reales nunca se versionan. Cerrar solo procesos/puertos propios de QA.

## Comandos

Desde sarc-bakend:

```powershell
.\mvnw.cmd test
```

En sarc-frontend:

```powershell
npm test -- --watch=false
npm run lint
npm run format:check
npm run build
```

Si el checkout CRLF causa warnings, comprobar también Prettier --end-of-line auto y registrar ambas salidas; no mezclar un reformato de módulos ajenos. OpenAPI se actualiza intencionalmente con OpenApiContractTest y -Dopenapi.update=true; después npm run api:types. En este cierre los fixes son internos y no requieren cambio de contrato.

## Escenarios esenciales

1. Captura 1296/105/0/39 → 97,29 %, pendiente, aislada por conector.
2. Semanas exactas vs ventanas 24 h/7 d/30 d; no suma de solapamientos, no oficial sin confirmación.
3. Validación y corrección: versión anterior vigente hasta validar; exclusión reversible; concurrencia/idempotencia.
4. Cobertura: ausente distinto de 0; especiales y excluidas; varias semanas por pares conector-semana; empates completos.
5. Importación de ambas hojas, horas al minuto, uptime informado sin Ocupado, alias/propuestas y pendientes.
6. Cinco roles y unión: consulta sin escritura ni valores/motivos operativos; UI y API directa.
7. Dashboard/PDF/Excel mismo corte, nombres actuales, sin datos sensibles, copia archivada inmutable.
8. Regresión de Consumo/inventario y presupuesto inicial ≤500 kB; matriz visual 360/390/768/1024/1440 × dos temas.
9. Cierre QA: rango elegido vacío con pendientes fuera/dentro → solo los del rango, igual al filtrar red; período de apertura estable para sin atribuir; PDF y Excel incluyen ese total; etiqueta de 20 dígitos no aborta las demás filas del lote.

## Pendientes y límites

T124 sigue abierta: cronometrar captura y lectura con personas reales. Bench secuencial no prueba 30 usuarios; evidence/performance.md incluye consultas a 1000 conectores por encima del objetivo de 2 s. El Excel 005 asigna semana declarada y no conserva una referencia independiente con horario desconocido; esa corrección y los pasteles son 006. No dar por hechos esos cambios en este recorrido.

Registrar resultados finales y regresiones en evidence/closure-qa.md y final-gates.md. No marcar pruebas humanas o recorridos parciales como completados por haber pasado la suite automática.
