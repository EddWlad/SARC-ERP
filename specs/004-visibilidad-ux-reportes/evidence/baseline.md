# Base de comparación Feature004

Baseline Git7354d6e, rama codex/004-visibilidad-ux-reportes. Java25.0.3, Node24.18.0. V001–V010: hashes en baseline-migrations.json. Servicios Docker del usuario: ninguno activo al inicio. BD dev no utilizada.

Suites base en ejecución: backend target/feature004-baseline-maven.log; frontend feature004-baseline-{test,lint,build}.log. Primer intento Angular en sandbox restringido falló por acceso denegado del compilador Sass; se repite con permiso del entorno. No se atribuye ese error al producto.

Resultados/tamaños/carga se completarán con ejecución real antes de modificar comportamiento.

Base frontend:132/132 pruebas,30 archivos; lint PASS; build PASS; inicial341.56kB. Backend verify PASS (ver log para recuento). Primer intento restringido no es defecto del producto.

Backend239/239 PASS. Fixture250.000 +15 sesiones, cierre Testcontainers mediante done. Medición before.json: sesiones p95=3550ms; dashboard p95=20857ms; PDF1500ms/102660bytes; XLSX3342ms/930307bytes. La base actual NO cumple los objetivos2s/5s bajo esta carga local; registrar comparación y no atribuir esta limitación previa a004 ni afirmar capacidad VPS. T006 adaptación de actores se verifica junto a contrato RED antes de producto.
