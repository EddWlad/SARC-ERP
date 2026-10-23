package com.tidsec.sarc_bakend.modules.inventory.domain.policy;

import java.util.regex.Pattern;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/**
 * Reglas de identificacion por red y tipo (PRD 6.2):
 * <ul>
 * <li>Punto KIA: 1 conector, codigo asignado de 6 digitos sin cero inicial, UID/PUK obligatorio.</li>
 * <li>Punto JAC o CONECTZ: 1 conector, codigo igual a la serie, UID/PUK obligatorio.</li>
 * <li>Electrolinera de cualquier red: 1 a 3 conectores, codigo igual a la serie, UID/PUK opcional.</li>
 * </ul>
 * Solo los puntos de carga tienen estado de uso.
 */
public record EquipmentClassification(ChargingNetwork network, InfrastructureType type) {

	private static final Pattern KIA_POINT_CODE = Pattern.compile("[1-9][0-9]{5}");

	public boolean isPoint() {
		return type == InfrastructureType.CHARGING_POINT;
	}

	public int maxConnectors() {
		return isPoint() ? 1 : 3;
	}

	/** El codigo asignado lo elige el usuario solo en puntos KIA; en el resto es la serie. */
	public boolean usesSerialAsCode() {
		return !(isPoint() && network == ChargingNetwork.KIA);
	}

	public String resolveAssignedCode(String serial, String requestedCode) {
		boolean requested = requestedCode != null && !requestedCode.isBlank();
		if (usesSerialAsCode()) {
			if (requested && !requestedCode.strip().equals(serial)) {
				throw new BusinessRuleException("ASSIGNED_CODE_IS_SERIAL",
						"En este tipo de equipo el codigo asignado es la serie y se genera automaticamente");
			}
			return serial;
		}
		if (!requested || !KIA_POINT_CODE.matcher(requestedCode.strip()).matches()) {
			throw new BusinessRuleException("INVALID_ASSIGNED_CODE",
					"El codigo de un punto KIA tiene 6 digitos y no empieza en cero");
		}
		return requestedCode.strip();
	}

	public void requireUidPuk(String uidPuk) {
		if (isPoint() && uidPuk == null) {
			throw new BusinessRuleException("UID_PUK_REQUIRED", "El UID/PUK es obligatorio en los puntos de carga");
		}
	}

	public void requireUsageStatus(UsageStatus usageStatus) {
		if (isPoint() && usageStatus == null) {
			throw new BusinessRuleException("USAGE_STATUS_REQUIRED",
					"El estado de uso es obligatorio en los puntos de carga");
		}
		if (!isPoint() && usageStatus != null) {
			throw new BusinessRuleException("USAGE_STATUS_NOT_APPLICABLE",
					"Las electrolineras no tienen estado de uso");
		}
	}

	public void requireConnectorCount(int count) {
		if (count < 1 || count > maxConnectors()) {
			throw new BusinessRuleException("INVALID_CONNECTOR_COUNT", isPoint()
					? "Un punto de carga tiene exactamente 1 conector"
					: "Una electrolinera tiene entre 1 y 3 conectores");
		}
	}
}
