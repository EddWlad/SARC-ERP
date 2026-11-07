# Versión candidata

Una **versión candidata** es el resultado de una integración en `main` cuyo CI quedó completamente verde. Es un GitHub Release marcado como *pre-release* con su tag, y no es todavía una publicación en producción.

**Responsable**: responsable técnico. **Requisitos previos**: acceso de lectura al repositorio de GitHub y `sha256sum` (Linux, macOS o Git Bash en Windows).

## Cómo se produce

1. Se integra un cambio en `main` y el flujo **CI** termina con éxito (backend, frontend, migraciones inmutables, dependencias, secretos y scripts de despliegue).
2. El flujo **Versión candidata** se dispara solo, construye el JAR y el frontend una única vez y genera `manifest.json` y `SHA256SUMS`.
3. Escanea los artefactos en busca de secretos. Si encuentra alguno, **no** crea la versión.
4. Crea el tag y el Release pre-release con cuatro archivos: `sarc-backend-<versión>.jar`, `sarc-frontend-<versión>.tar.gz`, `manifest.json` y `SHA256SUMS`.

El nombre de la versión es `AAAA.MM.DD-<n>-<sha7>`: fecha UTC, número de ejecución de CI y los siete primeros caracteres del commit. Ejemplo ficticio: `2026.11.20-57-e85c910`.

Un CI rojo no produce versión: no existe Release para ese commit.

## Cómo localizar una versión y comprobarla en menos de 2 minutos

1. En GitHub, abrir **Releases**; las versiones candidatas son los *pre-release*, la más reciente arriba.
2. Abrir la versión. El título es la versión; el tag apunta al commit exacto.
3. Descargar `manifest.json` y `SHA256SUMS` y leer el manifiesto:
   - `commit`: commit completo del que sale la versión.
   - `ciRunId`: ejecución de CI; cada elemento de `checks` tiene su `conclusion` (todas `success`) y su enlace.
   - `artifacts`: nombre, tamaño y `sha256` de cada archivo.
   - `migrations`: última migración (`latest`) y la lista con su suma.
4. Descargar el JAR y el `.tar.gz` en la misma carpeta y comprobar las sumas:

```bash
sha256sum -c SHA256SUMS
```

   Resultado esperado: `OK` para los tres archivos (`manifest.json` incluido). Cualquier `FAILED` significa que el archivo no es el que produjo el flujo: no usarlo y avisar al responsable.

## Qué significa `schemaChanges`

`schemaChanges` es **solo informativo**: indica si la última migración de esta versión difiere de la del Release candidato anterior (o `true` en el primero). No decide nada en producción: al publicar, `sarc-deploy` compara `migrations.latest` con la última migración realmente aplicada en el VPS y registra `migrationsApplied[]` en `deploy-history.jsonl`.

## Estado de una versión (derivado, no se mantiene a mano)

El estado de una versión se **deduce** de los datos que ya existen; no hay que anotarlo en ningún sitio:

| Estado | Cómo se reconoce |
|---|---|
| `candidata` | Es un Release *pre-release* y **no** tiene ninguna línea `COMPLETADA` en `deploy-history.jsonl`. |
| `publicada` | Tiene al menos una línea `COMPLETADA` en `/var/lib/sarc/deploy-history.jsonl` del VPS. |
| `retirada` | Después de su publicación hubo otra publicación `COMPLETADA` con otra versión. Sigue consultable. |

Ejemplo para consultar en el VPS: `sudo jq -r 'select(.result=="COMPLETADA") | [.version, .finishedAt] | @tsv' /var/lib/sarc/deploy-history.jsonl`.

## Si falla

- **No aparece el Release de un commit de `main`**: el CI estuvo rojo o el escaneo de secretos de los artefactos encontró algo. Abrir la ejecución de **CI** o **Versión candidata** en la pestaña *Actions*, corregir la causa en un cambio nuevo e integrarlo. No se crea una versión a mano.
- **`sha256sum -c` da `FAILED`**: no usar esos archivos; descargar otra vez y, si persiste, avisar al responsable técnico.
- **Falta algún archivo del Release**: la versión no es utilizable; generar otra.

## Reglas

- Una versión es **inmutable**: no se edita, no se reemplaza ni se mueve su tag. Si algo está mal, se integra una corrección y se genera otra versión.
- El manifiesto no contiene credenciales, valores de configuración ni datos de personas.
- Para publicar una versión en producción, ver `publicar.md`.
