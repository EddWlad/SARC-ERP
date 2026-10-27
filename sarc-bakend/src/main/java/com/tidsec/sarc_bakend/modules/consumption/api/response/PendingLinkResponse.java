package com.tidsec.sarc_bakend.modules.consumption.api.response;

import com.tidsec.sarc_bakend.modules.consumption.application.result.PendingLinkResult;

public record PendingLinkResponse(int linked, int awaitingApproval, int stillPending) {

	public static PendingLinkResponse from(PendingLinkResult result) {
		return new PendingLinkResponse(result.linked(), result.awaitingApproval(), result.stillPending());
	}
}
