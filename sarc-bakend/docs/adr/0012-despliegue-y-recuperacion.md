# ADR 0012 — Despliegue seguro y recuperación (Feature 007)

- **Estado**: aceptado (diseño; la puesta en producción real queda sujeta a autorización explícita).
- **Fecha**: 2026-11-07
- **Contexto**: Feature 007 (`specs/007-despliegue-seguro-cicd/`). Complementa el TRD §17–18 y no modifica ninguna regla de negocio (D01–D14).

## Contexto

SARC es un monolito modular (Spring Boot 4 + Angular 22) con PostgreSQL 18 y archivos en Cloudinary. Se prepara su paso a un VPS de Contabo con un proceso de publicación verificable, autorizado y recuperable. El TRD describe un objetivo de despliegue más amplio que el que justifica el tamaño actual del proyecto y algunas decisiones de producto posteriores lo adaptan.

## Decisión: adaptaciones respecto al TRD

| Tema | TRD | Decisión de esta feature | Motivo |
|---|---|---|---|
| Servidor | Dimensionamiento propio | VPS Contabo 6 vCPU / 12 GB / 200 GB SSD **como referencia inicial**, sin garantía de rendimiento | Es la configuración contratable; el rendimiento (30 usuarios, p95 2 s) se mide en el VPS real |
| Respaldo del proveedor | Retirado | **Auto Backup de Contabo** (USD 4/mes) como complemento: reconstruye el servidor completo; no se usa para recuperar la base ni cubre Cloudinary | Decisión aprobada en la aclaración Q2 |
| Copia independiente | Almacenamiento externo privado | **Copia lógica cifrada en un computador local autorizado**, descargada por el propio computador (*pull*) | No se contrata otro proveedor de almacenamiento |
| Pérdida máxima de datos | 1 h con archivado continuo | **24 h** con copia lógica diaria (`pg_dump -Fc`, cifrada con `age`) y copia previa a cada publicación | Aclaración Q3: el archivado continuo es desproporcionado |
| Salud pública | Varias rutas de actuator | Solo `/actuator/health/readiness` (`UP`/`DOWN`, incluye base de datos); el resto, `404` en Nginx y `ADMINISTRADOR` en la aplicación | Mínima exposición |
| Comprobación posterior | Prueba de humo con Swagger | API con cuenta de verificación y reporte de verificación; Swagger está desactivado en `prod` | Menor superficie; la prueba de navegador se hace a mano en la primera publicación |
| Certificado | Dominio | **Let's Encrypt `shortlived` para IP** (Certbot ≥ 5.4), renovación automática, aviso a 72 h y bloqueo de publicación | Aclaración Q1: IP pública sin dominio; el dominio se podrá incorporar sin pérdida de datos |

## Consecuencias

- La infraestructura vive como plantillas y scripts sin valores reales en `deploy/`; la guía de operación, en `docs/operacion/`.
- La aplicación recibe solo tres cambios pequeños: Cloudinary obligatorio en `prod`, *readiness* con base de datos y patrón de registro con `traceId`. No hay migraciones nuevas (principio III de la constitución).
- La publicación a producción exige aprobación en el entorno `production` de GitHub y comprobaciones previas (incluida una copia local verificada de menos de 24 h y la prueba de recepción de avisos).
- El objetivo de 24 h no se presenta como garantizado: se vigila con recibos de descarga, avisos y bloqueo de publicación, y se ensaya la restauración cada mes y tras cada cambio crítico.
