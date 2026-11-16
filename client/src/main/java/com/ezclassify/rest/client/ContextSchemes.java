package com.ezclassify.rest.client;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The schemes an {@link EzClassifyContext} holds, each reached by its key. {@link
 * EzClassifyContext#scheme()} stays the primary scheme; this collection reaches any scheme the
 * Context holds, such as a crosswalk's source or target.
 *
 * <p>The key is the {@code selector} that {@link EzClassifyContext#describeContext()} lists under
 * {@code schemes}, and nothing else addresses a scheme. The server is the authority on which keys
 * exist, so nothing here consults a local list.
 */
public final class ContextSchemes {
  private final EzClassifyContext ctx;
  private final ConcurrentHashMap<String, ContextScheme> views = new ConcurrentHashMap<>();

  ContextSchemes(EzClassifyContext ctx) {
    this.ctx = ctx;
  }

  /**
   * The view of the scheme this key names. Every operation and read on the view sends the key as
   * the {@code scheme} query parameter; the operations take no key of their own.
   *
   * <p>Getting a view sends no request. A key the Context does not hold answers 404 with code
   * {@code scheme_not_found} on the view's first call and is never answered by the primary scheme
   * instead. Each view's answers are cached apart from every other view's.
   *
   * @param key the Context-local key of the scheme
   * @return the view of that scheme, the same instance on every call with the same key
   * @throws IllegalArgumentException if {@code key} is null or blank
   */
  public ContextScheme get(String key) {
    Args.requiredId(key, "key");
    return views.computeIfAbsent(key, k -> new ContextScheme(ctx, k));
  }
}
