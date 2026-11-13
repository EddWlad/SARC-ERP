package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.util.List;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.WindowView;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

/** Pagina del historial de ventanas. */
public record WindowPageResponse(List<WindowResponse> content, int page, int size, long totalElements,
		int totalPages) {

	public static WindowPageResponse from(PageResult<WindowView> result) {
		return new WindowPageResponse(result.content().stream().map(WindowResponse::from).toList(), result.page(),
				result.size(), result.totalElements(), result.totalPages());
	}
}
