# Contrato: manifiesto de versión candidata

Asset `manifest.json` de cada Release pre-release. Lo produce el flujo «versión candidata» y lo consume `sarc-deploy` (comprobaciones previas) y la persona que audita.

```json
{
  "schema": 1,
  "version": "2026.11.20-57-e85c910",
  "commit": "e85c910…(40 hex)",
  "ciRunId": 123456789,
  "createdAt": "2026-11-20T15:04:05Z",
  "checks": [
    { "name": "backend-verify", "conclusion": "success", "kind": "codigo", "url": "https://github.com/…" }
  ],
  "artifacts": [
    { "name": "sarc-backend-2026.11.20-57-e85c910.jar", "bytes": 0, "sha256": "…" },
    { "name": "sarc-frontend-2026.11.20-57-e85c910.tar.gz", "bytes": 0, "sha256": "…" }
  ],
  "migrations": {
    "latest": "V016",
    "files": [ { "name": "V016__uptime_referencia_temporal.sql", "sha256": "…", "flywayChecksum": -206902758 } ]
  },
  "schemaChanges": false
}
```

Reglas:

- `version` único; coincide con el tag Git y con `build-info` del JAR.
- `checks` incluye **todas** las comprobaciones obligatorias; cualquiera distinta de `success` impide crear el Release.
- `SHA256SUMS` (asset aparte) contiene las mismas sumas; `sarc-deploy` verifica ambas antes de instalar.
- `migrations.files` lista todas las migraciones del JAR, con su suma SHA-256 y `flywayChecksum` (el CRC-32 con signo que Flyway guarda en `flyway_schema_history.checksum`, que permite a `sarc-deploy` comparar el historial de la base con el manifiesto); `schemaChanges` es informativo y compara con el Release candidato anterior. No decide nada en producción: la compatibilidad la decide `sarc-deploy` comparando `migrations.latest` con la última migración aplicada, y el registro de publicación guarda `migrationsApplied[]`.
- Prohibido: URLs con credenciales, valores de configuración, direcciones internas, datos de usuarios.
