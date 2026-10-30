# SARC Constitution

## Core Principles

### I. Especificación antes de cambios de producto

Toda funcionalidad nueva o cambio de comportamiento DEBE entrar mediante una especificación en
`specs/` antes de implementarse. Un prompt aislado NO DEBE sustituir esa especificación ni
autorizar por sí solo una modificación del producto. La especificación DEBE describir el
comportamiento elegido y criterios de aceptación comprobables.

La razón es conservar una decisión de producto revisable y evitar reglas dispersas entre
conversaciones y código.

### II. Reglas de negocio explícitas

Ninguna regla de negocio DEBE vivir únicamente en código, un trigger o una función SQL. Toda
regla implementada DEBE estar explicada también en una especificación de `specs/`, incluidos
límites y excepciones. La implementación y su especificación DEBEN poder relacionarse durante
la revisión. Una prueba o un comentario de código NO DEBE sustituir esa especificación.

Una decisión aún no tomada DEBE señalarse como pendiente en la especificación; NO DEBE
resolverse silenciosamente al implementar.

### III. Migraciones inmutables

Todo cambio de esquema DEBE incorporarse mediante una migración nueva. Una migración ya
aplicada NO DEBE reescribirse. Las correcciones posteriores DEBEN conservar la historia de
migraciones y expresarse mediante otra migración.

Así se reproduce la evolución de la base de datos y se evita compartir un número de migración
con efectos diferentes entre instalaciones.

## Decisiones de producto ratificadas

D01–D14 fijan el comportamiento exigido, no certifican cumplimiento del código actual. Sus
identificadores conservan la correspondencia con la auditoría. Los títulos del diagnóstico
anterior NO DEBEN prevalecer sobre las respuestas y reglas ratificadas aquí.

### D01. Correo modificable con continuidad de cuenta

El administrador DEBE poder cambiar el correo conservando UUID, autoría e históricos. El cambio
DEBE comprobar unicidad, quedar auditado y revocar las sesiones de esa cuenta. Una persona que
cambia de correo DEBE continuar con la misma cuenta, sin crear otra identidad por ese motivo.

### D02. Contraseña temporal con vigencia de 60 minutos

La contraseña temporal DEBE vencer a los 60 minutos de su emisión y exigir cambio obligatorio
al primer ingreso posterior. Vencida, NO DEBE permitir autenticarse: el administrador DEBE
emitir otro restablecimiento. Este plazo corresponde a la credencial y NO DEBE confundirse
con el vencimiento de una sesión inactiva.

### D03. Autogestión administrativa sin perder el último administrador

Un administrador DEBE poder desactivar su cuenta o renunciar a su rol con confirmación explícita
si otro administrador activo conserva el acceso. La operación DEBE bloquearse si deja al sistema
sin administración disponible. La protección DEBE garantizar esa disponibilidad, no exigir
siempre la intervención de otra persona.

### D04. UID/PUK literal y comparación explícita de identificadores

El UID/PUK DEBE conservarse literalmente, incluidas mayúsculas y minúsculas; NO DEBE convertirse
a mayúsculas para almacenarlo. Los identificadores originales DEBEN conservarse y su comparación
DEBE seguir una política por clase o proveedor expresada en la especificación correspondiente.
La política de comparación de una serie NO DEBE extenderse automáticamente al UID/PUK.

### D05. Normalización que autoriza vinculación automática

Una coincidencia por normalización DEBE autorizar vinculación automática a una identidad externa
verificada. La equivalencia DEBE ignorar mayúsculas, tildes y espacios repetidos, conforme al
comportamiento ratificado. NO DEBE exigirse confirmación manual únicamente porque la escritura
literal difiera de esa forma normalizada.

Una coincidencia ambigua DEBE conservar la conciliación explícita exigida por las reglas de
identidad del proyecto. Normalizar NO DEBE equivaler a fusionar equipos diferentes.

### D06. Continuidad del conector por posición

Se DEBE conservar el comportamiento vigente: la posición del conector dentro del equipo determina
su continuidad histórica. Cambiar su tipo o reincorporar una posición retirada DEBE conservar el
UUID de esa posición. Retirarla DEBE dejar el conector inactivo y mantener su histórico, sin
borrado físico.

### D07. Reemplazo físico uno a uno

El reemplazo DEBE sustituir un equipo por otro y formar una cadena lineal, conservando identidades
e históricos de ambos. Un equipo NO DEBE tener varios sucesores que declaren reemplazarlo.
Divisiones o ampliaciones, si se incorporan, DEBEN especificarse como operaciones distintas con
relaciones propias. Esta regla se aplica al reemplazo físico, no a corregir datos del mismo equipo.

### D08. Una excepción horaria por fecha con un único intervalo

Se DEBE conservar el comportamiento vigente: cada equipo DEBE admitir como máximo una excepción
por fecha, que indique cierre completo o un único intervalo de apertura y cierre. La excepción
DEBE prevalecer sobre el horario habitual. Esta ratificación NO DEBE interpretarse como
autorización para añadir múltiples intervalos a una excepción.

