package com.tidsec.sarc_bakend.shared.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Puerto publico del inventario para los modulos que miden por conector. */
public interface ConnectorDirectory {

	/** Conectores, tambien inactivos, de equipos no eliminados, por nombre de equipo y posicion. */
	List<MeasurableConnector> search(ConnectorQuery query);

	/** Conectores por id, incluidos los de equipos eliminados: el historico les pertenece. */
	Map<UUID, MeasurableConnector> findByIds(Collection<UUID> ids);
}
