# Evidencia sintética — Feature 003

Sin datos del usuario. Usuarios QA, equipos y sesiones creados en PostgreSQL desechable.

- `preview-high.png`: archivo Enerlink de 1.500 kWh sin término; aviso neutral y contador transversal.
- `ranking-desktop.png`: seis miembros en Top 5 por empate, posiciones compartidas.
- `review-history.png`: decisión vigente rechazada y acceso al historial; el contenido del diálogo es desplazable.
- `review-mobile.png`: diálogo a 390×844, controles y botones accesibles.
- `dashboard-final.png`: 8 oficiales / 2.040 kWh; aprobada sobre umbral como subconjunto y rechazada aparte. Captura previa al ajuste mínimo de singular/plural.
- `consumo-sandbox.xlsx`: descarga real del recorrido. Ocho oficiales, 2.040 kWh; una aprobada de 1.500 kWh y su justificación; ranking completo 1,2,3,4,4,4,7.
- `consumo-sandbox.pdf`: descarga original del mismo recorrido, conservada intacta como evidencia de QA. La revisión visual encontró que las excepciones quedaban tras el diario.
- `consumo-render-final.pdf`: renderer final con fixture sintética de los mismos totales y ranking, período mensual y branding real. Bloque energético trasladado al resumen inicial y comprobado por prueba automatizada y render visual.
- `axe-results.json`: AXE sobre instantáneas DOM reales en JSDOM, sin violaciones; limitaciones y comprobación incompleta identificadas dentro del archivo.
- `contrast.json`: contraste focal calculado con colores CSS del diálogo oscuro real.

Resultados, comandos, cobertura y límites completos en [quickstart.md](../quickstart.md). Las carreras, versiones obsoletas, correcciones y órdenes de aprobación se verificaron mediante dominio/API; no se presentan como recorridos manuales completos.
