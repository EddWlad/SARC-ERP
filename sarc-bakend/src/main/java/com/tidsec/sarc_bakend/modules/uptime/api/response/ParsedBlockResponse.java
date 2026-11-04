package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.math.BigDecimal;

import com.tidsec.sarc_bakend.modules.uptime.application.port.MeasurementBlockParser;

import io.swagger.v3.oas.annotations.media.Schema;

public record ParsedBlockResponse(BigDecimal available, BigDecimal occupied, BigDecimal fault, BigDecimal offline,
		@Schema(nullable = true) String reportedPercent) {

	public static ParsedBlockResponse from(MeasurementBlockParser.Block block) {
		return new ParsedBlockResponse(block.available(), block.occupied(), block.fault(), block.offline(),
				block.reportedPercent() == null ? null : block.reportedPercent().toPlainString());
	}
}
