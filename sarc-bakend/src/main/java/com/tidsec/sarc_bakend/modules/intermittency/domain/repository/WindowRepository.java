package com.tidsec.sarc_bakend.modules.intermittency.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowRevision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Puerto de persistencia de las ventanas de intermitencia y su historial. */
public interface WindowRepository {

	/** Escribe de inmediato: la barrera de solapamiento y duplicado de la base debe fallar aqui. */
	IntermittencyWindow save(IntermittencyWindow window);

	WindowRevision saveRevision(WindowRevision revision);

	Optional<IntermittencyWindow> findById(UUID id);

	/** Revisiones en orden ascendente. */
	List<WindowRevision> findRevisions(UUID windowId);

	/**
	 * Ventanas no anuladas del punto que se solapan con {@code [start, end)}, donde {@code end} nulo es
	 * infinito (una ventana abierta ocupa desde su inicio hasta que se cierra). {@code excludeId} deja fuera la
	 * propia ventana al corregirla o cerrarla.
	 */
	List<IntermittencyWindow> findOverlapping(UUID equipmentId, Instant start, Instant end, UUID excludeId);

	/** Ventana no anulada con el mismo punto, inicio y fin (ambos nulos si es abierta). */
	Optional<IntermittencyWindow> findIdentical(UUID equipmentId, Instant start, Instant end, UUID excludeId);

	/**
	 * Ventanas que intersecan {@code [from, to)}: {@code start_at < to AND coalesce(end_at, cutOff) > from}.
	 * {@code equipmentIds} vacio significa todos los puntos; {@code states} vacio, todos los estados.
	 */
	List<IntermittencyWindow> findInRange(Collection<UUID> equipmentIds, Instant from, Instant to, Instant cutOff,
			Collection<WindowState> states);

	/**
	 * Historial paginado, de la mas reciente a la mas antigua. {@code equipmentIds} y {@code states} vacios
	 * significan todos; {@code from} y {@code to} nulos, sin limite. Una ventana abierta se considera abierta
	 * hacia adelante.
	 */
	PageResult<IntermittencyWindow> search(Collection<UUID> equipmentIds, Instant from, Instant to,
			Collection<WindowState> states, PageQuery page);

	/** Ventanas con el estado dado, en cualquier momento, en orden de inicio. */
	List<IntermittencyWindow> findByState(WindowState state);

	/** Claves {@code fila:par} de las ventanas ya creadas desde ese lote: reintentar una confirmacion no las repite. */
	java.util.Set<String> findProvenance(UUID batchId);

	/** Igual que {@link #findProvenance(UUID)}, solo para las ventanas de una fila del lote. */
	java.util.Set<String> findProvenance(UUID batchId, int rowNumber);

	/**
	 * Punto de una ventana sin cargarla: permite bloquear el punto antes de leer la ventana, para no trabajar
	 * con un estado anterior al bloqueo.
	 */
	Optional<UUID> findEquipmentId(UUID windowId);

	/**
	 * Toma el bloqueo transaccional de escritura de un punto: serializa las escrituras concurrentes sobre el
	 * mismo equipo hasta el final de la transaccion. Debe llamarse dentro de una transaccion.
	 */
	void lockEquipment(UUID equipmentId);
}
