package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.FutureInstantPolicy;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.WindowRepository;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;

/**
 * Unica via de escritura de ventanas: captura, cierre, correccion e importacion pasan por aqui. Bloquea los
 * puntos en orden, valida el alcance y el instante futuro, rechaza duplicados y solapamientos contra las
 * ventanas no anuladas (FR-014, FR-015) y traduce las violaciones de las barreras de la base a los mismos
 * codigos. La base es la ultima barrera, no la unica (principio II).
 */
@Component
class WindowWriteGuard {

	private final WindowRepository windows;
	private final KiaPointDirectory points;
	private final Clock clock;

	WindowWriteGuard(WindowRepository windows, KiaPointDirectory points, Clock clock) {
		this.windows = windows;
		this.points = points;
		this.clock = clock;
	}

	Instant now() {
		return Instant.now(clock);
	}

	/**
	 * Toma el bloqueo de cada punto en orden ascendente de identificador, para que dos operaciones que tocan
	 * varios puntos no se esperen una a la otra (R7).
	 */
	void lock(Collection<UUID> equipmentIds) {
		equipmentIds.stream().distinct().sorted().forEach(windows::lockEquipment);
	}

	/**
	 * El punto debe existir y ser un punto de carga KIA. Un punto inactivo se acepta (el historico le
	 * pertenece); uno eliminado se rechaza hasta recuperarlo desde Inventario.
	 */
	KiaPointReference requireWritable(UUID equipmentId) {
		KiaPointReference point = points.findByIds(List.of(equipmentId)).get(equipmentId);
		if (point == null) {
			throw new NotFoundException("POINT_NOT_FOUND", "El punto de carga no existe");
		}
		if (!point.inScope()) {
			throw new BusinessRuleException("NOT_KIA_CHARGING_POINT",
					"El equipo no es un punto de carga KIA; solo se registran intermitencias de puntos KIA");
		}
		if (point.adminStatus() == AdministrativeStatus.DELETED) {
			throw new BusinessRuleException("DELETED_EQUIPMENT",
					"El punto esta eliminado; recuperelo desde Inventario antes de registrar ventanas");
		}
		return point;
	}

	void requireNotFuture(Instant instant) {
		if (FutureInstantPolicy.isFuture(instant, now())) {
			throw new BusinessRuleException("WINDOW_FUTURE_INSTANT",
					"Un instante no puede ser posterior al minuto en curso");
		}
	}

	/**
	 * No existe otra ventana no anulada identica ni solapada con {@code [start, end)}; un fin nulo es una
	 * ventana abierta, que ocupa desde su inicio hasta que se cierra. {@code excludeId} deja fuera la propia
	 * ventana al cerrarla o corregirla.
	 */
	void requireFree(UUID equipmentId, Instant start, Instant end, UUID excludeId) {
		windows.findIdentical(equipmentId, start, end, excludeId).ifPresent(found -> {
			throw duplicate(found);
		});
		List<IntermittencyWindow> overlapping = windows.findOverlapping(equipmentId, start, end, excludeId);
		if (!overlapping.isEmpty()) {
			throw overlap(overlapping.getFirst());
		}
	}

	/** Guarda y traduce una violacion de las barreras de la base al error de negocio que corresponde. */
	IntermittencyWindow save(IntermittencyWindow window) {
		try {
			return windows.save(window);
		}
		catch (DataIntegrityViolationException ex) {
			String detail = String.valueOf(ex.getMostSpecificCause().getMessage());
			if (detail.contains("ex_itm_window_overlap")) {
				throw new ConflictException("WINDOW_OVERLAP", "La ventana se solapa con otra del mismo punto");
			}
			if (detail.contains("uq_itm_window_identity")) {
				throw new ConflictException("WINDOW_DUPLICATE", "Ya existe una ventana identica en ese punto");
			}
			throw ex;
		}
	}

	private static ConflictException duplicate(IntermittencyWindow found) {
		return new ConflictException("WINDOW_DUPLICATE", "Ya existe una ventana identica en ese punto",
				Map.of("windowId", found.getId().toString()));
	}

	private static ConflictException overlap(IntermittencyWindow found) {
		String message = found.isOpen()
				? "El punto tiene una ventana abierta desde " + found.getStartAt()
						+ "; cierrela antes de registrar otra posterior"
				: "La ventana se solapa con otra del mismo punto";
		return new ConflictException("WINDOW_OVERLAP", message, Map.of("windowId", found.getId().toString()));
	}
}
