package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.RetroactivePolicy;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Raiz del agregado: un caso unico con numero inmutable {@code TICKET-NNN}, un solo elemento afectado, y sus episodios.
 * Los unicos estados son Abierto y Cerrado (FR-030); el ticket esta abierto si y solo si su ultimo episodio lo esta.
 * Guarda lo que el elemento era en T0 (etiqueta, red, ubicacion) para que los informes no cambien si el equipo se
 * renombra o cambia de red despues (D12, P-14). Nada se borra: las colecciones no tienen cascada de borrado.
 */
@Entity
@Table(name = "inc_ticket")
public class Ticket extends BaseAuditableEntity {

	@Column(nullable = false, updatable = false)
	private int ticketNumber;

	/** Columna generada por la base: {@code TICKET-} y el numero con al menos tres digitos, sin recortar. */
	@Generated(event = EventType.INSERT)
	@Column(insertable = false, updatable = false)
	private String ticketCode;

	@Column(nullable = false, updatable = false)
	private UUID clientRequestId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TicketState state;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private ElementClass elementClass;

	@Column(updatable = false)
	private UUID equipmentId;

	@Column(name = "equipment_label_at_t0", updatable = false)
	private String equipmentLabelAtT0;

	@Column(name = "network_at_t0", updatable = false)
	private String networkAtT0;

