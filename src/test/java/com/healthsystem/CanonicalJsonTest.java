package com.healthsystem;
import com.healthsystem.common.json.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CanonicalJsonTest {
 @Test void keysAndNumericallyEquivalentValuesHaveSameHash() {
  assertThat(CanonicalJson.text(JsonValues.read("{\"b\":1.00,\"a\":{\"z\":2,\"x\":true}}"))).isEqualTo("{\"a\":{\"x\":true,\"z\":2},\"b\":1}");
  assertThat(CanonicalJson.hash(JsonValues.read("{\"b\":1.00,\"a\":2}"))).isEqualTo(CanonicalJson.hash(JsonValues.read("{\"a\":2,\"b\":1}")));
 }
 @Test void actualChangesAndArrayOrderingChangeHash() {
  var original=CanonicalJson.hash(JsonValues.read("[1,2]"));
  assertThat(CanonicalJson.hash(JsonValues.read("[2,1]"))).isNotEqualTo(original);
  assertThat(CanonicalJson.hash(JsonValues.read("[1,3]"))).isNotEqualTo(original);
 }
}
