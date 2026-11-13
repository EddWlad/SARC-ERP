package com.tidsec.sarc_bakend.shared.catalog;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto publico del inventario para los puntos de carga KIA (feature 008). Lo implementa Inventario. Se
 * separa de {@link EquipmentDirectory} porque este resuelve identidades externas para Consumo y Uptime,
 * mientras que un punto KIA se resuelve por su Codigo asignado y se clasifica por alcance, uso y estado.
 */
public interface KiaPointDirectory {

	/**
	 * Equipos por Codigo asignado, de cualquier red, tipo y estado: el historico les pertenece y el alcance
	 * se decide con {@link KiaPointReference#inScope()}. El codigo se compara en forma literal, sin los
	 * espacios exteriores (politica D04, equivalente a la normalizacion de D05 para seis digitos). Una lista
	 * con mas de un equipo es un codigo ambiguo. Lo que no se encuentra no aparece.
	 */
	Map<String, List<KiaPointReference>> findByAssignedCodes(Collection<String> codes);

	/** Equipos por identidad, tambien inactivos y eliminados. Lo que no se encuentra no aparece. */
	Map<UUID, KiaPointReference> findByIds(Collection<UUID> ids);

	/**
	 * Puntos de carga KIA activos o inactivos cuyo codigo o nombre contiene el texto, sin distinguir
	 * mayusculas ni tildes, ordenados por nombre. Los eliminados no se ofrecen: se recuperan desde Inventario.
	 */
	List<KiaPointReference> search(String text, int limit);

	/** Puntos de carga KIA activos que ya existian en ese instante. */
	List<KiaPointReference> inScope(Instant at);

	/**
	 * Todos los puntos de carga KIA, tambien inactivos y eliminados, ordenados por nombre. Lo usa el alcance de la
	 * disponibilidad estimada para listar lo que queda fuera con su motivo (FR-045).
	 */
	List<KiaPointReference> allKiaPoints();
}
