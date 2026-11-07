# Evidencia SC-005: escaneo de secretos

**Fecha**: 2026-11-07. **Herramienta**: gitleaks v8.30.1 (linux_x64), descargado de su Release oficial con la suma SHA-256 verificada (la misma que fija `ci.yml`), con `--redact` y la configuración del repositorio (`.gitleaks.toml`).

| Objetivo | Comando | Resultado |
|---|---|---|
| Historial completo de Git (58 commits) | `gitleaks git --redact --config .gitleaks.toml .` | **0 hallazgos** |
| Árbol de trabajo (1369 archivos, sin ignorados) | `gitleaks dir --redact --config .gitleaks.toml` | **0 hallazgos** |
| Artefactos de ensayo (JAR y `.tar.gz` del frontend construidos localmente) y registro completo de `./mvnw verify` | `gitleaks dir --max-archive-depth 2 ...` | **0 hallazgos** |

## Hallazgos iniciales y su tratamiento

La primera ejecución, sin permisos, dio 6 hallazgos en el historial y 7 en el árbol. **Ninguno es un secreto real**; se revisó cada uno sin imprimir su valor:

| Regla | Dónde | Naturaleza | Tratamiento |
|---|---|---|---|
| `generic-api-key` | Pruebas de integración `IdentityApiTest`, `InventoryApiTest`, `EquipmentImportApiTest`, `ConsumptionApiTest` | Contraseña ficticia de las pruebas | Permiso con ruta y valor en `.gitleaks.toml` |
| `sumologic-access-token` | `specs/004-.../evidence/baseline-migrations.json` y `specs/006-.../evidence/baseline.md` | Sumas SHA-256 de migraciones (hexadecimal) | Permiso por ruta en `.gitleaks.toml` |
| `curl-auth-header` | `deploy/tests/nginx-config.bats` | Cabecera y cookie ficticias de una prueba | Permiso por ruta (`deploy/tests/*.bats`) |

Cada permiso lleva su motivo y se limita a rutas concretas; un hallazgo nuevo debe corregirse (revocar y rotar), no añadirse a la lista.

## Pendiente

El mismo escaneo sobre un **Release de ensayo real** y sobre los **registros de ejecuciones de GitHub Actions** requiere publicar en GitHub (no hecho: esta implementación no hace *push*). Lo ejecutarán el flujo `version-candidata` y el trabajo `secretos` de CI en la primera integración.
