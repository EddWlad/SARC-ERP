package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IssueCode;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowDecision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowInterpretation;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.BatchWindowConflicts;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.BatchWindowConflicts.Candidate;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.ImportRowInterpreter;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.WindowRepository;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Vuelve a interpretar y a clasificar filas de un lote (FR-025). Para cada una resuelve el punto por codigo o por
 * vinculacion explicita (R3), compara sus ventanas con las de las demas filas del lote del mismo punto y con las
 * ya guardadas (R6), y calcula los dias que toca. Una ventana abierta ocupa {@code [inicio, infinito)} en las dos
 * comparaciones (FR-014). Se recalcula completo despues de cada edicion o decision, asi que la clasificacion
 * siempre refleja el estado actual y nunca queda un conflicto de una decision ya retirada.
 */
@Component
class ImportClassifier {

	/** Tope de dias que una fila puede tocar: una caida mas larga que un anio ya es un error de dato. */
	private static final int MAX_TOUCHED_DAYS = 366;
	private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");
	private static final List<WindowState> LIVE = List.of(WindowState.OPEN, WindowState.REVIEW,
			WindowState.CONFIRMED, WindowState.TEST);

	private final KiaPointDirectory points;
	private final WindowRepository windows;
	private final OperatingZone zone;
	private final Clock clock;

	ImportClassifier(KiaPointDirectory points, WindowRepository windows, OperatingZone zone, Clock clock) {
		this.points = points;
		this.windows = windows;
		this.zone = zone;
		this.clock = clock;
	}

	/** Un punto resuelto para una fila y los motivos que ese resultado agrega. */
	private record Resolved(UUID equipmentId, List<IssueCode> issues) {
	}

	/**
	 * Reinterpreta y clasifica estas filas. Para comparar bien, deben ser todas las del lote que comparten punto
	 * con las que cambiaron (o todas, la primera vez).
	 */
	void classify(List<IntermittencyImportRow> rows) {
		Instant now = Instant.now(clock);
		ImportRowInterpreter interpreter = new ImportRowInterpreter(zone.zone());
		Map<UUID, RowInterpretation> interpretations = new HashMap<>();
		for (IntermittencyImportRow row : rows) {
			interpretations.put(row.getId(),
					interpreter.interpret(row.getRaw(), row.getEdits(), row.getInterpretation(), now));
		}
		Map<UUID, Resolved> resolved = resolvePoints(rows, interpretations);
		for (IntermittencyImportRow row : rows) {
			Resolved point = resolved.get(row.getId());
			RowInterpretation base = interpretations.get(row.getId());
			List<IssueCode> rowIssues = new ArrayList<>(base.rowIssues());
			if (point.equipmentId() != null && row.getLinkedAt() != null) {
				rowIssues.remove(IssueCode.MISSING_CODE);
			}
			rowIssues.addAll(point.issues());
			interpretations.put(row.getId(), base.withRowIssues(rowIssues));
		}
		markBatchConflicts(rows, interpretations, resolved);
		markStoredConflicts(rows, interpretations, resolved);
		for (IntermittencyImportRow row : rows) {
			RowInterpretation interpretation = interpretations.get(row.getId());
			row.classified(interpretation, interpretation.classification(), resolved.get(row.getId()).equipmentId(),
					touchedDays(interpretation));
		}
	}

	// ----- Punto -----

