# QA004 visibilidad

RED antes de producto:44 regresiones de consumo PASS;9 tests nuevos,6 fallos esperados,0 errores de entorno. Casos fallan por propiedades restringidas presentes, totalElements15 en vez de10 e historial completo. Log: backend target/feature004-red-tests.log.

GREEN parcial, 2026-10-31. `mvnw verify` sin errores (log target/feature004-verify.log). Frontend: 156/156 pruebas, lint PASS, build PASS, inicial 341,56 kB.

Recorrido con Feature004BrowserSandbox (PostgreSQL desechable, storage simulado) en 1366x768:

- Gerente: tablero sin tarjeta operativa; `state=TEST` da 403 `CONSUMPTION_OPERATIONAL_ACCESS_DENIED`; emisión PDF con X-Report-Id y X-Report-Cut-Off propios, `Cache-Control: no-store, private`.
- Gerente sobre su OFFICIAL_ONLY: descarga binaria con tamaño exacto.
- Administrador emite XLSX WITH_OPERATIONS; gerente lo ve restringido, con motivo y acción.
- Nueva emisión desde ese original: precarga filtros, crea otro ID y corte; checksum y fecha del original iguales.
- Conector: ayuda de una línea a 1366px y de dos a 375px; 12px libres antes del campo siguiente, sin scroll horizontal.

Segunda ronda, misma fixture:

- API con sesión real: gerente, supervisor y asistente solo ven 10 sesiones validadas, sin campos operativos, y `state=TEST` da 403. Analista, administrador y multirol ven 15 y `state=TEST` da 200.
- Interfaz: supervisor en tablero; asistente en matriz completa; analista y multirol en sesiones con acciones operativas.
- Acceso directo de asistente a /usuarios, /consumo/importaciones y tipos de conector: redirige a /inicio.
- Pérdida de rol con sesión viva: administrador cambia roles de analista desde otra pestaña; la petición siguiente da 401 `UNAUTHENTICATED` y la interfaz vuelve a login con `returnUrl`. Multirol, tras perder ANALISTA y reingresar, ve 10 sesiones y 403.
- Errores de storage: `ConsumptionApiTest` verifica `REPORT_ARCHIVE_UNAVAILABLE`; `IssuedReportAccessApiTest` cubre descarga. No probado en navegador.

T066 proveedor real pendiente por decisión explícita del usuario: no dispone de entorno/carpeta QA autorizados. No usar credenciales dev ni afirmar prueba externa por mock.
