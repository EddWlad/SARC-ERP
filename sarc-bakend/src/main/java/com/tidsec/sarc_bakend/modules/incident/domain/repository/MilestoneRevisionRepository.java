package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneRevision;

/** Puerto de las revisiones de hitos: solo se anexan filas (research R3). */
public interface MilestoneRevisionRepository {

	MilestoneRevision save(MilestoneRevision revision);

	/** Las revisiones de un episodio, de la mas antigua a la mas reciente. */
	List<MilestoneRevision> findByEpisode(UUID episodeId);

	/** Las revisiones de varios episodios en una consulta, sin orden garantizado entre episodios. */
	List<MilestoneRevision> findByEpisodes(java.util.Collection<UUID> episodeIds);
}
