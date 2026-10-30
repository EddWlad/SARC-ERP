package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.policy.PreferredSourcePolicy;
import com.tidsec.sarc_bakend.modules.consumption.domain.policy.SessionRules;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Sesion de carga reportada por una plataforma. Su identidad natural es (fuente, ambito, ID de la
 * fuente), por eso reimportar la misma sesion no la duplica. Se cuenta en el dia local de inicio y
 * su red sale del equipo del inventario al que se vincula.
 */
@Entity
@Table(name = "energy_session")
public class EnergySession extends BaseAuditableEntity {

	public static final String AUDIT_ENTITY_TYPE = "ENERGY_SESSION";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private SessionSource source;

	@Column(nullable = false, updatable = false, length = 100)
	private String sourceScope;

	@Column(nullable = false, updatable = false, length = 100)
	private String externalSessionId;

	private UUID equipmentId;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ChargingNetwork network;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private InfrastructureType infrastructureType;

	@Column(length = 150)
	private String sourceSite;

	@Column(length = 150)
	private String sourceCharger;

	@Column(length = 80)
	private String sourceSerial;

	@Column(length = 150)
	private String sourceConnector;

	@Column(nullable = false)
	private Instant startedAt;

	private Instant endedAt;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(precision = 12, scale = SessionReading.ENERGY_SCALE)
	private BigDecimal energyKwh;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SessionState state;

	/** Un analista aprobo que esta fuente, no preferente para su equipo, sume (PRD 7.2). */
	@Column(nullable = false)
	private boolean sourceApproved;

	/** Motivo de una sesion Enerlink sin equipo; nulo si esta vinculada. */
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private LinkIssue linkIssue;

	@Column(nullable = false)
	private int revision;

	@Column(nullable = false)
	private UUID importBatchId;

	protected EnergySession() {
	}

	private EnergySession(SessionSource source, String sourceScope, String externalSessionId, UUID importBatchId) {
		super(UUID.randomUUID());
		this.source = source;
		this.sourceScope = sourceScope;
		this.externalSessionId = externalSessionId;
		this.importBatchId = importBatchId;
		this.revision = 1;
	}

	/**
	 * Sesion nueva: validada si el equipo es conocido y la fuente es la preferente (o se aprobo),
	 * pendiente si no; de prueba si asi se marco.
	 */
	public static EnergySession record(SessionSource source, String sourceScope, String externalSessionId,
			SessionReading reading, EquipmentReference equipment, boolean sourceApproved, boolean test,
			UUID importBatchId, OperatingZone zone) {
		EnergySession session = new EnergySession(source, sourceScope, externalSessionId, importBatchId);
		session.apply(reading, zone);
		if (equipment != null) {
			session.attach(equipment);
		}
		session.sourceApproved = sourceApproved;
		session.state = test ? SessionState.TEST : session.officialState();
		return session;
	}

	public boolean hasSameReading(SessionReading reading) {
		return reading().sameAs(reading);
	}

	/**
	 * La lectura identifica al mismo cargador que la sesion: la serie en Wallbox, sin distinguir
	 * mayusculas; ACTIVO y CARGADOR en Enerlink, por su forma normalizada (D05).
	 */
	public boolean identifiesSameCharger(SessionReading reading) {
		return switch (source) {
			case WALLBOX -> sameText(sourceSerial, reading.serial());
			case ENERLINK -> new ExternalKey(sourceSite, sourceCharger).comparable()
					.equals(new ExternalKey(reading.site(), reading.charger()).comparable());
		};
	}

	/** Solo una sesion vinculada cambia de cargador; sin equipo no hay vinculo que proteger. */
	public boolean changesCharger(SessionReading reading) {
		return equipmentId != null && !identifiesSameCharger(reading);
	}

	/**
	 * Correccion aprobada desde una reimportacion: guarda la version anterior y aplica la nueva. Nunca
	 * se sobrescribe sin dejar rastro (PRD RF-003.6).
	 */
	public SessionRevision correct(SessionReading reading, UUID batchId, OperatingZone zone, UUID actorId,
			Instant at) {
		if (changesCharger(reading)) {
			throw new BusinessRuleException("SESSION_EQUIPMENT_CHANGED",
					"La correccion cambia el cargador de la sesion; debe reasignarse de forma explicita");
		}
		SessionRevision previous = SessionRevision.of(this, batchId, actorId, at);
		apply(reading, zone);
		this.importBatchId = batchId;
		this.revision++;
		return previous;
	}

