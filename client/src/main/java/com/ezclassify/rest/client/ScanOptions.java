package com.ezclassify.rest.client;

import java.util.Objects;

/**
 * Immutable choices for one scan operation.
 *
 * <p>Null omits an option.
 */
public final class ScanOptions {
  private final String inputFormat;
  private final String outputFormat;

  private ScanOptions(String inputFormat, String outputFormat) {
    this.inputFormat = inputFormat;
    this.outputFormat = outputFormat;
  }

  /**
   * The declared input view or form; null lets the server recognize it.
   *
   * @return the option, or null when omitted
   */
  public String inputFormat() {
    return inputFormat;
  }

  /**
   * The declared output address; null uses the server default.
   *
   * @return the option, or null when omitted
   */
  public String outputFormat() {
    return outputFormat;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof ScanOptions that)) return false;
    return Objects.equals(inputFormat, that.inputFormat)
        && Objects.equals(outputFormat, that.outputFormat);
  }

  @Override
  public int hashCode() {
    return Objects.hash(inputFormat, outputFormat);
  }

  @Override
  public String toString() {
    return "ScanOptions[inputFormat=" + inputFormat + ", outputFormat=" + outputFormat + "]";
  }

  /**
   * Omit every optional choice.
   *
   * @return the default options
   */
  public static ScanOptions defaults() {
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

    private String inputFormat;
    private String outputFormat;

    /**
     * The declared input view or form; null lets the server recognize it.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder inputFormat(String value) {
      this.inputFormat = value;
      return this;
    }

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
     * Snapshot these choices for an operation.
     *
     * @return immutable options
     */
    public ScanOptions build() {
      return new ScanOptions(inputFormat, outputFormat);
    }
  }
}