	private Map<UUID, Resolved> resolvePoints(List<IntermittencyImportRow> rows,
			Map<UUID, RowInterpretation> interpretations) {
		Set<String> codes = new LinkedHashSet<>();
		Set<UUID> linked = new HashSet<>();
		for (IntermittencyImportRow row : rows) {
			if (row.getLinkedAt() != null && row.getEquipmentId() != null) {
				linked.add(row.getEquipmentId());
			}
			else if (!interpretations.get(row.getId()).code().isEmpty()) {
				codes.add(interpretations.get(row.getId()).code());
			}
		}
		Map<String, List<KiaPointReference>> byCode = points.findByAssignedCodes(codes);
		Map<UUID, KiaPointReference> byId = points.findByIds(linked);
		Map<UUID, Resolved> result = new HashMap<>();
		for (IntermittencyImportRow row : rows) {
			RowInterpretation interpretation = interpretations.get(row.getId());
			List<IssueCode> issues = new ArrayList<>();
			UUID equipmentId = null;
			KiaPointReference point = null;
			if (row.getLinkedAt() != null && row.getEquipmentId() != null) {
				point = byId.get(row.getEquipmentId());
				equipmentId = point == null ? null : point.id();
				if (point == null || !point.inScope() || point.adminStatus() == AdministrativeStatus.DELETED) {
					issues.add(IssueCode.UNKNOWN_CODE);
					equipmentId = null;
					point = null;
				}
			}
			else if (!interpretation.code().isEmpty()) {
				List<KiaPointReference> found = byCode.getOrDefault(interpretation.code(), List.of());
				if (found.isEmpty()) {
					issues.add(IssueCode.UNKNOWN_CODE);
				}
				else if (found.size() > 1) {
					issues.add(IssueCode.AMBIGUOUS_CODE);
				}
				else {
					point = found.getFirst();
					equipmentId = point.id();
					if (!point.inScope()) {
						issues.add(IssueCode.NOT_KIA_CHARGING_POINT);
					}
					else if (point.adminStatus() == AdministrativeStatus.DELETED) {
						issues.add(IssueCode.DELETED_EQUIPMENT);
					}
				}
			}
			if (point != null && point.inScope() && point.adminStatus() != AdministrativeStatus.DELETED) {
				if (point.adminStatus() == AdministrativeStatus.INACTIVE) {
					issues.add(IssueCode.INACTIVE_EQUIPMENT);
				}
				if (!interpretation.name().isEmpty() && !comparable(interpretation.name())
						.equals(comparable(point.name()))) {
					issues.add(IssueCode.NAME_DIFFERS);
				}
			}
			result.put(row.getId(), new Resolved(equipmentId, issues));
		}
		return result;
	}

	/** Minusculas, sin tildes y con espacios simples: el nombre del archivo solo se compara, nunca vincula. */
	private static String comparable(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
				.strip().replaceAll("\\s+", " ");
	}

	// ----- Comparacion con el resto del lote -----

	/** Una fila se aplica al confirmar si no se omitio ni queda pendiente; solo esas ocupan tiempo en el lote. */
	private static boolean applies(IntermittencyImportRow row) {
		return row.getDecision() == null || row.getDecision() == RowDecision.APPLY;
	}

	private static boolean candidate(RowWindow window) {
		return !window.ignored() && window.hasShape() && window.issues().stream()
				.noneMatch(code -> code.classification() == RowClassification.INVALID);
	}

	private void markBatchConflicts(List<IntermittencyImportRow> rows, Map<UUID, RowInterpretation> interpretations,
			Map<UUID, Resolved> resolved) {
		Map<UUID, List<Candidate>> byPoint = new HashMap<>();
		for (IntermittencyImportRow row : rows) {
			UUID point = resolved.get(row.getId()).equipmentId();
			if (point == null || !applies(row) || hasPointProblem(resolved.get(row.getId()))) {
				continue;
			}
			for (RowWindow window : interpretations.get(row.getId()).windows()) {
				if (candidate(window)) {
					byPoint.computeIfAbsent(point, key -> new ArrayList<>())
							.add(new Candidate(row.getId(), window.pair(), window.startInstant(), window.endInstant()));
				}
			}
		}
		Map<String, IssueCode> conflicts = new HashMap<>();
		byPoint.values().forEach(list -> conflicts.putAll(BatchWindowConflicts.detect(list)));
		if (conflicts.isEmpty()) {
			return;
		}
		for (IntermittencyImportRow row : rows) {
			RowInterpretation interpretation = interpretations.get(row.getId());
			List<RowWindow> updated = interpretation.windows().stream().map(window -> {
				IssueCode code = conflicts.get(BatchWindowConflicts.key(row.getId(), window.pair()));
				return code == null ? window : withIssue(window, code, window.conflictWindowId());
			}).toList();
			interpretations.put(row.getId(), interpretation.withWindows(updated));
		}
	}

	private static boolean hasPointProblem(Resolved resolved) {
		return resolved.issues().stream().anyMatch(code -> code.classification() == RowClassification.PENDING_LINK
				|| code.classification() == RowClassification.OUT_OF_SCOPE);
	}

