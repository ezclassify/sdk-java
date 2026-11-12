package com.ezclassify.rest.contract;

/**
 * The resolved composition and result returned by a Context operation.
 *
 * @param <T> the operation-specific result type
 * @param resolution the single Context composition used throughout the operation
 * @param result the operation's own result
 * @param responseProjection how a document operation's response was projected, or {@code null} for
 *     an operation that does not take a document
 */
public record ContextOperationResult<T>(
    ContextResolution resolution, T result, DocumentResponseProjection responseProjection) {}
