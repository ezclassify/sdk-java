package com.ezclassify.rest.client;

import com.ezclassify.rest.contract.ProcessingContext;
import java.util.List;
import java.util.Objects;

/**
 * Immutable choices for one validate operation.
 *
 * <p>Null omits an option; empty lists and false remain explicit wire choices.
 */
public final class ValidateOptions {
  private final String inputFormat;
  private final String outputFormat;
  private final String surface;
  private final ProcessingContext context;
  private final List<String> parts;

  private ValidateOptions(
      String inputFormat,
      String outputFormat,
      String surface,
      ProcessingContext context,
      List<String> parts) {
    context = OptionSnapshots.context(context);
    parts = parts == null ? null : List.copyOf(parts);
    this.inputFormat = inputFormat;
    this.outputFormat = outputFormat;
    this.surface = surface;
    this.context = context;
    this.parts = parts;
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

  /**
   * The declared surface; null leaves the server default.
   *
   * @return the option, or null when omitted
   */
  public String surface() {
    return surface;
  }

  /**
   * Part markings the whole must cover.
   *
   * @return the option, or null when omitted
   */
  public List<String> parts() {
    return parts;
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
    if (!(other instanceof ValidateOptions that)) return false;
    return Objects.equals(inputFormat, that.inputFormat)
        && Objects.equals(outputFormat, that.outputFormat)
        && Objects.equals(surface, that.surface)
        && Objects.equals(context, that.context)
        && Objects.equals(parts, that.parts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(inputFormat, outputFormat, surface, context, parts);
  }

  @Override
  public String toString() {
    return "ValidateOptions[inputFormat="
        + inputFormat
        + ", outputFormat="
        + outputFormat
        + ", surface="
        + surface
        + ", context="
        + context
        + ", parts="
        + parts
        + "]";
  }

  /**
   * Omit every optional choice.
   *
   * @return the default options
   */
  public static ValidateOptions defaults() {
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
    private String surface;
    private ProcessingContext context;
    private List<String> parts;

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
     * Part markings the whole must cover.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder parts(List<String> value) {
      this.parts = value;
      return this;
    }

    /**
     * Snapshot these choices for an operation.
     *
     * @return immutable options
     */
    public ValidateOptions build() {
      return new ValidateOptions(inputFormat, outputFormat, surface, context, parts);
    }
  }
}
