package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.PointTestResult;

/** Ventanas que cambiaron y ventanas abiertas del rango que no se pudieron marcar. */
public record PointTestResultResponse(int affectedWindows, int openWindowsNotMarked) {

	public static PointTestResultResponse from(PointTestResult result) {
		return new PointTestResultResponse(result.affectedWindows(), result.openWindowsNotMarked());
	}
}
