# Implementation Plan: Código asignado según estado de uso KIA

**Branch**: `main` (rama Git real; el resolver identifica la feature como `001-codigo-uso-kia`)
**Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)
**Input**: `specs/001-codigo-uso-kia/spec.md`

## Summary

Hacer opcional el código solo para puntos KIA FREE. Mantener obligatorio en BLOCKED y
DISCONNECTED, conservar códigos al actualizar y validar siempre en dominio y persistencia.
La implementación debe ampliar el contrato de ausencia de código hasta sus lectores.
Esta fase solo documenta diseño; no ejecuta migraciones ni modifica fuentes.

Alcance aclarado tras análisis A1–A2: FR-012 protege contra efectos automáticos, no inmoviliza
el código frente a correcciones explícitas ya autorizadas. FR-011 permite únicamente adaptar
lectura, selección y presentación de RF-003 a la ausencia de código; no cambia su negocio.

### Estado de aceptación observado por lectura

| Criterio | Estado actual | Trabajo necesario |
| --- | --- | --- |
| AC1: alta FREE sin código | No cumple: política, NOT NULL y formulario lo impiden | Nueva capacidad en dominio, esquema y formulario; prueba directa/importación |
| AC2: alta BLOCKED sin código | Ya rechaza; mensaje actual habla de formato | Mantener rechazo, explicar obligatoriedad y añadir caso por estado |
| AC3: BLOCKED 654321 → FREE | Conserva si el request mantiene código; importación ya mezcla vacíos con actual | Asegurar conservación también al omitir campo en actualización directa; nuevos escenarios |
| AC4: FREE sin código → BLOCKED | El estado inicial no es creable hoy | Nueva transición; rechazo sin código, aceptación con válido |
| Formato y duplicados | Ya existen patrón, consulta global y UNIQUE | Retenerlos; no comparar ausencia como identificador |
| Otras redes, tipos e históricos | Ya tienen políticas y vínculos por UUID | Pruebas de no regresión; no reescribir datos |

Ninguno de estos estados equivale a una prueba ejecutada en esta fase.

## Technical Context

**Language/Version**: Java 25; Angular 22/TypeScript.
**Primary Dependencies**: Spring Boot 4.1.1, Security, Data JPA/Hibernate, Jakarta Validation,
Maven; Angular Signals/Signal Forms, Material y SCSS.
**Storage**: PostgreSQL 18 en Docker Compose; Flyway V001–V007 actuales.
**Testing**: JUnit, AssertJ, MockMvc, Testcontainers, ArchUnit y OpenApiContractTest;
Vitest y pruebas de formulario Angular.
**Target Platform**: aplicación web existente; backend y SPA, despliegue del proyecto.
**Project Type**: monolito modular por funcionalidades; api → application → domain ← infrastructure.
**Performance Goals**: no introducir consultas por campo o por pulsación; conservar objetivos del
TRD sin inventar un SLA para esta feature.
**Constraints**: solo FREE KIA punto admite ausencia; sin generación nueva, pérdida de códigos,
cambios de permisos, métricas o migraciones aplicadas.
**Scale/Scope**: RF-002, importación de equipos y adaptación de lectores compartidos con RF-003;
sin nuevos módulos, proveedores ni procesos.

**Mapeos reales**: RF-002 usa records con `toCommand()` y `from(...)`; ni el pom ni las fuentes
revisadas configuran MapStruct. ADR 0001 documenta ese enfoque. Conservar esos mapeos y DTOs;
no introducir MapStruct ni una conversión masiva solo por esta feature.

## Constitution Check

| Gate | Antes de investigación | Después de diseño |
| --- | --- | --- |
| I: spec antes de comportamiento | PASS: spec activa y checklist 16/16 | PASS: diseño vinculado a FR-001–FR-012 |
| II: reglas explícitas | PASS: FREE/BLOCKED/DISCONNECTED definidos | PASS: matriz en data-model y contratos; SQL no añade reglas |
| III: migraciones nuevas | PASS: no editar V004 | PASS: proponer V008 nueva, sin actualizaciones de filas |
| D01–D14 y datos históricos | PASS: fuera de alcance funcional | PASS: se preservan UUID, referencias, UID/PUK y archivos emitidos |
| Alcance del comando | PASS | PASS: solo documentos de diseño |

