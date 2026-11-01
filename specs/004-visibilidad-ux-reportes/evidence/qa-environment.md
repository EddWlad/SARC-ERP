# Entorno QA004

- Windows; Java25.0.3; Node24.18.0; Docker con PostgreSQL18 desechable.
- Excel de escritorio instalado en Program Files/Microsoft Office/root/Office16/EXCEL.EXE; versión/apertura real por comprobar.
- Impeccable instalado, DESIGN/PRODUCT y sidecar existentes. No regenerar identidad.
- Navegador/teclado/contraste: se registrará método real, sin suponer dispositivo físico.
- Storage para automatización: mock con bytes. Proveedor privado QA: pendiente de credenciales autorizadas; no usar credenciales reales/dev para probar.
- Ignore existentes revisados: target/node_modules/dist/build/env/log y salidas locales cubiertas; Prettier/generated y ESLint ignoran artefactos. npm privado, no publicar ni crear npmignore innecesario.
