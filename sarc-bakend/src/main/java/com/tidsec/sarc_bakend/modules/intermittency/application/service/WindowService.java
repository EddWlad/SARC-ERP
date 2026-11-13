package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.CaptureWindowsCommand;
import com.tidsec.sarc_bakend.modules.intermittency.application.command.CaptureWindowsCommand.WindowInput;
import com.tidsec.sarc_bakend.modules.intermittency.application.command.CloseWindowCommand;
import com.tidsec.sarc_bakend.modules.intermittency.application.command.WindowChangeCommands;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.CaptureResult;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.CaptureResult.DayTotal;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.PointTestResult;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.WindowView;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowChange;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowRevision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowSource;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodSlicer;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodSlicer.DaySlice;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.WindowRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.error.SarcException;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Escritura de ventanas por editores: captura manual y cierre de una ventana abierta. Toda escritura pasa por
 * {@link WindowWriteGuard}, que bloquea el punto y rechaza duplicados y solapamientos.
 */
@Service
@PreAuthorize(IntermittencyPermissions.EDITORS)
public class WindowService {

	private static final String CODE_OVERLAP = "WINDOW_OVERLAP";
	private static final String CODE_DUPLICATE = "WINDOW_DUPLICATE";

	private final WindowRepository windows;
	private final WindowWriteGuard guard;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final PeriodSlicer slicer;

	WindowService(WindowRepository windows, WindowWriteGuard guard, AuditTrail audit, CurrentActor actor,
			OperatingZone zone) {
		this.windows = windows;
		this.guard = guard;
		this.audit = audit;
		this.actor = actor;
		this.slicer = new PeriodSlicer(zone.zone());
	}

	/**
	 * Guarda las ventanas de un punto en una sola operacion atomica: si una falla, no se guarda ninguna y el
	 * error lista el motivo de cada una (FR-019 a FR-021b).
	 */
	@Transactional
	public CaptureResult capture(CaptureWindowsCommand command) {
		guard.lock(List.of(command.equipmentId()));
		KiaPointReference point = guard.requireWritable(command.equipmentId());
		Instant now = guard.now();
		UUID actorId = actor.id().orElseThrow();

		List<IntermittencyWindow> created = new ArrayList<>();
		List<Map<String, Object>> errors = new ArrayList<>();
		SarcException firstFailure = null;
		for (int index = 0; index < command.windows().size(); index++) {
			WindowInput input = command.windows().get(index);
			try {
				IntermittencyWindow window = IntermittencyWindow.register(point.id(), label(point), input.startAt(),
						input.endAt(), input.comment(), WindowSource.MANUAL, null, false, now);
				guard.requireFree(point.id(), window.getStartAt(), window.getEndAt(), null);
				requireFreeAmong(created, window);
				created.add(window);
			}
			catch (SarcException invalid) {
				errors.add(error(index, invalid));
				firstFailure = firstFailure == null ? invalid : firstFailure;
			}
		}
		if (!errors.isEmpty()) {
			throw atomicFailure(errors, firstFailure);
		}

		List<WindowView> views = new ArrayList<>();
		for (IntermittencyWindow window : created) {
			IntermittencyWindow saved = guard.save(window);
			WindowRevision revision = saved.snapshot(WindowChange.CREATED, null, actorId, now);
			windows.saveRevision(revision);
			audit.record("INTERMITTENCY_WINDOW_CREATED", IntermittencyWindow.AUDIT_ENTITY_TYPE, saved.getId(),
					Map.of("equipmentId", point.id().toString(), "state", saved.getState().name(), "source",
							saved.getSource().name()));
			views.add(view(saved, now));
		}
		return new CaptureResult(point.id(), views, dayTotals(created));
	}

	/** Pone fin a una ventana abierta; queda CONFIRMED o, si dura 240 minutos o mas, REVIEW (FR-012, FR-021a). */
	@Transactional
	public WindowView close(CloseWindowCommand command) {
		IntermittencyWindow window = lockAndLoad(command.windowId(), command.version());
		Instant now = guard.now();
		guard.requireFree(window.getEquipmentId(), window.getStartAt(), command.endAt(), window.getId());
		WindowRevision revision = window.close(command.endAt(), actor.id().orElseThrow(), now);
		IntermittencyWindow saved = guard.save(window);
		windows.saveRevision(revision);
		audit.record("INTERMITTENCY_WINDOW_CLOSED", IntermittencyWindow.AUDIT_ENTITY_TYPE, saved.getId(),
				Map.of("equipmentId", saved.getEquipmentId().toString(), "state", saved.getState().name(),
						"minutes", String.valueOf(saved.getDurationMinutes())));
		return view(saved, now);
	}

