package com.tidsec.sarc_bakend.shared.catalog;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;

/**
 * Puerto publico del inventario (TRD 03.2): los demas modulos resuelven equipos a traves de el, sin
 * acceder a sus entidades ni repositorios. Lo implementa el modulo de inventario. Los resultados se
 * devuelven indexados por la misma clave que envio quien consulta; lo que no se encuentra no aparece.
 */
public interface EquipmentDirectory {

	/** Equipos por serie fisica, incluidos inactivos y eliminados (el historico les pertenece). */
	Map<String, EquipmentReference> findBySerials(Collection<String> serials);

	/** Equipos por identidad externa VERIFICADA (RF-002.10): solo esas son confiables para conciliar. */
	Map<ExternalKey, EquipmentReference> findByVerifiedIdentities(ExternalPlatform platform,
			Collection<ExternalKey> keys);

	Map<UUID, EquipmentReference> findByIds(Collection<UUID> ids);
}