### D09. Solo la primera hoja del Excel se importa

Se DEBE conservar el comportamiento vigente: solo la primera hoja del libro DEBE usarse como
entrada. Las hojas posteriores NO DEBEN importarse automáticamente. El orden de las hojas DEBE
formar parte del contrato explicado en la especificación de importación.

### D10. Revisión humana de energía superior al umbral

Una energía normalizada superior al umbral de 1.000 kWh por sesión DEBE tratarse como observación
revisable, no como rechazo definitivo basado solo en ese umbral. La decisión humana DEBE quedar
trazada; una excepción admitida DEBE conservar justificación y aprobación antes de computarse
como dato oficial. La aprobación NO DEBE omitir las demás reglas de coherencia.

### D11. Redondeo por lectura antes de comparación y agregación

Se DEBE conservar el comportamiento vigente: cada lectura energética DEBE normalizarse a tres
decimales de kWh mediante `HALF_UP` antes de comparar correcciones y sumar indicadores.
La comparación de lecturas y los totales oficiales DEBEN utilizar esos valores normalizados.
La especificación DEBE declarar que se suma después de redondear cada lectura, no al revés.

### D12. Informes nuevos con nombres actuales del equipo

Todo informe nuevo, incluso sobre un período pasado, DEBE utilizar nombres actuales de los equipos.
La consulta histórica DEBE permitir distinguir la etiqueta de la fecha del hecho y la identificación
vigente, mediante su presentación o selección de vista especificada. Esta regla NO DEBE modificar
las copias de informes ya emitidos y archivados.

### D13. Mes corriente como período inicial

Se DEBE conservar el comportamiento vigente: tablero de consumo e histórico de sesiones DEBEN
iniciar con el mes en curso, incluido hoy. El rango DEBE abarcar desde el primer día del mes hasta
el día siguiente a hoy, con fin exclusivo, usando el día operativo. Los accesos rápidos DEBEN
recalcularse cuando cambia ese día.

### D14. Top 5 con empates incluidos

El ranking DEBE ordenarse por energía e incluir todos los equipos empatados con el que ocupa
el quinto lugar. Por ese motivo PUEDE contener más de cinco equipos y DEBE indicarlo en su
presentación. El UUID NO DEBE decidir la exclusión de un equipo empatado en ese límite.
Si hay menos de cinco equipos, DEBEN mostrarse los disponibles.

## Flujo de desarrollo y revisión

1. Antes de modificar comportamiento, la especificación en `specs/` DEBE identificar las
   decisiones constitucionales aplicables y describir escenarios verificables.
2. La revisión DEBE comprobar que las reglas en backend, frontend y base de datos coinciden con
   la especificación y esta constitución. Una validación SQL adicional NO DEBE crear una política
   de producto que la especificación no describa.
3. Un cambio de esquema DEBE revisarse como migración nueva, comprobando que no se alteran
   migraciones ya aplicadas.
4. Las diferencias entre implementación y estas reglas DEBEN registrarse como trabajo pendiente
   en la especificación correspondiente. Ratificar esta constitución NO DEBE presentarse como
   prueba de implementación o cumplimiento actual.
5. Plantillas y comandos DEBEN consultar la constitución vigente en ejecución. Esta ratificación
   NO DEBE usarse para editar automáticamente código, pruebas, plantillas ni documentación
   dependiente fuera del alcance de la operación solicitada.

## Governance

Esta constitución es la referencia normativa de las reglas no negociables de SARC. Se ratifica
con las decisiones de `documents/Reglas que no se negocian en SARC.txt` y el contexto de
`documents/auditoria-decisiones.md`. Un supuesto del código o ADR previo que contradiga estas
decisiones NO DEBE sustituirlas. Los demás requisitos de PRD, TRD y ADR conservan su aplicación
cuando no exista contradicción.

Una enmienda DEBE identificar regla afectada, propuesta y motivo, obtener aprobación explícita
del responsable del producto y registrar el impacto sobre especificaciones y datos existentes.
Si cambia comportamiento, su implementación DEBE tramitarse en `specs/`; modificar la constitución
NO DEBE ejecutar por sí mismo esa implementación.

La versión DEBE seguir SemVer: MAJOR para retirar o redefinir principios de forma incompatible;
MINOR para añadir principios o ampliar materialmente reglas; PATCH para aclaraciones sin cambio
de significado. La fecha original de ratificación DEBE conservarse. Cada modificación DEBE
actualizar fecha de enmienda y versión según su alcance.

Toda revisión de especificaciones y cambios de comportamiento DEBE declarar conformidad con esta
constitución o señalar puntos pendientes. Una excepción a una regla no negociable DEBE tratarse
mediante enmienda aprobada; NO DEBE introducirse como atajo en la implementación.

**Version**: 1.0.0 | **Ratified**: 2026-10-29 | **Last Amended**: 2026-10-29
