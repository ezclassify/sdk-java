package com.ezclassify.rest.client;

import com.ezclassify.rest.contract.RenderPreservation;
import java.util.Objects;

/**
 * Immutable choices for one render operation.
 *
 * <p>Null omits an option.
 */
public final class RenderOptions {
  private final String outputFormat;
  private final RenderPreservation preservation;

  private RenderOptions(String outputFormat, RenderPreservation preservation) {
    this.outputFormat = outputFormat;
    this.preservation = preservation;
  }

  /**
   * The declared rendering output address; null uses the server default.
   *
   * @return the option, or null when omitted
   */
  public String outputFormat() {
    return outputFormat;
  }

  /**
   * The required preservation behavior; null uses the server default.
   *
   * @return the option, or null when omitted
   */
  public RenderPreservation preservation() {
    return preservation;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof RenderOptions that)) return false;
    return Objects.equals(outputFormat, that.outputFormat)
        && Objects.equals(preservation, that.preservation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(outputFormat, preservation);
  }

  @Override
  public String toString() {
    return "RenderOptions[outputFormat=" + outputFormat + ", preservation=" + preservation + "]";
  }

  /**
   * Omit every optional choice.
   *
   * @return the default options
   */
  public static RenderOptions defaults() {
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
    private RenderPreservation preservation;

    /**
     * The declared rendering output address; null uses the server default.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder outputFormat(String value) {
      this.outputFormat = value;
      return this;
    }

    /**
     * The required preservation behavior; null uses the server default.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder preservation(RenderPreservation value) {
      this.preservation = value;
      return this;
    }

    /**
     * Snapshot these choices for an operation.
     *
     * @return immutable options
     */
    public RenderOptions build() {
      return new RenderOptions(outputFormat, preservation);
    }
  }
}
