package com.ezclassify.rest.client;

import com.ezclassify.rest.contract.Assertion;
import com.ezclassify.rest.contract.ProcessingContext;
import java.util.List;

/** Immutable copies of the caller-owned collections an options value holds. */
final class OptionSnapshots {
  private OptionSnapshots() {}

  /** Copy a list of assertions. An assertion is an immutable record of strings, so it is shared. */
  static List<Assertion> assertions(List<Assertion> values) {
    return values == null ? null : List.copyOf(values);
  }

  /** Copy a processing context, including the assertions of the whole marking it names. */
  static ProcessingContext context(ProcessingContext value) {
    if (value == null) return null;
    ProcessingContext.PartOf partOf = value.partOf();
    if (partOf == null) return value;
    return new ProcessingContext(
        value.role(),
        new ProcessingContext.PartOf(
            partOf.rendered(),
            partOf.assertions() == null ? null : List.copyOf(partOf.assertions())));
  }
}