	/**
	 * Conciliacion explicita: la reimportacion trae la sesion en otro cargador y el analista la
	 * reasigno. Cambia lectura y equipo juntos (nulo = pendiente de vincular) y conserva la version
	 * anterior con su equipo. {@code sourceApproved}: se aprobo la fuente no preferente del nuevo equipo.
	 */
	public SessionRevision reassign(SessionReading reading, EquipmentReference equipment, boolean sourceApproved,
			UUID batchId, OperatingZone zone, UUID actorId, Instant at) {
		SessionRevision previous = SessionRevision.of(this, batchId, actorId, at);
		apply(reading, zone);
		detach();
		if (equipment != null) {
			attach(equipment);
		}
		this.sourceApproved = sourceApproved;
		this.importBatchId = batchId;
		this.revision++;
		if (state != SessionState.TEST) {
			state = officialState();
		}
		return previous;
	}

	/**
	 * Vincula una sesion pendiente con el equipo ya identificado. Si la fuente no es la preferente
	 * del equipo queda por aprobar, igual que al importarla con el equipo ya conocido.
	 */
	public void link(EquipmentReference equipment) {
		link(equipment, false);
	}

	/** {@code sourceApproved}: en la vista previa se aprobo la fuente no preferente del equipo. */
	public void link(EquipmentReference equipment, boolean sourceApproved) {
		if (equipmentId != null) {
			throw new BusinessRuleException("SESSION_ALREADY_LINKED", "La sesion ya esta vinculada a un equipo");
		}
		attach(equipment);
		this.sourceApproved = this.sourceApproved || sourceApproved;
		if (state == SessionState.PENDING_LINK) {
			state = officialState();
		}
	}

	/** Registra por que la sesion sigue sin equipo; no afecta a una sesion vinculada. */
	public void markUnlinkable(LinkIssue issue) {
		if (equipmentId == null) {
			this.linkIssue = issue;
		}
	}

	/** Un analista aprueba que esta fuente no preferente sume para su equipo. */
	public void approveSource() {
		if (state != SessionState.AWAITING_APPROVAL) {
			throw new BusinessRuleException("SESSION_NOT_AWAITING_APPROVAL",
					"La sesion no espera aprobacion de fuente");
		}
		sourceApproved = true;
		state = SessionState.VALIDATED;
	}

	/** Marca o desmarca como prueba; al desmarcar vuelve a su estado segun la vinculacion. */
	public void markTest(boolean test) {
		state = test ? SessionState.TEST : officialState();
	}

	public SessionReading reading() {
		return new SessionReading(startedAt, endedAt, energyKwh, sourceSite, sourceCharger, sourceSerial,
				sourceConnector);
	}

	private void apply(SessionReading reading, OperatingZone zone) {
		SessionRules.requireValid(reading);
		this.startedAt = reading.startedAt();
		this.endedAt = reading.endedAt();
		this.startDate = zone.dateOf(reading.startedAt());
		this.energyKwh = reading.energyKwh();
		this.sourceSite = reading.site();
		this.sourceCharger = reading.charger();
		this.sourceSerial = reading.serial();
		this.sourceConnector = reading.connector();
	}

	private void attach(EquipmentReference equipment) {
		this.equipmentId = equipment.id();
		this.network = equipment.network();
		this.infrastructureType = equipment.type();
		this.linkIssue = null;
	}

	private void detach() {
		this.equipmentId = null;
		this.network = null;
		this.infrastructureType = null;
	}

	/**
	 * Estado que le corresponde fuera de las pruebas: sin equipo, pendiente; con equipo, validada si
	 * la fuente es la preferente o se aprobo; si no, por aprobar. Es la unica regla para importar,
	 * vincular, reasignar y reincorporar pruebas.
	 */
	private SessionState officialState() {
		if (equipmentId == null) {
			return SessionState.PENDING_LINK;
		}
		return sourceApproved || PreferredSourcePolicy.isPreferred(source, network, infrastructureType)
				? SessionState.VALIDATED
				: SessionState.AWAITING_APPROVAL;
	}

	private static boolean sameText(String current, String incoming) {
		return current == null ? incoming == null : incoming != null && current.equalsIgnoreCase(incoming);
	}

	public SessionSource getSource() {
		return source;
	}

	public String getSourceScope() {
		return sourceScope;
	}

	public String getExternalSessionId() {
		return externalSessionId;
	}

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public ChargingNetwork getNetwork() {
		return network;
	}

	public InfrastructureType getInfrastructureType() {
		return infrastructureType;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public BigDecimal getEnergyKwh() {
		return energyKwh;
	}

	public SessionState getState() {
		return state;
	}

	public boolean isSourceApproved() {
		return sourceApproved;
	}

	public LinkIssue getLinkIssue() {
		return linkIssue;
	}

	public int getRevision() {
		return revision;
	}

	public UUID getImportBatchId() {
		return importBatchId;
	}
}
