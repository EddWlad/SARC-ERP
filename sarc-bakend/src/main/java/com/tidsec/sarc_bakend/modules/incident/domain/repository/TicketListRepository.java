package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.util.List;
import java.util.UUID;

/** Puerto de consulta del listado: devuelve los ids de la pagina, en orden, y el total. Solo lectura. */
public interface TicketListRepository {

	IdPage find(TicketListQuery query);

	/** Los ids de una pagina y el total de tickets que cumplen los filtros. */
	record IdPage(List<UUID> ids, long total) {
	}
}
