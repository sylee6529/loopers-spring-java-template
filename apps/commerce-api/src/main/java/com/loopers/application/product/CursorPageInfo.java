package com.loopers.application.product;

import lombok.Getter;

import java.util.List;

@Getter
public class CursorPageInfo<T> {

    private final List<T> content;
    private final String nextCursor;
    private final boolean hasNext;

    private CursorPageInfo(List<T> content, String nextCursor, boolean hasNext) {
        this.content = content;
        this.nextCursor = nextCursor;
        this.hasNext = hasNext;
    }

    public static <T> CursorPageInfo<T> of(List<T> content, String nextCursor, boolean hasNext) {
        return new CursorPageInfo<>(content, nextCursor, hasNext);
    }
}
