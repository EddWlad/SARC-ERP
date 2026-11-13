package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.WindowDetail;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.WindowView;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.WindowRepository;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/**
 * Lectura del historial de ventanas con proyeccion por rol (FR-005): quien no es editor solo ve las
 * CONFIRMED, sin motivos de revision, y sus parametros no amplian esa proyeccion.
 */
@Service
@Transactional(readOnly = true)
public class WindowReadService {

	private final WindowRepository windows;
	private final KiaPointDirectory points;
	private final IntermittencyReadAccess access;
	private final Clock clock;

	WindowReadService(WindowRepository windows, KiaPointDirectory points, IntermittencyReadAccess access,
			Clock clock) {
		this.windows = windows;
		this.points = points;
		this.access = access;
		this.clock = clock;
	}

	/** Un editor ve todos los estados que pida (todos si no pide ninguno); los demas, solo CONFIRMED. */
	public PageResult<WindowView> list(Collection<UUID> equipmentIds, Instant from, Instant to,
			Collection<WindowState> states, PageQuery page) {
		boolean operational = access.canReadOperational();
		Collection<WindowState> visible = operational ? states : List.of(WindowState.CONFIRMED);
		Instant now = Instant.now(clock);
		return windows.search(equipmentIds, from, to, visible, page).map(window -> view(window, operational, now));
	}

	/**
	 * Detalle de una ventana. Quien no es editor solo ve las CONFIRMED: ante cualquier otro estado recibe 404, igual
	 * que si el id no existiera, para no revelar detalle operativo. Los editores reciben ademas el historial.
	 */
	public WindowDetail detail(UUID id) {
		boolean operational = access.canReadOperational();
		IntermittencyWindow window = windows.findById(id)
				.filter(found -> operational || found.getState() == WindowState.CONFIRMED)
				.orElseThrow(() -> new NotFoundException("WINDOW_NOT_FOUND", "La ventana no existe"));
		KiaPointReference point = points.findByIds(List.of(window.getEquipmentId())).get(window.getEquipmentId());
		return new WindowDetail(view(window, operational, Instant.now(clock)),
				operational ? windows.findRevisions(id) : null, point == null ? null : point.name());
	}

	private static WindowView view(IntermittencyWindow window, boolean operational, Instant now) {
		return WindowView.of(window, now, operational);
	}
}
