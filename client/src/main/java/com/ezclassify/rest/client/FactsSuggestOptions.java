package com.ezclassify.rest.client;

import java.util.Objects;

/**
 * Immutable choices for one facts-based suggest operation.
 *
 * <p>Null omits an option; empty lists and false remain explicit wire choices.
 */
public final class FactsSuggestOptions {
  private final String outputFormat;
  private final String surface;
  private final String filter;
  private final Boolean includeGated;

  private FactsSuggestOptions(
      String outputFormat, String surface, String filter, Boolean includeGated) {
    this.outputFormat = outputFormat;
    this.surface = surface;
    this.filter = filter;
    this.includeGated = includeGated;
  }

  /**
   * The declared output address; null uses the server default.
   *
   * @return the option, or null when omitted
   */
  public String outputFormat() {
    return outputFormat;
  }

  /**
   * The declared surface; null leaves the server default.
   *
   * @return the option, or null when omitted
   */
  public String surface() {
    return surface;
  }

  /**
   * Text to narrow the available suggestions.
   *
   * @return the option, or null when omitted
   */
  public String filter() {
    return filter;
  }

  /**
   * Whether to include choices with unmet prerequisites; null uses the server default.
   *
   * @return the option, or null when omitted
   */
  public Boolean includeGated() {
    return includeGated;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof FactsSuggestOptions that)) return false;
    return Objects.equals(outputFormat, that.outputFormat)
        && Objects.equals(surface, that.surface)
        && Objects.equals(filter, that.filter)
        && Objects.equals(includeGated, that.includeGated);
  }

  @Override
  public int hashCode() {
    return Objects.hash(outputFormat, surface, filter, includeGated);
  }

  @Override
  public String toString() {
    return "FactsSuggestOptions[outputFormat="
        + outputFormat
        + ", surface="
        + surface
        + ", filter="
        + filter
        + ", includeGated="
        + includeGated
        + "]";
  }

  /**
   * Omit every optional choice.
   *
   * @return the default options
   */
  public static FactsSuggestOptions defaults() {
    return builder().build();
  }

  /**
   * Start an operation-options builder.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Mutable builder whose build method returns an immutable snapshot. */
  public static final class Builder {
    private Builder() {}

    private String outputFormat;
    private String surface;
    private String filter;
    private Boolean includeGated;

    /**
     * The declared output address; null uses the server default.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder outputFormat(String value) {
      this.outputFormat = value;
      return this;
    }

    /**
     * The declared surface; null leaves the server default.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder surface(String value) {
      this.surface = value;
      return this;
    }

    /**
     * Text to narrow the available suggestions.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder filter(String value) {
      this.filter = value;
      return this;
    }

    /**
     * Whether to include choices with unmet prerequisites; null uses the server default.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder includeGated(Boolean value) {
      this.includeGated = value;
      return this;
    }

    /**
     * Snapshot these choices for an operation.
     *
     * @return immutable options
     */
    public FactsSuggestOptions build() {
      return new FactsSuggestOptions(outputFormat, surface, filter, includeGated);
    }
  }
}
