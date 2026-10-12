package com.tpm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FractionalIndexTest {

    @Test
    void firstKeyIsTheMiddleDigit() {
        assertThat(FractionalIndex.between(null, null)).isEqualTo("V");
    }

    @ParameterizedTest
    @CsvSource({
            "'', V",       // before
            "V, ''",       // after (upper = null)
            "V, W",        // consecutive digits -> needs an extra digit
            "a, a1",       // prefix relationship
            "zz, ''",      // near the end of the key space
            "'', 01",      // near the start of the key space
            "A0V, A1"})
    void generatesKeyStrictlyBetween(String lower, String upper) {
        var lo = lower.isEmpty() ? null : lower;
        var hi = upper.isEmpty() ? null : upper;
        var mid = FractionalIndex.between(lo, hi);
        if (lo != null) assertThat(mid).isGreaterThan(lo);
        if (hi != null) assertThat(mid).isLessThan(hi);
        assertThat(mid).doesNotEndWith("0");
    }

    @Test
    void rejectsInvalidBounds() {
        assertThatThrownBy(() -> FractionalIndex.between("b", "a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FractionalIndex.between("a", "a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FractionalIndex.between("a0", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FractionalIndex.between("a!", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repeatedInsertsAtTheSameSpotStayOrderedAndGrowSlowly() {
        // Worst case for fractional indexing: always insert right after the same element.
        var lower = "V";
        var upper = "W";
        for (int i = 0; i < 1_000; i++) {
            var mid = FractionalIndex.between(lower, upper);
            assertThat(mid).isGreaterThan(lower).isLessThan(upper);
            upper = mid;
        }
        assertThat(upper.length()).isLessThan(200);
    }

    /** Property test: random inserts into a list keep string order == list order. */
    @RepeatedTest(20)
    void randomInsertionsPreserveOrder() {
        var random = new Random();
        var keys = new ArrayList<String>();
        for (int i = 0; i < 300; i++) {
            int index = random.nextInt(keys.size() + 1);
            var lower = index == 0 ? null : keys.get(index - 1);
            var upper = index == keys.size() ? null : keys.get(index);
            keys.add(index, FractionalIndex.between(lower, upper));
        }
        assertThat(keys).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void evenlySpacedKeysAreSortedAndShort() {
        var keys = FractionalIndex.evenlySpaced(5_000);
        assertThat(keys).hasSize(5_000).isSorted().doesNotHaveDuplicates();
        assertThat(keys).allSatisfy(k -> assertThat(k).hasSizeLessThanOrEqualTo(3).doesNotEndWith("0"));
        // ...and you can still insert between any neighbours afterwards.
        assertThat(FractionalIndex.between(keys.get(10), keys.get(11))).isBetween(keys.get(10), keys.get(11));
    }
}