No hay excepciones constitucionales ni decisiones bloqueantes pendientes.
La ausencia es una adaptación técnica necesaria para la spec; no autoriza borrar códigos existentes.

## Project Structure

### Documentation (this feature)

```text
specs/001-codigo-uso-kia/
  spec.md
  plan.md
  research.md
  data-model.md
  contracts/inventario.md
  quickstart.md
  checklists/requirements.md
```

`tasks.md` se generará con speckit-tasks; no corresponde a esta fase.

### Source Code (repository root)

```text
sarc-bakend/src/main/java/com/tidsec/sarc_bakend/
  modules/inventory/{api,application,domain,infrastructure}/
  modules/consumption/{api,infrastructure}/
  shared/catalog/
sarc-bakend/src/main/resources/db/migration/
sarc-bakend/src/test/java/com/tidsec/sarc_bakend/
sarc-frontend/src/app/
  features/inventory/{domain,presentation}/
  features/consumption/presentation/
  core/api/generated/
```

**Structure Decision**: reutilizar capas y componentes existentes. La política pertenece a
EquipmentClassification; Equipment aplica la regla en altas, ediciones y borradores de importación.
No añadir endpoint de estado de uso, repositorio paralelo, motor de importación ni framework.

## Diseño mínimo y secuencia de implementación posterior

### 1. Esquema y dominio

- Crear, durante implementación, `V008__codigo_kia_segun_uso.sql` si V008 sigue libre;
  si hubo nuevas migraciones, usar el siguiente número. No modificar V004 ni ninguna aplicada.
- Permitir SQL NULL en assigned_code, condicionado por CHECK explícito a KIA/CHARGING_POINT/FREE.
  Fuera de ese caso exigir valor no nulo. Todo código KIA informado mantiene patrón;
  los demás equipos conservan código igual a serie. Ver data-model.
- Conservar UNIQUE global normal; no índice parcial por estado ni NULLS NOT DISTINCT.
  Múltiples ausencias no son duplicados. No ejecutar UPDATE, backfill ni regeneración.
- Ajustar nulabilidad JPA de Equipment.assignedCode y pasar estado final a
  EquipmentClassification.resolveAssignedCode. Validar estado aplicable antes de decidir código.
- Alta: ausencia normalizada a null solo para FREE KIA punto. No devolver serie como sustituto.
- Edición: resolver omisión/vacío con el código actual antes de validar el resultado, únicamente
  para el código propio KIA; nunca mezclar indiscriminadamente otros campos de un PUT.
  Conservar código previo si existe; si no existe y pasa a BLOCKED/DISCONNECTED, rechazar.
- Si se proporciona explícitamente un código nuevo en una corrección ya autorizada, conservar
  su tratamiento previo y validar formato/unicidad/auditoría. No reutilizar la conservación por
  omisión para ignorar esa corrección ni habilitar nuevas operaciones de borrado o reasignación.
- Preservar generación por serie ya autorizada para las otras redes/tipos.
- Incorporar pruebas de política, entidad y SQL antes de considerar cerrado el cambio.

### 2. Aplicación, unicidad, auditoría e importación

- EquipmentService.register/update mantienen transacciones, autorización, versiones e identidad.
- EquipmentIdentifiers sigue descartando al propio UUID. EquipmentRepositoryAdapter.findHoldersOf
  debe añadir el predicado de código solo cuando no sea null, como hace con UID/PUK.
  No convertir ausencia en IS NULL que encuentre a otros FREE sin código.
- Mantener UNIQUE como cierre ante carreras; verificar una sola aceptación si dos altas compiten
  por el mismo código informado.
- Sustituir construcción de auditoría intolerante a null en EquipmentService.register: el Map.of
  actual contiene assignedCode. En EquipmentImportProcessor.create existe el mismo patrón.
  Omitir esa entrada cuando no hay código; no almacenar un código ficticio. EquipmentChanges.diff
  ya transforma ausencia en texto vacío para el cambio auditado: conservar convención.
- EquipmentRowEvaluator utiliza Equipment.draft tanto para revisar como para aplicar.
  ProposedEquipment.detailsOver ya conserva código de celda vacía: reutilizarlo.
  Mantener evaluación final, aprobación de MODIFIED y detección de cambios concurrentes.
- EquipmentImportProcessor ya evita añadir claves de código cuando son null: comprobarlo con
  dos filas FREE sin código y códigos duplicados informados.
