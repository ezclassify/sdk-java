package com.ezclassify.rest.client;

import com.ezclassify.rest.contract.Assertion;
import com.ezclassify.rest.contract.ProcessingContext;
import java.util.List;
import java.util.Objects;

/**
 * Immutable choices for one process operation.
 *
 * <p>Null omits an option; empty lists and false remain explicit wire choices.
 */
public final class ProcessOptions {
  private final String inputFormat;
  private final String outputFormat;
  private final String surface;
  private final List<Assertion> assertions;
  private final ProcessingContext context;
  private final List<String> parts;
  private final List<String> outputs;
  private final List<String> suggestFor;

  private ProcessOptions(
      String inputFormat,
      String outputFormat,
      String surface,
      List<Assertion> assertions,
      ProcessingContext context,
      List<String> parts,
      List<String> outputs,
      List<String> suggestFor) {
    assertions = OptionSnapshots.assertions(assertions);
    context = OptionSnapshots.context(context);
    parts = parts == null ? null : List.copyOf(parts);
    outputs = outputs == null ? null : List.copyOf(outputs);
    suggestFor = suggestFor == null ? null : List.copyOf(suggestFor);
    this.inputFormat = inputFormat;
    this.outputFormat = outputFormat;
    this.surface = surface;
    this.assertions = assertions;
    this.context = context;
    this.parts = parts;
    this.outputs = outputs;
    this.suggestFor = suggestFor;
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
   * Output addresses to write in the renders map.
   *
   * @return the option, or null when omitted
   */
  public List<String> outputs() {
    return outputs;
  }

  /**
   * Category ids whose picker alternatives are requested.
   *
   * @return the option, or null when omitted
   */
  public List<String> suggestFor() {
    return suggestFor;
  }

  /**
   * Return a defensive copy of assertions.
   *
   * @return the option, or null when omitted
   */
  public List<Assertion> assertions() {
    return OptionSnapshots.assertions(assertions);
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
    if (!(other instanceof ProcessOptions that)) return false;
    return Objects.equals(inputFormat, that.inputFormat)
        && Objects.equals(outputFormat, that.outputFormat)
        && Objects.equals(surface, that.surface)
        && Objects.equals(assertions, that.assertions)
        && Objects.equals(context, that.context)
        && Objects.equals(parts, that.parts)
        && Objects.equals(outputs, that.outputs)
        && Objects.equals(suggestFor, that.suggestFor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        inputFormat, outputFormat, surface, assertions, context, parts, outputs, suggestFor);
  }

  @Override
  public String toString() {
    return "ProcessOptions[inputFormat="
        + inputFormat
        + ", outputFormat="
        + outputFormat
        + ", surface="
        + surface
        + ", assertions="
        + assertions
        + ", context="
        + context
        + ", parts="
        + parts
        + ", outputs="
        + outputs
        + ", suggestFor="
        + suggestFor
        + "]";
  }

  /**
   * Omit every optional choice.
   *
   * @return the default options
   */
  public static ProcessOptions defaults() {
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
    private List<Assertion> assertions;
    private ProcessingContext context;
    private List<String> parts;
    private List<String> outputs;
    private List<String> suggestFor;

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
     * Chosen facts to settle together with the text.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder assertions(List<Assertion> value) {
      this.assertions = value;
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
     * Output addresses to write in the renders map.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder outputs(List<String> value) {
      this.outputs = value;
      return this;
    }

    /**
     * Category ids whose picker alternatives are requested.
     *
     * @param value the option value, or null to omit it
     * @return this builder
     */
    public Builder suggestFor(List<String> value) {
      this.suggestFor = value;
      return this;
    }

    /**
     * Snapshot these choices for an operation.
     *
     * @return immutable options
     */
    public ProcessOptions build() {
      return new ProcessOptions(
          inputFormat, outputFormat, surface, assertions, context, parts, outputs, suggestFor);
    }
  }
}