	/**
	 * Corrige el inicio, el fin o el comentario de una ventana confirmada o por revisar, con justificacion. Se
	 * conserva la revision anterior y puede volver a REVIEW (FR-017, FR-021a).
	 */
	@Transactional
	public WindowView correct(WindowChangeCommands.Correct command) {
		IntermittencyWindow window = lockAndLoad(command.windowId(), command.version());
		Instant now = guard.now();
		window.requireCorrectable();
		// Se comprueba antes de cambiar la entidad: una consulta con la entidad modificada la escribiria primero y
		// la base rechazaria el solapamiento sin el mensaje de negocio.
		guard.requireFree(window.getEquipmentId(), command.startAt() == null ? window.getStartAt() : command.startAt(),
				command.endAt() == null ? window.getEndAt() : command.endAt(), window.getId());
		WindowRevision revision = window.correct(command.startAt(), command.endAt(), command.comment(),
				command.justification(), actor.id().orElseThrow(), now);
		return persist(window, revision, "INTERMITTENCY_WINDOW_CORRECTED", now);
	}

	/** Un editor confirma una ventana por revisar; la puede confirmar quien la registro. */
	@Transactional
	public WindowView confirmReview(WindowChangeCommands.Justified command) {
		IntermittencyWindow window = lockAndLoad(command.windowId(), command.version());
		Instant now = guard.now();
		WindowRevision revision = window.confirmReview(command.justification(), actor.id().orElseThrow(), now);
		return persist(window, revision, "INTERMITTENCY_WINDOW_REVIEW_CONFIRMED", now);
	}

	/** Marca una ventana confirmada o por revisar como prueba: se conserva y deja de contar como oficial. */
	@Transactional
	public WindowView markTest(WindowChangeCommands.Justified command) {
		IntermittencyWindow window = lockAndLoad(command.windowId(), command.version());
		Instant now = guard.now();
		WindowRevision revision = window.markTest(command.justification(), actor.id().orElseThrow(), now);
		return persist(window, revision, "INTERMITTENCY_WINDOW_MARKED_TEST", now);
	}

	@Transactional
	public WindowView unmarkTest(WindowChangeCommands.Justified command) {
		IntermittencyWindow window = lockAndLoad(command.windowId(), command.version());
		Instant now = guard.now();
		WindowRevision revision = window.unmarkTest(command.justification(), actor.id().orElseThrow(), now);
		return persist(window, revision, "INTERMITTENCY_WINDOW_UNMARKED_TEST", now);
	}

	/** Anula la ventana sin borrarla: queda con su historial y deja de bloquear a las demas. */
	@Transactional
	public WindowView annul(WindowChangeCommands.Justified command) {
		IntermittencyWindow window = lockAndLoad(command.windowId(), command.version());
		Instant now = guard.now();
		WindowRevision revision = window.annul(command.justification(), actor.id().orElseThrow(), now);
		return persist(window, revision, "INTERMITTENCY_WINDOW_ANNULLED", now);
	}

	/**
	 * Marca como prueba todas las ventanas CONFIRMED o REVIEW del punto en {@code [from, to)}, con una
	 * justificacion unica y una revision por ventana. Las abiertas del rango no se marcan (una ventana de prueba
	 * siempre esta cerrada) y se informa cuantas son. No se guarda ninguna marca permanente del punto ni se
	 * modifica Inventario (FR-016).
	 */
	@Transactional
	public PointTestResult markPointTest(WindowChangeCommands.PointTest command) {
		return changePointTest(command, true);
	}

	/** Devuelve a CONFIRMED o REVIEW las ventanas de prueba del punto en el rango. */
	@Transactional
	public PointTestResult unmarkPointTest(WindowChangeCommands.PointTest command) {
		return changePointTest(command, false);
	}

	private PointTestResult changePointTest(WindowChangeCommands.PointTest command, boolean mark) {
		if (command.from() == null || command.to() == null || !command.to().isAfter(command.from())) {
			throw new BusinessRuleException("PERIOD_INVALID", "El rango debe tener al menos un dia: el fin es exclusivo");
		}
		// Antes de buscar ventanas: un rango sin ventanas tampoco se cambia sin motivo ni deja auditoria sin el.
		IntermittencyWindow.requireJustification(command.justification());
		guard.lock(List.of(command.equipmentId()));
		guard.requireWritable(command.equipmentId());
		Instant from = slicer.startOf(command.from());
		Instant to = slicer.startOf(command.to());
		Instant now = guard.now();
		UUID actorId = actor.id().orElseThrow();
		List<IntermittencyWindow> targets = windows.findInRange(List.of(command.equipmentId()), from, to, to,
				mark ? List.of(WindowState.CONFIRMED, WindowState.REVIEW) : List.of(WindowState.TEST));
		for (IntermittencyWindow window : targets) {
			WindowRevision revision = mark ? window.markTest(command.justification(), actorId, now)
					: window.unmarkTest(command.justification(), actorId, now);
			persist(window, revision, mark ? "INTERMITTENCY_WINDOW_MARKED_TEST" : "INTERMITTENCY_WINDOW_UNMARKED_TEST",
					now);
		}
		int open = mark ? windows.findInRange(List.of(command.equipmentId()), from, to, to, List.of(WindowState.OPEN))
				.size() : 0;
		audit.record(mark ? "INTERMITTENCY_POINT_MARKED_TEST" : "INTERMITTENCY_POINT_UNMARKED_TEST", "INTERMITTENCY_POINT",
				command.equipmentId(), Map.of("from", command.from().toString(), "to", command.to().toString(),
						"windows", String.valueOf(targets.size()), "openNotMarked", String.valueOf(open)));
		return new PointTestResult(targets.size(), open);
	}

