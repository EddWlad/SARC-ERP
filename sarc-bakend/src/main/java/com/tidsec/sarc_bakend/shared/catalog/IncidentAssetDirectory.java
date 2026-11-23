package com.tidsec.sarc_bakend.shared.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto publico del inventario para Incidencias (feature 010). Lo implementa Inventario. Se separa de
 * {@link EquipmentDirectory} (identidades externas para Consumo y Uptime) y de {@link KiaPointDirectory} (puntos
 * KIA): aqui se necesitan equipos de cualquier red y tipo con su ubicacion, su enlace de Maps y sus conectores.
 */
public interface IncidentAssetDirectory {

	/**
	 * Equipos activos cuyo nombre o codigo contiene el texto, sin distinguir mayusculas ni tildes, ordenados por
	 * nombre. Un texto vacio devuelve los primeros. Nunca devuelve inactivos ni eliminados.
	 */
	List<IncidentAsset> search(String text, int limit);

	/** Equipos por identidad, tambien inactivos y eliminados: el historico les pertenece. Lo que no existe no aparece. */
	Map<UUID, IncidentAsset> findByIds(Collection<UUID> ids);
}
