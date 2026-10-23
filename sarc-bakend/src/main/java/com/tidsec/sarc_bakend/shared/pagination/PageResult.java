package com.tidsec.sarc_bakend.shared.pagination;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
		return new PageResult<>(content.stream().<R>map(mapper).toList(), page, size, totalElements, totalPages);
	}
}