	/**
	 * Bloquea el punto de la ventana y solo despues la lee, con el bloqueo tomado, para no trabajar con un estado
	 * anterior a un cambio concurrente; comprueba la version leida por quien edita.
	 */
	private IntermittencyWindow lockAndLoad(UUID id, long version) {
		UUID equipmentId = windows.findEquipmentId(id)
				.orElseThrow(() -> new NotFoundException("WINDOW_NOT_FOUND", "La ventana no existe"));
		guard.lock(List.of(equipmentId));
		IntermittencyWindow window = windows.findById(id)
				.orElseThrow(() -> new NotFoundException("WINDOW_NOT_FOUND", "La ventana no existe"));
		window.requireVersion(version);
		return window;
	}

	private WindowView persist(IntermittencyWindow window, WindowRevision revision, String action, Instant now) {
		IntermittencyWindow saved = guard.save(window);
		windows.saveRevision(revision);
		audit.record(action, IntermittencyWindow.AUDIT_ENTITY_TYPE, saved.getId(),
				Map.of("equipmentId", saved.getEquipmentId().toString(), "state", saved.getState().name(), "revision",
						String.valueOf(saved.getCurrentRevision())));
		return view(saved, now);
	}

	/** Nombre y codigo del punto en la fecha del hecho: se conserva aunque el equipo cambie de nombre (D12). */
	static String label(KiaPointReference point) {
		return point.assignedCode() == null ? point.name() : point.name() + " (" + point.assignedCode() + ")";
	}

	private WindowView view(IntermittencyWindow window, Instant now) {
		return WindowView.of(window, now, true);
	}

	/** Las ventanas de una misma peticion tampoco pueden duplicarse ni solaparse entre si. */
	private static void requireFreeAmong(List<IntermittencyWindow> others, IntermittencyWindow window) {
		for (IntermittencyWindow other : others) {
			boolean identical = other.getStartAt().equals(window.getStartAt())
					&& java.util.Objects.equals(other.getEndAt(), window.getEndAt());
			if (identical) {
				throw new ConflictException(CODE_DUPLICATE, "La ventana esta repetida en la misma captura");
			}
			if (overlaps(other, window)) {
				throw new ConflictException(CODE_OVERLAP, "La ventana se solapa con otra de la misma captura");
			}
		}
	}

	private static boolean overlaps(IntermittencyWindow left, IntermittencyWindow right) {
		Instant leftEnd = left.getEndAt() == null ? Instant.MAX : left.getEndAt();
		Instant rightEnd = right.getEndAt() == null ? Instant.MAX : right.getEndAt();
		return left.getStartAt().isBefore(rightEnd) && right.getStartAt().isBefore(leftEnd);
	}

	private List<DayTotal> dayTotals(List<IntermittencyWindow> created) {
		Map<java.time.LocalDate, long[]> totals = new LinkedHashMap<>();
		for (IntermittencyWindow window : created) {
			if (window.getEndAt() == null) {
				continue;
			}
			for (DaySlice slice : slicer.byDay(window.getStartAt(), window.getEndAt(), window.getStartAt(),
					window.getEndAt(), window.getEndAt())) {
				long[] total = totals.computeIfAbsent(slice.day(), day -> new long[2]);
				total[0] += slice.minutes();
				total[1]++;
			}
		}
		return totals.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(entry -> new DayTotal(entry.getKey(), entry.getValue()[0], (int) entry.getValue()[1])).toList();
	}

	private static Map<String, Object> error(int index, SarcException invalid) {
		Map<String, Object> error = new LinkedHashMap<>();
		error.put("index", index);
		error.put("code", invalid.code());
		error.put("message", invalid.getMessage());
		return error;
	}

	/**
	 * Un solo error HTTP con el motivo de cada ventana. El codigo y el tipo (conflicto o regla de negocio) son
	 * los del primer error, para que el estado HTTP corresponda al codigo.
	 */
	private static SarcException atomicFailure(List<Map<String, Object>> errors, SarcException first) {
		String message = errors.size() == 1 ? first.getMessage()
				: "Ninguna ventana se guardo: " + errors.size() + " tienen errores";
		Map<String, Object> properties = Map.of("errors", List.copyOf(errors));
		return first instanceof ConflictException ? new ConflictException(first.code(), message, properties)
				: new BusinessRuleException(first.code(), message, properties);
	}
}
