package com.tidsec.sarc_bakend.modules.consumption.application.command;

import java.util.UUID;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReviewStatus;

public record DecideEnergyReviewCommand(UUID id, EnergyReviewStatus decision, String justification, long version) {
}
