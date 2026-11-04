# Línea base de la feature 005

**Fecha**: 2026-11-01. **Rama**: `main`. **Commit**: `375236597201131366a27da67146cb34cfb859d7`.

## Versiones

| Herramienta | Versión |
|-------------|---------|
| Java | 25.0.3 |
| Node | 24.18.0 |
| Angular CLI | 22.2.4 |
| PostgreSQL de pruebas | 18 (Testcontainers) |

## Resultados

| Comando | Resultado |
|---------|-----------|
| `./mvnw test` | Salida 0. 269 pruebas en 38 clases, 0 fallos, 0 errores, 0 omitidas |
| `npm test` | Salida 0. 41 archivos, 164 pruebas |
| `npm run lint` | Salida 0 |
| `npm run build` | Salida 0 |
| `npm run format:check` | Salida 1. 84 archivos avisados |

`format:check` falla en local por fin de línea: el índice guarda LF y el árbol de trabajo CRLF (`core.autocrlf=true`). No es un defecto de formato de la versión versionada. Comparar contra este punto de partida y comprobar los archivos nuevos o tocados con fin de línea LF.

## Migraciones V001–V012 (SHA-256, primeros 16 caracteres)

| Migración | Hash |
|-----------|------|
| V001 | `ccef1a4b1de3c7de` |
| V002 | `74157c76512b7ec8` |
| V003 | `aedaf7e519a92d39` |
| V004 | `774e8127c25f1ca0` |
| V005 | `3e802a889d549b09` |
| V006 | `84b5dca7b89ea96a` |
| V007 | `1fb6d16792deed92` |
| V008 | `3ff27c0da7e91635` |
| V009 | `d6855eb1bdf657fe` |
| V010 | `43478a5f5bd03a57` |
| V011 | `7d702719026b0998` |
| V012 | `f238db8a3736b323` |

## Cambios locales ajenos, presentes antes de empezar

`.claude/launch.json`, `.specify/integration.json`, `sarc-frontend/.impeccable/design.json`, y sin seguimiento `.agents/skills/impeccable/`, `.claude/agents/`, `.claude/skills/`, `.codex/`, `.github/agents/`, `.github/hooks/`, `.github/skills/`, `.specify/integrations/claude.manifest.json`, `sarc-frontend/.impeccable/live/`. No forman parte de esta feature.

Los puertos 8080, 4200 y 5432 ya estaban en uso por procesos ajenos a estas pruebas y se dejan como están.