- Actualizar hint de EquipmentSheetColumns para indicar obligatoriedad según uso; conservar
  columnas, formato, primera hoja y el flujo de carga.

### 3. DTOs y contrato publicado

- CreateEquipmentRequest y UpdateEquipmentRequest ya permiten ausencia por @Size sin @NotBlank.
  No añadir validación incondicional ni duplicar la política contextual en el controlador.
- Marcar assignedCode nullable donde se expone: EquipmentResponse, EquipmentSummaryResponse y
  EquipmentRefResponse de consumo. Verificar coherencia de EquipmentView, EquipmentReference y
  sus adaptadores sin cambiar IDs ni estructura de módulos.
- Mantener mapeos locales toCommand/from. Regenerar OpenAPI con la prueba existente y después
  tipos Angular generados. No editar a mano archivos generados para ocultar divergencias.
- Conservar rutas, permisos y formatos de error existentes; detalles en contracts/inventario.md.

### 4. Angular y lectores de reportes

- Reutilizar equipment-rules.ts para determinar obligatoriedad: ownCode y uso distinto de FREE
  (BLOCKED/DISCONNECTED); mantener validación independiente del estado de uso obligatorio.
- Mantener visible el campo KIA; required condicional y pattern solo cuando hay contenido.
  Vacío/espacios FREE válidos, valores no vacíos inválidos siguen bloqueando el formulario.
- No limpiar el modelo al cambiar a FREE. initialFormValue y conversión actual ya conservan
  valor cargado; añadir pruebas de ida/vuelta y omisión.
- Propagar nulabilidad usando aliases de OpenAPI de equipment.ts, sin DTOs cliente paralelos.
- Adaptar solo presentación de ausencia en inventario y lectores de consumo: campo sin código
  puede mostrarse vacío o con el marcador visual ya usado para ausencias; nunca persistirlo.
  Evitar etiquetas como “Nombre · null” en equipment-filter.ts.
- InventoryPdfRenderer e InventoryExcelRenderer usan List.of con assignedCode: convertir
  ausencia a texto vacío antes de construir filas. No alterar el esquema de columnas.
- ConsumptionLabels.code retorna actualmente el código sin normalizar null; evitar null en
  filas List.of de ConsumptionExcelRenderer y PDF. Ajustar alcance textual de
  ConsumptionReportService para que no produzca “(null)”.
- Son adaptaciones de lectura al nuevo valor permitido, no cambios de cálculo en RF-003.
  Su autorización está delimitada en FR-011: preservar también identidad, conciliación,
  filtros, permisos y archivos ya emitidos; no incorporar funcionalidades de consumo adicionales.

### 5. Verificación y salida

Ejecutar la matriz de quickstart, contrato OpenAPI, arquitectura y suites relevantes.
No dar por cumplido un criterio por lectura estática o por pasar solo la prueba del formulario.
Mantener revisión focalizada: no aplicar en paralelo otras decisiones de la constitución.

## Riesgos y mitigaciones

| Riesgo | Mitigación y prueba exigida |
| --- | --- |
| NULL admitido accidentalmente en otras redes/estados | CHECK con ramas booleanas y NOT NULL explícitos; inserciones SQL negativas |
| SQL CHECK admite UNKNOWN | No depender solo de una expresión regex o igualdad con null; exigir presencia en cada rama obligatoria |
| Omisión del PUT borra código | Resolver valor final y probar omisión, blanco y transición con 654321 |
| Map.of/List.of falla por ausencia | Pruebas de alta manual/importada, auditoría, PDF y Excel con código null |
| Cliente antiguo espera string | Desplegar backend/contrato y frontend compatibles coordinadamente; validar tipos generados |
| Revertir esquema después de aceptar NULL | No restaurar NOT NULL sin tratar datos legítimos; corrección hacia adelante, nunca códigos inventados |
| Consumo identifica por código | Confirmar que conserva UUID, serie e identidades externas; probar importación y KPIs de un FREE sin código |
| Lotes cambian clasificaciones/conteos | Corregir fixture FREE sin código hoy esperado INVALID; preservar intención con BLOCKED y agregar caso nuevo |
| Historia o permisos alterados | Comparar IDs, sesiones/revisiones, relaciones y archivos previos; probar roles de consulta denegados |

## Complexity Tracking

Sin violaciones constitucionales. No se incorporan frameworks ni abstracciones nuevos.
