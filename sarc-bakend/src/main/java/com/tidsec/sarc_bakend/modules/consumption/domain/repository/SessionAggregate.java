package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReviewStatus;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Sesiones de una misma combinacion de dimensiones; {@code energyKwh} es nula si ninguna informa lectura. */
public record SessionAggregate(LocalDate date, ChargingNetwork network, SessionSource source, InfrastructureType type,
		UUID equipmentId, EnergyReviewStatus energyReview, long sessions, BigDecimal energyKwh, long readings) {
}
