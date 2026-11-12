package com.ezclassify.rest.client;

import static org.junit.jupiter.api.Assertions.*;

import com.ezclassify.rest.contract.Assertion;
import com.ezclassify.rest.contract.AssertionConfidence;
import com.ezclassify.rest.contract.ProcessingContext;
import com.ezclassify.rest.contract.ProcessingRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationOptionsTest {
  private static Assertion fact() {
    return new Assertion("level", "H", AssertionConfidence.CERTAIN, null, "user", null, null);
  }

  @Test
  void builtListsRemainStableAfterCallerAndBuilderMutation() {
    List<String> outputs = new ArrayList<>(List.of("marking/short"));
    var builder =
        ProcessOptions.builder().outputs(outputs).parts(List.of()).suggestFor(List.of("level"));
    var options = builder.build();
    outputs.add("marking/long");
    builder.outputs(List.of("marking/long"));
    assertEquals(List.of("marking/short"), options.outputs());
    assertEquals(List.of(), options.parts());
    assertThrows(UnsupportedOperationException.class, () -> options.outputs().add("other"));
  }

  @Test
  void assertionsAndWholeContextAreDefensiveSnapshots() {
    List<Assertion> assertions = new ArrayList<>(List.of(fact()));
    List<Assertion> whole = new ArrayList<>(List.of(fact()));
    var context =
        new ProcessingContext(ProcessingRole.PART, new ProcessingContext.PartOf("H", whole));
    var options = ProcessOptions.builder().assertions(assertions).context(context).build();
    assertions.clear();
    whole.clear();
    assertEquals(1, options.assertions().size());
    assertEquals(1, options.context().partOf().assertions().size());
    assertThrows(UnsupportedOperationException.class, () -> options.assertions().clear());
    assertThrows(
        UnsupportedOperationException.class, () -> options.context().partOf().assertions().clear());
  }

  @Test
  void omittedEmptyAndFalseChoicesRemainDistinct() {
    assertNull(ProcessOptions.defaults().outputs());
    assertEquals(List.of(), ProcessOptions.builder().outputs(List.of()).build().outputs());
    assertNull(SuggestOptions.defaults().includeGated());
    assertEquals(
        Boolean.FALSE, SuggestOptions.builder().includeGated(false).build().includeGated());
  }

  @Test
  void optionListsRejectNullMembersAtTheBoundary() {
    List<String> outputs = new ArrayList<>();
    outputs.add(null);
    assertThrows(
        NullPointerException.class, () -> ProcessOptions.builder().outputs(outputs).build());
  }
}
