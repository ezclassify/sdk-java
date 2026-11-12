package com.ezclassify.rest.client;

/**
 * Response-cache choice for one Context binding.
 *
 * @param disableCache send every operation to the server, including normally cached scheme reads;
 *     access decisions are always sent to the server
 */
public record ContextCacheOptions(boolean disableCache) {
  /**
   * Use bounded scheme caching.
   *
   * @return the default cache choice
   */
  public static ContextCacheOptions defaults() {
    return new ContextCacheOptions(false);
  }
}
