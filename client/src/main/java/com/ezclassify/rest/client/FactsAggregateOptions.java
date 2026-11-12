package com.ezclassify.rest.client;

import com.ezclassify.rest.contract.ProcessingContext;
import java.util.Objects;

/**
 * Immutable choices for one facts-based aggregate operation.
 *
 * <p>Null omits an option; empty lists and false remain explicit wire choices.
 */
public final class FactsAggregateOptions {
  private final String outputFormat;
  private final String surface;
  private final ProcessingContext context;

  private FactsAggregateOptions(String outputFormat, String surface, ProcessingContext context) {
    context = OptionSnapshots.context(context);
    this.outputFormat = outputFormat;
    this.surface = surface;
    this.context = context;
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
   * Return a defensive copy of context.
   *
   * @return the option, or null when omitted
   */
  public ProcessingContext context() {
    return OptionSnapshots.context(context);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof FactsAggregateOptions that)) return false;
    return Objects.equals(outputFormat, that.outputFormat)
        && Objects.equals(surface, that.surface)
        && Objects.equals(context, that.context);
  }

  @Override
  public int hashCode() {
    return Objects.hash(outputFormat, surface, context);
  }

  @Override
  public String toString() {
    return "FactsAggregateOptions[outputFormat="
        + outputFormat
        + ", surface="
        + surface
        + ", context="
        + context
        + "]";
  }

  /**
   * Omit every optional choice.
   *
   * @return the default options
   */
  public static FactsAggregateOptions defaults() {
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
    private ProcessingContext context;

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
     * The caller-supplied marking role and whole-marking context.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder context(ProcessingContext value) {
      this.context = value;
      return this;
    }

    /**
     * Snapshot these choices for an operation.
     *
     * @return immutable options
     */
    public FactsAggregateOptions build() {
      return new FactsAggregateOptions(outputFormat, surface, context);
    }
  }
}
