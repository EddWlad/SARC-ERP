package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;

/**
 * Busca puntos de carga KIA por codigo o nombre. Sin autorizacion de editores: la captura lo usa para elegir el
 * punto y el filtro del dashboard, que consultan todos los roles (FR-032). El codigo ya se muestra a todos en
 * el Top 10 (FR-034), asi que la busqueda no revela nada nuevo.
 */
@Service
@Transactional(readOnly = true)
public class KiaPointQueryService {

	private final KiaPointDirectory points;

	KiaPointQueryService(KiaPointDirectory points) {
		this.points = points;
	}

	/** Solo puntos dentro del alcance del modulo: nunca una electrolinera ni un equipo de otra red. */
	public List<KiaPointReference> search(String text, int limit) {
		return points.search(text, limit).stream().filter(KiaPointReference::inScope).toList();
	}
}
