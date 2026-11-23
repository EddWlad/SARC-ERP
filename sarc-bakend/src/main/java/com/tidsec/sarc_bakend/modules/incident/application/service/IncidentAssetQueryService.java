package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAssetDirectory;

/** Equipos activos para el formulario de creacion: solo los editores crean tickets. Nunca devuelve UID/PUK ni OCPP. */
@Service
@PreAuthorize(IncidentPermissions.EDITORS)
@Transactional(readOnly = true)
public class IncidentAssetQueryService {

	/** Tope de resultados de una busqueda del formulario. */
	public static final int MAX_RESULTS = 50;

	private final IncidentAssetDirectory assets;

	IncidentAssetQueryService(IncidentAssetDirectory assets) {
		this.assets = assets;
	}

	public List<IncidentAsset> search(String text, int limit) {
		return assets.search(text, Math.min(Math.max(limit, 1), MAX_RESULTS));
	}
}
