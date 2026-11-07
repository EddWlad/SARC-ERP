package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.time.Clock;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.uptime.application.command.UptimeFilter;
import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeView;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementFact;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy.Kind;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy.Window;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator.Kpis;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator.Unit;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeScope;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementRepository;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorDirectory;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorQuery;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Consulta de indicadores. Lee en {@code REPEATABLE READ} para que resumen y resultados vean la misma
 * foto. Sin rango elegido mira 366 dias atras, el maximo admitido para una consulta.
 */
@Service
public class UptimeQueryService {

	private static final long MAX_RANGE_DAYS = 366;
	private static final Duration LOOKBACK = Duration.ofDays(MAX_RANGE_DAYS);
	private static final int TREND_WEEKS = 12;
	private static final int SCOPE_LIMIT = 50;

	private final MeasurementRepository measurements;
	private final ConnectorDirectory connectors;
	private final UptimeReadAccess access;
	private final OperatingZone zone;
	private final Clock clock;

	UptimeQueryService(MeasurementRepository measurements, ConnectorDirectory connectors, UptimeReadAccess access,
			OperatingZone zone, Clock clock) {
		this.measurements = measurements;
		this.connectors = connectors;
		this.access = access;
		this.zone = zone;
		this.clock = clock;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public UptimeView view(UptimeFilter filter) {
		check(filter);
		Instant now = clock.instant();
		List<MeasurableConnector> scope = connectorsOf(filter);
		Set<UUID> ids = scope.stream().map(MeasurableConnector::connectorId).collect(Collectors.toSet());
		boolean chosen = filter.from() != null;
        boolean weekly = filter.referenceWeek()!=null || !chosen && filter.periodKind()==Kind.PROVISIONAL
            && filter.periodType()==PeriodType.ROLLING_7D;
        if (weekly) return referenceView(filter,scope,now);
		Instant from = chosen ? zone.startOf(filter.from()) : now.minus(LOOKBACK);
		Instant to = chosen ? zone.startOf(filter.to()) : now;
		List<MeasurementFact> unfilteredFacts = measurements.findFacts(from, to);
		List<MeasurementFact> facts = unfilteredFacts.stream()
				.filter(fact -> ids.contains(fact.connectorId())).toList();
		List<Window> windows = unfilteredFacts.stream().filter(MeasurementFact::included)
				.map(fact -> new Window(fact.periodType(), fact.periodStart(), fact.periodEnd())).distinct().toList();

		List<Instant> weeks = filter.periodKind() == Kind.PROVISIONAL ? List.of() : officialWeeks(filter, windows, now);
		List<Unit> officialUnits = UptimeCalculator.officialUnits(scope, facts, weeks);
		List<Unit> provisionalUnits = filter.periodKind() == Kind.OFFICIAL ? List.of()
				: provisionalUnits(filter, scope, facts, windows);
		Kpis official = published(officialUnits);
		Kpis provisional = published(provisionalUnits);
        if (!chosen && filter.periodKind()==null && windows.stream().noneMatch(w->w.type().isExact()) && measurements.latestIncludedReference().isPresent())
            return referenceView(filter,scope,now);

		Kind kind = headline(filter, official, provisional);
        if(kind==Kind.NONE && !chosen && filter.periodKind()==null)kind=OpeningPeriodPolicy.resolve(windows).kind();
		List<Unit> units = switch (kind) {
			case OFFICIAL -> officialUnits;
			case PROVISIONAL -> provisionalUnits;
			case NONE -> UptimeCalculator.provisionalUnits(scope, facts);
		};
		UptimeCalculator.Coverage coverage = UptimeCalculator.coverage(units);
		UptimeView.ResolvedPeriod resolved = resolved(kind, units, weeks, filter, windows);
		boolean operational = access.canReadOperational();
		return new UptimeView(resolved, official, provisional, coverage, units,
				trend(scope, facts, trendWeeks(from, to, now, chosen)), notices(official, coverage, facts, chosen, from, to, now),
				kind==Kind.NONE && !chosen?0:pendingLink(filter, from, to, unfilteredFacts), measurements.lastValidatedAt(), now, operational ? operations(selectedOperationalFacts(filter,resolved,facts)) : null, officialUnits,
				provisionalUnits);
	}

	/** Paginado sobre el calculo completo: el orden y las posiciones son del ranking. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<UptimeCalculator.ConnectorResult> connectorResults(UptimeFilter filter, PageQuery page) {
		UptimeView view=resultsView(filter);
        return page(UptimeCalculator.ranking(view.units()), page);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<UptimeCalculator.EquipmentResult> equipmentResults(UptimeFilter filter, PageQuery page) {
		UptimeView view=resultsView(filter);
        return page(view.selection().basis()==UptimeView.SelectionBasis.REFERENCE_WEEK
                && filter.temporalSection()==TemporalPrecision.REFERENCE_WEEK
            ? UptimeCalculator.referenceEquipment(view.units(),view.selection().referenceWeek())
            : UptimeCalculator.equipment(view.units()), page);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<UptimeCalculator.Exclusion> exclusions(UptimeFilter filter, PageQuery page) {
		return page(UptimeCalculator.exclusions(resultsView(filter).units(), access.canReadOperational()), page);
	}

    private UptimeView resultsView(UptimeFilter filter) {
        UptimeView view=view(filter);
        if (view.selection().basis()==UptimeView.SelectionBasis.REFERENCE_WEEK && filter.temporalSection()==null)
            throw new BusinessRuleException("UPTIME_SECTION_REQUIRED","Elige el apartado temporal de los resultados.");
        return view;
    }

    private UptimeView referenceView(UptimeFilter filter,List<MeasurableConnector> scope,Instant now) {
        LocalDate week=filter.referenceWeek()!=null?filter.referenceWeek():measurements.latestIncludedReference().orElse(null);
        var resolved=new UptimeView.ResolvedPeriod(week==null?Kind.NONE:Kind.PROVISIONAL,
            PeriodType.ROLLING_7D,null,null,zone.zone().getId());
        var selection=new UptimeView.Selection(week==null?UptimeView.SelectionBasis.NONE:UptimeView.SelectionBasis.REFERENCE_WEEK,
            week,PeriodType.ROLLING_7D,zone.zone().getId(),filter.temporalSection());
        List<MeasurementFact> facts=week==null?List.of():measurements.findReferenceFacts(week,
            scope.stream().map(MeasurableConnector::connectorId).toList());
        List<MeasurementFact> known=facts.stream().filter(f->f.belongsTo(TemporalPrecision.KNOWN_RANGE))
            .filter(f->f.periodType()==PeriodType.ROLLING_7D).toList();
        List<MeasurementFact> reference=facts.stream().filter(f->f.belongsTo(TemporalPrecision.REFERENCE_WEEK)).toList();
        List<Unit> knownUnits=week==null?List.of():UptimeCalculator.provisionalUnits(scope,known);
        List<Unit> referenceUnits=week==null?List.of():UptimeCalculator.provisionalUnits(scope,reference);
        var knownCoverage=week==null?null:UptimeCalculator.coverage(knownUnits);
        var report=week==null?null:UptimeCalculator.referenceKpis(referenceUnits,week);
        boolean refSelected=filter.temporalSection()==TemporalPrecision.REFERENCE_WEEK;
        List<Unit> selected=refSelected?referenceUnits:knownUnits;
        long pendingKnown=measurements.countPendingLinksByReference(week,TemporalPrecision.KNOWN_RANGE);
        long pendingReference=measurements.countPendingLinksByReference(week,TemporalPrecision.REFERENCE_WEEK);
        // La cabecera pertenece al bloque con horario; un informe no hereda su denominador.
        boolean knownPresent=!known.isEmpty() || filter.temporalSection()==TemporalPrecision.KNOWN_RANGE;
        long unattributed=facts.stream().filter(f->f.temporalPrecision()==TemporalPrecision.REQUIRES_RECONCILIATION
            && f.pendingSection()==null && !f.excluded()).count();
        List<MeasurementFact> operationFacts=filter.temporalSection()==null?facts:refSelected?reference:known;
        return new UptimeView(resolved,null,published(knownUnits),knownPresent?knownCoverage:null,selected,List.of(),
            List.of(UptimeView.Notice.NO_OFFICIAL_WEEK),refSelected?pendingReference:pendingKnown,
            measurements.lastValidatedAt(),now,access.canReadOperational()?operations(operationFacts):null,
            List.of(),knownUnits,selection,report,referenceUnits,knownCoverage,
            week==null?null:UptimeCalculator.composition(knownUnits),pendingReference,unattributed);
    }

	public List<MeasurableConnector> scope(String text) {
		return connectors.search(new ConnectorQuery(text, null, null, null, null)).stream().filter(UptimeQueryService::evaluable)
				.limit(SCOPE_LIMIT).toList();
	}

	private void check(UptimeFilter filter) {
        if (filter.referenceWeek()!=null && (filter.referenceWeek().getDayOfWeek()!=DayOfWeek.MONDAY
            || filter.from()!=null || filter.to()!=null || filter.periodKind()==Kind.OFFICIAL
            || filter.periodType()!=null && filter.periodType()!=PeriodType.ROLLING_7D)
            || filter.temporalSection()==TemporalPrecision.REQUIRES_RECONCILIATION
            || filter.temporalSection()!=null && filter.referenceWeek()==null
                && !(filter.periodKind()==Kind.PROVISIONAL && filter.periodType()==PeriodType.ROLLING_7D && filter.from()==null))
            throw new BusinessRuleException("UPTIME_SELECTION_INVALID","La referencia semanal y el rango real son selecciones distintas.");
		if ((filter.from() == null) != (filter.to() == null)
				|| filter.from() != null && !filter.from().isBefore(filter.to())) {
			throw new BusinessRuleException("UPTIME_PERIOD_INVALID", "Indica un rango valido.");
		}
		if (filter.from() != null && ChronoUnit.DAYS.between(filter.from(), filter.to()) > MAX_RANGE_DAYS) {
			throw new BusinessRuleException("UPTIME_RANGE_TOO_LONG", "El rango no puede superar 366 dias.");
		}
		if (filter.periodKind() == Kind.PROVISIONAL && (filter.periodType() == null || filter.periodType().isExact())) {
			throw new BusinessRuleException("UPTIME_PERIOD_TYPE_REQUIRED", "Elige el tipo de ventana provisional.");
		}
		if (filter.periodKind() == Kind.OFFICIAL && filter.from() != null
				&& (filter.from().getDayOfWeek() != DayOfWeek.MONDAY || filter.to().getDayOfWeek() != DayOfWeek.MONDAY)) {
			throw new BusinessRuleException("UPTIME_WEEK_NOT_ALIGNED", "Las semanas oficiales van de lunes a lunes.");
		}
	}

	private List<MeasurableConnector> connectorsOf(UptimeFilter filter) {
		return connectors.search(new ConnectorQuery(null, filter.network(), filter.type(), filter.brand(),
				filter.equipmentId())).stream().filter(UptimeQueryService::evaluable)
				.filter(item -> filter.connectorId() == null || item.connectorId().equals(filter.connectorId())).toList();
	}

	private static boolean evaluable(MeasurableConnector connector) {
		return UptimeScope.includes(connector.network(), connector.type())
				&& connector.equipmentStatus() != AdministrativeStatus.DELETED;
	}

	/** Con rango, todas sus semanas cerradas; sin el, la semana oficial mas reciente. */
	private List<Instant> officialWeeks(UptimeFilter filter, List<Window> windows, Instant now) {
		if (filter.from() == null) {
			return windows.stream().filter(window -> window.type().isExact()).map(Window::start)
					.max(Comparator.naturalOrder()).stream().toList();
		}
		List<Instant> weeks = new ArrayList<>();
		LocalDate monday = filter.from().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
		for (; !monday.plusWeeks(1).isAfter(filter.to()) && !zone.startOf(monday.plusWeeks(1)).isAfter(now); monday = monday
				.plusWeeks(1)) {
			weeks.add(zone.startOf(monday));
		}
		return weeks;
	}

	private List<Unit> provisionalUnits(UptimeFilter filter, List<MeasurableConnector> scope, List<MeasurementFact> facts,
			List<Window> windows) {
		PeriodType type = filter.periodType() != null && !filter.periodType().isExact() ? filter.periodType()
				: OpeningPeriodPolicy.preferredProvisional(windows).orElse(null);
		if (type == null) {
			return List.of();
		}
		Predicate<Instant> inRange = filter.from() != null
				? end -> !end.isBefore(zone.startOf(filter.from())) && end.isBefore(zone.startOf(filter.to()))
				: windows.stream().anyMatch(window -> window.type() == type) ? OpeningPeriodPolicy.range(type, windows)::includes
						: end -> false;
		return UptimeCalculator.provisionalUnits(scope,
				facts.stream().filter(fact -> fact.periodType() == type && inRange.test(fact.periodEnd())).toList());
	}

	/** Un bloque sin medicion incluida no existe: nulo, nunca 0. */
	private static Kpis published(List<Unit> units) {
		Kpis kpis = UptimeCalculator.kpis(units);
		return kpis.uptimePercent() == null ? null : kpis;
	}

	private static Kind headline(UptimeFilter filter, Kpis official, Kpis provisional) {
		if (filter.periodKind() != null && filter.periodKind() != Kind.NONE) {
			return filter.periodKind();
		}
		if (official != null) {
			return Kind.OFFICIAL;
		}
		return provisional != null ? Kind.PROVISIONAL : Kind.NONE;
	}

	private UptimeView.ResolvedPeriod resolved(Kind kind, List<Unit> units, List<Instant> weeks, UptimeFilter filter,
			List<Window> windows) {
		String timeZone = zone.zone().getId();
		return switch (kind) {
			case NONE -> new UptimeView.ResolvedPeriod(Kind.NONE, null, filter.from()==null?null:zone.startOf(filter.from()), filter.to()==null?null:zone.startOf(filter.to()), timeZone);
			case OFFICIAL -> new UptimeView.ResolvedPeriod(Kind.OFFICIAL, PeriodType.EXACT_WEEK,
					weeks.isEmpty() ? null : weeks.getFirst(),
					weeks.isEmpty() ? null : weeks.getLast().plus(PeriodType.EXACT_WEEK.nominal()), timeZone);
			case PROVISIONAL -> provisionalPeriod(units, filter, windows, timeZone);
		};
	}

	private UptimeView.ResolvedPeriod provisionalPeriod(List<Unit> units, UptimeFilter filter, List<Window> windows,
			String timeZone) {
		List<MeasurementFact> included = units.stream().map(Unit::fact).filter(Objects::nonNull).toList();
		PeriodType type = included.isEmpty() ? filter.periodType() : included.getFirst().periodType();
		return new UptimeView.ResolvedPeriod(Kind.PROVISIONAL, type, extreme(included, MeasurementFact::periodStart, false),
				extreme(included, MeasurementFact::periodEnd, true), timeZone);
	}

	private static Instant extreme(List<MeasurementFact> facts, Function<MeasurementFact, Instant> value, boolean latest) {
		Comparator<Instant> order = latest ? Comparator.naturalOrder() : Comparator.reverseOrder();
		return facts.stream().map(value).max(order).orElse(null);
	}

	private List<UptimeView.TrendPoint> trend(List<MeasurableConnector> scope, List<MeasurementFact> facts,
			List<Instant> weeks) {
		return weeks.stream().map(week -> new UptimeView.TrendPoint(zone.dateOf(week),
				UptimeCalculator.kpis(UptimeCalculator.officialUnits(scope, facts, List.of(week))).uptimePercent()))
				.filter(point -> point.uptimePercent() != null).toList();
	}

	/** Sin rango elegido, las ultimas doce semanas cerradas. */
	private List<Instant> trendWeeks(Instant from, Instant to, Instant now, boolean chosen) {
		LocalDate last = lastClosedWeek(now);
		List<Instant> weeks = new ArrayList<>();
		for (int back = 0; chosen ? back < 53 : back < TREND_WEEKS; back++) {
			Instant start = zone.startOf(last.minusWeeks(back));
			if (!start.isBefore(from) && !start.plus(PeriodType.EXACT_WEEK.nominal()).isAfter(to)) {
				weeks.addFirst(start);
			}
		}
		return weeks;
	}

	private LocalDate lastClosedWeek(Instant now) {
		return zone.dateOf(now).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
	}

	private List<UptimeView.Notice> notices(Kpis official, UptimeCalculator.Coverage coverage, List<MeasurementFact> facts,
			boolean chosen, Instant from, Instant to, Instant now) {
		List<UptimeView.Notice> notices = new ArrayList<>();
		if (official == null) {
			notices.add(UptimeView.Notice.NO_OFFICIAL_WEEK);
		}
		else {
			Instant latest = zone.startOf(lastClosedWeek(now));
			boolean inView = !chosen || !latest.isBefore(from) && !latest.isAfter(to);
			if (inView && facts.stream()
					.noneMatch(fact -> fact.included() && fact.periodType().isExact() && fact.periodStart().equals(latest))) {
				notices.add(UptimeView.Notice.LATEST_WEEK_MISSING);
			}
		}
		if (coverage.denominator() == 0) {
			notices.add(UptimeView.Notice.NO_EVALUABLE_CONNECTORS);
		}
		return notices;
	}

	/** El periodo sin atribuir se resuelve antes de filtros de red/equipo/conector. */
	private long pendingLink(UptimeFilter filter, Instant from, Instant to, List<MeasurementFact> facts) {
		if (filter.from() != null) {
			return measurements.countPendingLinks(from, to);
		}
		List<Window> windows = facts.stream().filter(MeasurementFact::included)
				.filter(fact -> filter.periodKind() != Kind.OFFICIAL || fact.periodType().isExact())
				.filter(fact -> filter.periodKind() != Kind.PROVISIONAL || !fact.periodType().isExact())
				.filter(fact -> filter.periodType() == null || fact.periodType() == filter.periodType())
				.map(fact -> new Window(fact.periodType(), fact.periodStart(), fact.periodEnd())).distinct().toList();
		OpeningPeriodPolicy.Opening opening = OpeningPeriodPolicy.resolve(windows);
		return measurements.countPendingLinks(opening.from() == null ? from : opening.from(),
				opening.to() == null ? to : opening.to());
	}

    private List<MeasurementFact> selectedOperationalFacts(UptimeFilter filter,UptimeView.ResolvedPeriod resolved,
            List<MeasurementFact> facts) {
        if (filter.from()!=null) return facts.stream()
            .filter(f->filter.periodType()==null || f.periodType()==filter.periodType())
            .filter(f->filter.periodKind()!=Kind.OFFICIAL || f.periodType().isExact()).toList();
        if (resolved.kind()==Kind.NONE || resolved.from()==null || resolved.to()==null) return List.of();
        return facts.stream().filter(f->f.periodType()==resolved.periodType())
            .filter(f->!f.periodEnd().isBefore(resolved.from()) && !f.periodEnd().isAfter(resolved.to())).toList();
    }

	private static UptimeView.Operations operations(List<MeasurementFact> facts) {
		int observed = 0;
		int ready = 0;
		int corrections = 0;
		for (MeasurementFact fact : facts) {
			if (fact.temporalPrecision()!=TemporalPrecision.REQUIRES_RECONCILIATION && fact.latestPending()) {
				if (fact.latestObserved()) {
					observed++;
				}
				else if (fact.validated()) {
					corrections++;
				}
				else {
					ready++;
				}
			}
		}
		return new UptimeView.Operations(observed, ready, corrections);
	}

	private static <T> PageResult<T> page(List<T> items, PageQuery page) {
		int start = (int)Math.min((long)page.page() * page.size(), items.size());
		int end = Math.min(start + page.size(), items.size());
		int totalPages = (int) Math.ceil((double) items.size() / page.size());
		return new PageResult<>(items.subList(start, end), page.page(), page.size(), items.size(), totalPages);
	}
}