	// ----- Comparacion con lo guardado -----

	private void markStoredConflicts(List<IntermittencyImportRow> rows, Map<UUID, RowInterpretation> interpretations,
			Map<UUID, Resolved> resolved) {
		Set<UUID> pointIds = new HashSet<>();
		Instant earliest = FAR_FUTURE;
		for (IntermittencyImportRow row : rows) {
			Resolved point = resolved.get(row.getId());
			if (point.equipmentId() == null || hasPointProblem(point)) {
				continue;
			}
			for (RowWindow window : interpretations.get(row.getId()).windows()) {
				if (candidate(window)) {
					pointIds.add(point.equipmentId());
					earliest = window.startInstant().isBefore(earliest) ? window.startInstant() : earliest;
				}
			}
		}
		if (pointIds.isEmpty()) {
			return;
		}
		Map<UUID, List<IntermittencyWindow>> stored = new HashMap<>();
		for (IntermittencyWindow window : windows.findInRange(pointIds, earliest, FAR_FUTURE, FAR_FUTURE, LIVE)) {
			stored.computeIfAbsent(window.getEquipmentId(), key -> new ArrayList<>()).add(window);
		}
		for (IntermittencyImportRow row : rows) {
			Resolved point = resolved.get(row.getId());
			if (point.equipmentId() == null || hasPointProblem(point)) {
				continue;
			}
			RowInterpretation interpretation = interpretations.get(row.getId());
			List<IntermittencyWindow> existing = stored.getOrDefault(point.equipmentId(), List.of());
			List<RowWindow> updated = new ArrayList<>();
			for (RowWindow window : interpretation.windows()) {
				updated.add(candidate(window) ? compare(window, existing) : window);
			}
			interpretations.put(row.getId(), interpretation.withWindows(updated));
		}
	}

	private static RowWindow compare(RowWindow window, List<IntermittencyWindow> existing) {
		Instant start = window.startInstant();
		Instant end = window.endInstant();
		for (IntermittencyWindow stored : existing) {
			if (stored.getStartAt().equals(start) && java.util.Objects.equals(stored.getEndAt(), end)) {
				return withIssue(window, IssueCode.ALREADY_RECORDED, stored.getId().toString());
			}
		}
		List<IntermittencyWindow> overlapping = existing.stream().filter(stored -> {
			Instant storedEnd = stored.getEndAt() == null ? Instant.MAX : stored.getEndAt();
			Instant newEnd = end == null ? Instant.MAX : end;
			return stored.getStartAt().isBefore(newEnd) && start.isBefore(storedEnd);
		}).toList();
		if (overlapping.isEmpty()) {
			return window;
		}
		return withIssue(window, IssueCode.OVERLAPS_EXISTING,
				overlapping.size() == 1 ? overlapping.getFirst().getId().toString() : null);
	}

	private static RowWindow withIssue(RowWindow window, IssueCode code, String conflictWindowId) {
		if (window.issues().contains(code)) {
			return window;
		}
		List<IssueCode> issues = new ArrayList<>(window.issues());
		issues.add(code);
		return window.withIssues(issues, conflictWindowId);
	}

	// ----- Dias tocados -----

	/** Los dias locales de sus ventanas activas o, si no hay ninguna, la fecha de la fila (FR-045). */
	private List<LocalDate> touchedDays(RowInterpretation interpretation) {
		Set<LocalDate> days = new LinkedHashSet<>();
		for (RowWindow window : interpretation.active()) {
			// Una ventana invalida (por ejemplo, con un fin futuro) no aporta dias: se usa la fecha de la fila.
			if (!candidate(window)) {
				continue;
			}
			LocalDate first = window.startInstant().atZone(zone.zone()).toLocalDate();
			Instant last = window.endInstant() == null ? window.startInstant()
					: window.endInstant().minus(1, ChronoUnit.MINUTES);
			LocalDate lastDay = last.atZone(zone.zone()).toLocalDate();
			for (LocalDate day = first; !day.isAfter(lastDay) && days.size() < MAX_TOUCHED_DAYS; day = day.plusDays(1)) {
				days.add(day);
			}
		}
		if (days.isEmpty() && interpretation.day() != null) {
			days.add(interpretation.day());
		}
		return new ArrayList<>(days);
	}
}
