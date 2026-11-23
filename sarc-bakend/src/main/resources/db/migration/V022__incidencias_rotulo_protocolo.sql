-- P-02 (FR-051a): el responsable del producto confirmo que el protocolo vigente es la version 2.0, aprobada por gerencia.
-- Solo cambia el rotulo de la version inicial de referencias: ningun valor de tiempo ni de prioridad se modifica, asi
-- que los episodios y los informes ya emitidos siguen igual (P-03).
UPDATE inc_reference_version
SET label = 'Protocolo de monitoreo firmado, versión 2.0'
WHERE code = 'PROTOCOLO-FIRMADO-1';