	@Column(name = "infrastructure_type_at_t0", updatable = false)
	private String infrastructureTypeAtT0;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false)
	private ChatbotScope chatbotScope;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false)
	private SiteElementKind siteElementKind;

	@Column(updatable = false)
	private String elementName;

	@Column(updatable = false)
	private UUID referenceEquipmentId;

	@Column(updatable = false)
	private String manualSiteName;

	@Column(updatable = false)
	private String manualLocation;

	@Column(updatable = false)
	private String manualMapsUrl;

	@Column(name = "site_label_at_t0", updatable = false)
	private String siteLabelAtT0;

	@Column(name = "location_at_t0", updatable = false)
	private String locationAtT0;

	@Column(name = "maps_url_at_t0", updatable = false)
	private String mapsUrlAtT0;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IncidentType incidentType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportOrigin reportOrigin;

	private String reportOriginDetail;

	@Column(nullable = false)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Priority priority;

	@Column(nullable = false)
	private UUID currentResponsibleId;

	@Column(nullable = false)
	private String currentResponsibleName;

	@Column(nullable = false)
	private String duplicateKey;

	@Column(updatable = false)
	private String duplicateOverrideReason;

	@Column(nullable = false)
	private Instant lastActivityAt;

	@OneToMany(mappedBy = "ticket", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
	@OrderBy("ordinal ASC")
	@BatchSize(size = 500)
	private List<Episode> episodes = new ArrayList<>();

	@OneToMany(mappedBy = "ticket", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
	@BatchSize(size = 500)
	private List<TicketConnector> connectors = new ArrayList<>();

	protected Ticket() {
	}

	private Ticket(NewTicket in) {
		super(UUID.randomUUID());
		AffectedElement element = in.element();
		this.ticketNumber = in.number();
		this.clientRequestId = in.clientRequestId();
		this.state = TicketState.OPEN;
		this.elementClass = element.elementClass();
		this.equipmentId = element.equipmentId();
		this.equipmentLabelAtT0 = element.equipmentLabelAtT0();
		this.networkAtT0 = element.networkAtT0();
		this.infrastructureTypeAtT0 = element.infrastructureTypeAtT0();
		this.chatbotScope = element.chatbotScope();
		this.siteElementKind = element.siteElementKind();
		this.elementName = element.elementName();
		this.referenceEquipmentId = element.referenceEquipmentId();
		this.manualSiteName = element.manualSiteName();
		this.manualLocation = element.manualLocation();
		this.manualMapsUrl = element.manualMapsUrl();
		this.siteLabelAtT0 = element.siteLabelAtT0();
		this.locationAtT0 = element.locationAtT0();
		this.mapsUrlAtT0 = element.mapsUrlAtT0();
		this.incidentType = in.type();
		this.reportOrigin = in.origin();
		this.reportOriginDetail = in.originDetail();
		this.description = in.description();
		this.priority = in.priority();
		this.currentResponsibleId = in.responsibleId();
		this.currentResponsibleName = in.responsibleName();
		this.duplicateKey = in.duplicateKey();
		this.duplicateOverrideReason = in.duplicateOverrideReason();
		this.lastActivityAt = in.now();
	}

	/**
	 * Abre un ticket con su primer episodio y los cinco hitos: T0 registrado con la hora informada y T1 a T4
	 * pendientes. Un T0 futuro se rechaza, y uno retroactivo (mas de un minuto antes del registro) exige un motivo
	 * (FR-018). El numero ya viene asignado por el contador: aqui nunca se genera.
	 */
	public static Ticket open(NewTicket in) {
		RetroactivePolicy.Kind kind = RetroactivePolicy.evaluate(in.t0(), in.now());
		if (kind == RetroactivePolicy.Kind.FUTURE) {
			throw IncidentErrors.t0Invalid("T0 no puede estar en el futuro");
		}
		boolean retroactive = kind == RetroactivePolicy.Kind.RETROACTIVE;
		if (retroactive && (in.t0Reason() == null || in.t0Reason().isBlank())) {
			throw IncidentErrors.t0Invalid("Un T0 retroactivo exige un motivo");
		}
		Ticket ticket = new Ticket(in);
		in.element().connectors().forEach(ref -> ticket.connectors.add(new TicketConnector(ticket, ref)));
		ticket.episodes.add(Episode.open(ticket, 1, in.referenceVersionId(), null, in.t0(), retroactive,
				retroactive ? in.t0Reason() : null, in.actorId(), in.now()));
		return ticket;
	}

	public int getTicketNumber() {
		return ticketNumber;
	}

	/** El codigo, por ejemplo {@code TICKET-086}; lo genera la base al guardar. */
	public String getTicketCode() {
		return ticketCode;
	}

	public UUID getClientRequestId() {
		return clientRequestId;
	}

	public TicketState getState() {
		return state;
	}

	public boolean isOpen() {
		return state == TicketState.OPEN;
	}

	public ElementClass getElementClass() {
		return elementClass;
	}

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public String getEquipmentLabelAtT0() {
		return equipmentLabelAtT0;
	}

	public String getNetworkAtT0() {
		return networkAtT0;
	}

	public String getInfrastructureTypeAtT0() {
		return infrastructureTypeAtT0;
	}

	public ChatbotScope getChatbotScope() {
		return chatbotScope;
	}

	public SiteElementKind getSiteElementKind() {
		return siteElementKind;
	}

	public String getElementName() {
		return elementName;
	}

	public UUID getReferenceEquipmentId() {
		return referenceEquipmentId;
	}

	public String getManualSiteName() {
		return manualSiteName;
	}

	public String getManualLocation() {
		return manualLocation;
	}

	public String getManualMapsUrl() {
		return manualMapsUrl;
	}

	public String getSiteLabelAtT0() {
		return siteLabelAtT0;
	}

	public String getLocationAtT0() {
		return locationAtT0;
	}

	public String getMapsUrlAtT0() {
		return mapsUrlAtT0;
	}

	public IncidentType getIncidentType() {
		return incidentType;
	}

	public ReportOrigin getReportOrigin() {
		return reportOrigin;
	}

	public String getReportOriginDetail() {
		return reportOriginDetail;
	}

	public String getDescription() {
		return description;
	}

	public Priority getPriority() {
		return priority;
	}

	public UUID getCurrentResponsibleId() {
		return currentResponsibleId;
	}

	public String getCurrentResponsibleName() {
		return currentResponsibleName;
	}

	public String getDuplicateKey() {
		return duplicateKey;
	}

	public String getDuplicateOverrideReason() {
		return duplicateOverrideReason;
	}

	public Instant getLastActivityAt() {
		return lastActivityAt;
	}

	/** Los episodios, del primero al ultimo. */
	public List<Episode> getEpisodes() {
		return List.copyOf(episodes);
	}

	/** Los conectores afectados; vacio significa el equipo completo (FR-002). */
	public List<TicketConnector> getConnectors() {
		return List.copyOf(connectors);
	}

	/** El ultimo episodio, que define el estado del ticket. */
	public Episode latestEpisode() {
		return episodes.getLast();
	}

	public Optional<Episode> episode(int ordinal) {
		return episodes.stream().filter(episode -> episode.getOrdinal() == ordinal).findFirst();
	}

	/** Cambia la prioridad vigente; el motivo y el evento los lleva el caso de uso. */
	public void changePriority(Priority newPriority) {
		this.priority = newPriority;
	}

	public void assignResponsible(UUID responsibleId, String responsibleName) {
		this.currentResponsibleId = responsibleId;
		this.currentResponsibleName = responsibleName;
	}

	/** Cambia el tipo de incidencia; debe ser compatible con la clase del elemento (FR-022). */
	public void reclassify(IncidentType newType) {
		if (!newType.allowedFor(elementClass)) {
			throw IncidentErrors.elementInvalid("El tipo de incidencia no corresponde a este elemento");
		}
		this.incidentType = newType;
	}

	public void correctDescription(String newDescription) {
		this.description = newDescription;
	}

	public void correctOrigin(ReportOrigin newOrigin, String newDetail) {
		this.reportOrigin = newOrigin;
		this.reportOriginDetail = newDetail;
	}

	/**
	 * Cierra el ticket cerrando su ultimo episodio (FR-047). El estado del ticket sigue al del episodio. Devuelve la
	 * revision de T4 para que el caso de uso la guarde.
	 */
	public MilestoneRevision close(ClosureData data, UUID by, Instant now) {
		MilestoneRevision revision = latestEpisode().close(data, by, now);
		this.state = TicketState.CLOSED;
		this.lastActivityAt = now;
		return revision;
	}

	/**
	 * Reabre el ticket con un episodio nuevo, con su propio T0 (la hora de la nueva deteccion) y la version de referencias
	 * vigente en ese momento (FR-044, FR-049). Conserva el numero y deja intactos los episodios anteriores. No se reabre un
	 * ticket abierto ni uno cerrado como duplicado o registrado por error. El nuevo T0 no puede ser anterior al cierre del
	 * episodio previo ni estar en el futuro, y una hora anterior al registro exige un motivo.
	 */
	public Episode reopen(String reason, Instant t0, String t0Reason, UUID referenceVersionId, UUID by, Instant now) {
		Episode previous = latestEpisode();
		if (previous.isOpen()) {
			throw IncidentErrors.alreadyOpen();
		}
		if (previous.closureResult().map(ClosureResult::excludedFromIndicators).orElse(false)) {
			throw IncidentErrors.reopenForbidden();
		}
		if (reason == null || reason.isBlank()) {
			throw IncidentErrors.justificationRequired("Reabrir un ticket exige un motivo");
		}
		RetroactivePolicy.Kind kind = RetroactivePolicy.evaluate(t0, now);
		if (kind == RetroactivePolicy.Kind.FUTURE) {
			throw IncidentErrors.t0Invalid("La nueva detección no puede estar en el futuro");
		}
		boolean retroactive = kind == RetroactivePolicy.Kind.RETROACTIVE;
		if (retroactive && (t0Reason == null || t0Reason.isBlank())) {
			throw IncidentErrors.t0Invalid("Una hora de detección anterior al registro exige un motivo");
		}
		Instant closedAt = previous.milestone(MilestoneKind.T4).getOccurredAt();
		if (closedAt != null && t0.isBefore(closedAt)) {
			throw IncidentErrors.t0Invalid("La nueva detección no puede ser anterior al cierre del episodio anterior");
		}
		Episode next = Episode.open(this, previous.getOrdinal() + 1, referenceVersionId, reason.strip(), t0, retroactive,
				retroactive ? t0Reason.strip() : null, by, now);
		episodes.add(next);
		this.state = TicketState.OPEN;
		this.lastActivityAt = now;
		return next;
	}

	/** Anota la ultima actividad del ticket (la «ultima actualizacion» del tablero). */
	public void touch(Instant now) {
		this.lastActivityAt = now;
	}
}
