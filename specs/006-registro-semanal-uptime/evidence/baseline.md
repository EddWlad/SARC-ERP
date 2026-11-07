# Baseline006 — 2026-11-05

Rama codex/006-registro-semanal-uptime; base9739aca. Java Temurin25.0.3, Maven Wrapper3.10.0, npm11.16.0, Docker Engine26.1.1. V016 libre. Ignore Git/Docker/ESLint/Prettier verificado; frontend privado, no publicacion npm/Terraform/Helm. Sin cambios necesarios.

Cambios ajenos preservados: .claude/launch.json, .specify/integration.json, sarc-frontend/.impeccable/design.json y herramientas no versionadas. No archivos reales ni credenciales.

Baseline dirigida: mvnw.cmd -o -Dtest=MeasurementRulesTest,PeriodRulesTest test; exit0,33 pruebas sin fallos. Ejecucion con acceso ampliado para Maven/Docker; PostgreSQL sera desechable, Compose no se inicia. No es prueba de cumplimiento006.

## SHA-256 migraciones originales

- V001__init_roles_usuarios.sql: ccef1a4b1de3c7de5394d731b02890de9f35904d1fe0be33612fee54f278b0e3
- V002__auditoria.sql: 74157c76512b7ec81b9fca18fea5473557693300ee065dd2bb6d5742138d5e00
- V003__spring_session.sql: aedaf7e519a92d39a29d8c42991224a65332bba6fe3c228d3b6d43233382a5dd
- V004__inventario_equipos.sql: 774e8127c25f1ca0d790795a500fae2dd6837ab3f2db38b6fb221d43032a6a11
- V005__consumo_energia.sql: 3e802a889d549b0945b891954cae7ca5139e0dc73232c167bc8d6182b5759059
- V006__importacion_equipos.sql: 84b5dca7b89ea96a90f6061547ff446d50ab6b8aeecb171ea32cce2eca2f47a9
- V007__correcciones_qa.sql: 1fb6d16792deed9249e7b6ce60382dbe02a7330e7f1e1097c35ba76e2f6a618a
- V008__codigo_kia_segun_uso.sql: 3ff27c0da7e916353dc5512f0f6d728137f69fd3a0e5103802986dfd30c57337
- V009__vinculacion_enerlink_sesiones.sql: d6855eb1bdf657fef271b9e12fd8a80161edb29da65d461c25e01835f60557f7
- V010__revision_energia.sql: 43478a5f5bd03a57f8468d7cd10a855e004ce901d099ed23585d5cbd0c4212df
- V011__visibilidad_reportes.sql: 7d702719026b09980966220b0e47938a6023e68cfecdfa745bbdc3345090bccc
- V012__indice_sesiones_recientes.sql: f238db8a3736b32398b4701ceb7d73dff84971b4585dcfa7c4805ecb1568f979
- V013__uptime_disponibilidad.sql: 68a52725ee59e3b85d568c99c8991240fc3e39168cb201775c04d9f5272ad089
- V014__uptime_informe_semanal.sql: e567ff0b97d62205f8f05616c575abea7c3e42b004f619a9f58c8ac2a305ad69
- V015__uptime_conector_propuesto.sql: 89ef0b2721301ea7ac46e1dc4fe9a64d4c768c0c2e6cdbd4ff202646f1725479
