package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowRevision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.WindowRepository;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.persistence.AdvisoryLocks;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

@Component
class WindowRepositoryAdapter implements WindowRepository {

	/** Un punto con ese identificador nunca existe: relleno para el IN de una lista vacia. */
	private static final Collection<UUID> NO_POINTS = List.of(new UUID(0, 0));

	private static final Collection<WindowState> NO_STATES = List.of(WindowState.ANNULLED);

	private final WindowJpaRepository windows;
	private final WindowRevisionJpaRepository revisions;

	@PersistenceContext
	private EntityManager entityManager;

	WindowRepositoryAdapter(WindowJpaRepository windows, WindowRevisionJpaRepository revisions) {
		this.windows = windows;
		this.revisions = revisions;
	}

	@Override
	public IntermittencyWindow save(IntermittencyWindow window) {
		return windows.saveAndFlush(window);
	}

	@Override
	public WindowRevision saveRevision(WindowRevision revision) {
		return revisions.saveAndFlush(revision);
	}

	@Override
	public Optional<IntermittencyWindow> findById(UUID id) {
		return windows.findById(id);
	}

	@Override
	public List<WindowRevision> findRevisions(UUID windowId) {
		return revisions.findByWindowIdOrderByRevisionAsc(windowId);
	}

	@Override
	public List<IntermittencyWindow> findOverlapping(UUID equipmentId, Instant start, Instant end, UUID excludeId) {
		return windows.findOverlapping(equipmentId, start, end, excludeId);
	}

	@Override
	public Optional<IntermittencyWindow> findIdentical(UUID equipmentId, Instant start, Instant end, UUID excludeId) {
		return windows.findIdentical(equipmentId, start, end, excludeId).stream().findFirst();
	}

	@Override
	public List<IntermittencyWindow> findInRange(Collection<UUID> equipmentIds, Instant from, Instant to,
			Instant cutOff, Collection<WindowState> states) {
		boolean allPoints = equipmentIds == null || equipmentIds.isEmpty();
		boolean allStates = states == null || states.isEmpty();
		return windows.findInRange(from, to, cutOff, allPoints, allPoints ? NO_POINTS : equipmentIds, allStates,
				allStates ? NO_STATES : states);
	}

	@Override
	public PageResult<IntermittencyWindow> search(Collection<UUID> equipmentIds, Instant from, Instant to,
			Collection<WindowState> states, PageQuery page) {
		boolean allPoints = equipmentIds == null || equipmentIds.isEmpty();
		boolean allStates = states == null || states.isEmpty();
		Page<IntermittencyWindow> found = windows.search(from != null, from == null ? Instant.EPOCH : from,
				to != null, to == null ? Instant.EPOCH : to, allPoints, allPoints ? NO_POINTS : equipmentIds,
				allStates, allStates ? NO_STATES : states,
				PageRequest.of(page.page(), page.size(), Sort.by(Sort.Order.desc("startAt"), Sort.Order.asc("id"))));
		return new PageResult<>(found.getContent(), found.getNumber(), found.getSize(), found.getTotalElements(),
				found.getTotalPages());
	}

	@Override
	public List<IntermittencyWindow> findByState(WindowState state) {
		return windows.findByStateOrderByStartAtAscIdAsc(state);
	}

	@Override
	public java.util.Set<String> findProvenance(UUID batchId) {
		return new java.util.HashSet<>(windows.findProvenance(batchId));
	}

	@Override
	public java.util.Set<String> findProvenance(UUID batchId, int rowNumber) {
		return new java.util.HashSet<>(windows.findRowProvenance(batchId, rowNumber));
	}

	@Override
	public java.util.Optional<UUID> findEquipmentId(UUID windowId) {
		return windows.findEquipmentId(windowId);
	}

	@Override
	public void lockEquipment(UUID equipmentId) {
		AdvisoryLocks.lock(entityManager, "itm:", equipmentId.toString());
	}
}
