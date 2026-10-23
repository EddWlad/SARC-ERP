package com.tidsec.sarc_bakend.shared.pagination;

public record PageQuery(int page, int size) {

	public static final int MAX_SIZE = 100;

	public PageQuery {
		if (page < 0) {
			throw new IllegalArgumentException("page debe ser >= 0");
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new IllegalArgumentException("size debe estar entre 1 y " + MAX_SIZE);
		}
	}
}
