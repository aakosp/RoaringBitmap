package org.roaringbitmap;

import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.roaringbitmap.RandomisedTestData.ITERATIONS;
import static org.roaringbitmap.RandomisedTestData.randomBitmap;
import static org.roaringbitmap.Util.toUnsignedLong;

public class Fuzzer {

  @FunctionalInterface
  interface IntBitmapPredicate {
    boolean test(int index, RoaringBitmap bitmap);
  }

  @FunctionalInterface
  interface RangeBitmapPredicate {
    boolean test(long min, long max, RoaringBitmap bitmap);
  }

  public static <T> void verifyInvariance(String testName, T value, Function<RoaringBitmap, T> func) {
    verifyInvariance(testName, ITERATIONS, 1 << 9, value, func);
  }

  public static <T> void verifyInvariance(String testName,
                                          int count,
                                          int maxKeys,
                                          T value,
                                          Function<RoaringBitmap, T> func) {
    IntStream.range(0, count)
            .parallel()
            .mapToObj(i -> randomBitmap(maxKeys))
            .forEach(bitmap -> {
              try {
                Assert.assertEquals(value, func.apply(bitmap));
              } catch (Throwable e) {
                Reporter.report(testName, ImmutableMap.of("value", value), e, bitmap);
                throw e;
              }
            });
  }



  public static void verifyInvariance(String testName, int maxKeys, RangeBitmapPredicate pred) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    IntStream.range(0, ITERATIONS)
            .parallel()
            .mapToObj(i -> randomBitmap(maxKeys))
            .forEach(bitmap -> {
              long min = random.nextLong(1L << 32);
              long max = random.nextLong(min,1L << 32);
              try {
                Assert.assertTrue(pred.test(min, max, bitmap));
              } catch (Throwable e) {
                Reporter.report(testName, ImmutableMap.of("min", min, "max", max), e, bitmap);
                throw e;
              }
            });
  }

  public static <T> void verifyInvariance(String testName,
                                          Predicate<RoaringBitmap> validity,
                                          Function<RoaringBitmap, T> left,
                                          Function<RoaringBitmap, T> right) {
    verifyInvariance(testName, ITERATIONS, 1 << 8, validity, left, right);
  }

  public static <T> void verifyInvariance(String testName,
                                          int count,
                                          int maxKeys,
                                          Predicate<RoaringBitmap> validity,
                                          Function<RoaringBitmap, T> left,
                                          Function<RoaringBitmap, T> right) {
    IntStream.range(0, count)
            .parallel()
            .mapToObj(i -> randomBitmap(maxKeys))
            .filter(validity)
            .forEach(bitmap -> {
              try {
                Assert.assertEquals(left.apply(bitmap), right.apply(bitmap));
              } catch (Throwable e) {
                Reporter.report(testName, ImmutableMap.of(), e, bitmap);
                throw e;
              }});
  }

  public static <T> void verifyInvariance(String testName,
                                          BiFunction<RoaringBitmap, RoaringBitmap, T> left,
                                          BiFunction<RoaringBitmap, RoaringBitmap, T> right) {
    verifyInvariance(testName, ITERATIONS, 1 << 8, left, right);
  }

  public static <T> void verifyInvariance(String testName,
                                          BiPredicate<RoaringBitmap, RoaringBitmap> validity,
                                          BiFunction<RoaringBitmap, RoaringBitmap, T> left,
                                          BiFunction<RoaringBitmap, RoaringBitmap, T> right) {
    verifyInvariance(testName, validity, ITERATIONS, 1 << 8, left, right);
  }


  public static <T> void verifyInvariance(String testName,
                                          int count,
                                          int maxKeys,
                                          BiFunction<RoaringBitmap, RoaringBitmap, T> left,
                                          BiFunction<RoaringBitmap, RoaringBitmap, T> right) {
    verifyInvariance(testName, (l, r) -> true, count, maxKeys, left, right);
  }

  public static <T> void verifyInvariance(String testName,
                                          BiPredicate<RoaringBitmap, RoaringBitmap> validity,
                                          int count,
                                          int maxKeys,
                                          BiFunction<RoaringBitmap, RoaringBitmap, T> left,
                                          BiFunction<RoaringBitmap, RoaringBitmap, T> right) {
    IntStream.range(0, count)
            .parallel()
            .forEach(i -> {
              RoaringBitmap one = randomBitmap(maxKeys);
              RoaringBitmap two = randomBitmap(maxKeys);
              if (validity.test(one, two)) {
                try {
                  Assert.assertEquals(left.apply(one, two), right.apply(one, two));
                } catch (Throwable t) {
                  Reporter.report(testName, ImmutableMap.of(), t, one, two);
                  throw t;
                }
              }
            });
  }

  public static void verifyInvariance(String testName,
                                      Predicate<RoaringBitmap> validity,
                                      IntBitmapPredicate predicate) {
    verifyInvariance(testName, validity, ITERATIONS, 1 << 3, predicate);
  }

  public static void verifyInvariance(String testName,
                                      Predicate<RoaringBitmap> validity,
                                      int count,
                                      int maxKeys,
                                      IntBitmapPredicate predicate) {
    IntStream.range(0, count)
            .parallel()
            .mapToObj(i -> randomBitmap(maxKeys))
            .filter(validity)
            .forEach(bitmap -> {
              for (int i = 0; i < bitmap.getCardinality(); ++i) {
                try {
                  Assert.assertTrue(predicate.test(i, bitmap));
                } catch (Throwable t) {
                  Reporter.report(testName, ImmutableMap.of("index", i), t, bitmap);
                  throw t;
                }
              }
            });
  }

  public static void verifyInvariance(Consumer<RoaringBitmap> action) {
    verifyInvariance(rb -> true, action);
  }

  public static void verifyInvariance(Predicate<RoaringBitmap> validity,
                                      Consumer<RoaringBitmap> action) {
    verifyInvariance(validity, ITERATIONS, 1 << 3, action);
  }

  public static void verifyInvariance(Predicate<RoaringBitmap> validity,
                                      int count,
                                      int maxKeys,
                                      Consumer<RoaringBitmap> action) {
    IntStream.range(0, count)
            .parallel()
            .mapToObj(i -> randomBitmap(maxKeys))
            .filter(validity)
            .forEach(action);
  }

  @Test
  public void rankSelectInvariance() {
    verifyInvariance("rankSelectInvariance", bitmap -> !bitmap.isEmpty(), (i, rb) -> rb.rank(rb.select(i)) == i + 1);
  }

  @Test
  public void selectContainsInvariance() {
    verifyInvariance("rankSelectInvariance", bitmap -> !bitmap.isEmpty(), (i, rb) -> rb.contains(rb.select(i)));
  }

  @Test
  public void firstSelect0Invariance() {
    verifyInvariance("rankSelectInvariance", bitmap -> !bitmap.isEmpty(),
            bitmap -> bitmap.first(),
            bitmap -> bitmap.select(0));
  }

  @Test
  public void lastSelectCardinalityInvariance() {
    verifyInvariance("rankSelectInvariance", bitmap -> !bitmap.isEmpty(),
            bitmap -> bitmap.last(),
            bitmap -> bitmap.select(bitmap.getCardinality() - 1));
  }

  @Test
  public void andCardinalityInvariance() {
    verifyInvariance("andCardinalityInvariance", ITERATIONS, 1 << 9,
            (l, r) -> RoaringBitmap.and(l, r).getCardinality(),
            (l, r) -> RoaringBitmap.andCardinality(l, r));
  }

  @Test
  public void orCardinalityInvariance() {
    verifyInvariance("orCardinalityInvariance", ITERATIONS, 1 << 9,
            (l, r) -> RoaringBitmap.or(l, r).getCardinality(),
            (l, r) -> RoaringBitmap.orCardinality(l, r));
  }

  @Test
  public void xorCardinalityInvariance() {
    verifyInvariance("xorCardinalityInvariance", ITERATIONS, 1 << 9,
            (l, r) -> RoaringBitmap.xor(l, r).getCardinality(),
            (l, r) -> RoaringBitmap.xorCardinality(l, r));
  }

  @Test
  public void containsContainsInvariance() {
    verifyInvariance("containsContainsInvariance", (l, r) -> l.contains(r) && !r.equals(l),
            (l, r) -> false,
            (l, r) -> !r.contains(l));
  }

  @Test
  public void containsAndInvariance() {
    verifyInvariance("containsAndInvariance", (l, r) -> l.contains(r), (l, r) -> RoaringBitmap.and(l, r).equals(r));
  }


  @Test
  public void limitCardinalityEqualsSelf() {
    verifyInvariance("limitCardinalityEqualsSelf", true, rb -> rb.equals(rb.limit(rb.getCardinality())));
  }

  @Test
  public void limitCardinalityXorCardinalityInvariance() {
    verifyInvariance("limitCardinalityXorCardinalityInvariance", rb -> true,
            rb -> rb.getCardinality(),
            rb -> rb.getCardinality() / 2
                    + RoaringBitmap.xorCardinality(rb, rb.limit(rb.getCardinality() / 2)));
  }

  @Test
  public void containsRangeFirstLastInvariance() {
    verifyInvariance("containsRangeFirstLastInvariance", true,
            rb -> RoaringBitmap.add(rb.clone(), toUnsignedLong(rb.first()), toUnsignedLong(rb.last()))
                               .contains(toUnsignedLong(rb.first()), toUnsignedLong(rb.last())));
  }

  @Test
  public void intersectsRangeFirstLastInvariance() {
    verifyInvariance("intersectsRangeFirstLastInvariance", true, rb -> rb.intersects(toUnsignedLong(rb.first()), toUnsignedLong(rb.last())));
  }

  @Test
  public void containsSelf() {
    verifyInvariance("containsSelf", true, rb -> rb.contains(rb.clone()));
  }

  @Test
  public void containsSubset() {
    verifyInvariance("containsSubset", true, rb -> rb.contains(rb.limit(rb.getCardinality() / 2)));
  }

  @Test
  public void andCardinalityContainsInvariance() {
    verifyInvariance("andCardinalityContainsInvariance", (l, r) -> RoaringBitmap.andCardinality(l, r) == 0,
            (l, r) -> false,
            (l, r) -> l.contains(r) || r.contains(l));
  }

  @Test
  public void sizeOfUnionOfDisjointSetsEqualsSumOfSizes() {
    verifyInvariance("sizeOfUnionOfDisjointSetsEqualsSumOfSizes", (l, r) -> RoaringBitmap.andCardinality(l, r) == 0,
            (l, r) -> l.getCardinality() + r.getCardinality(),
            (l, r) -> RoaringBitmap.orCardinality(l, r));
  }

  @Test
  public void sizeOfDifferenceOfDisjointSetsEqualsSumOfSizes() {
    verifyInvariance("sizeOfDifferenceOfDisjointSetsEqualsSumOfSizes", (l, r) -> RoaringBitmap.andCardinality(l, r) == 0,
            (l, r) -> l.getCardinality() + r.getCardinality(),
            (l, r) -> RoaringBitmap.xorCardinality(l, r));
  }

  @Test
  public void equalsSymmetryInvariance() {
    verifyInvariance("equalsSymmetryInvariance", (l, r) -> l.equals(r), (l, r) -> r.equals(l));
  }

  @Test
  public void orOfDisjunction() {
    verifyInvariance("orOfDisjunction", ITERATIONS, 1 << 8,
            (l, r) -> l,
            (l, r) -> RoaringBitmap.or(l, RoaringBitmap.and(l, r)));
  }

  @Test
  public void orCoversXor() {
    verifyInvariance("orCoversXor", ITERATIONS, 1 << 8,
            (l, r) -> RoaringBitmap.or(l, r),
            (l, r) -> RoaringBitmap.or(l, RoaringBitmap.xor(l, r)));
  }

  @Test
  public void xorInvariance() {
    verifyInvariance("xorInvariance", ITERATIONS, 1 << 9,
            (l, r) -> RoaringBitmap.xor(l, r),
            (l, r) -> RoaringBitmap.andNot(RoaringBitmap.or(l, r), RoaringBitmap.and(l, r)));
  }

  @Test
  public void rangeCardinalityVsMaterialisedRange() {
    verifyInvariance("rangeCardinalityVsMaterialisedRange", 1 << 9,
            (min, max, bitmap) -> {
                RoaringBitmap range = new RoaringBitmap();
                range.add(min, max);
                return bitmap.rangeCardinality(min, max) == RoaringBitmap.andCardinality(range, bitmap);
            });
  }

  @Test
  public void absentValuesConsistentWithBitSet() {
    List<Integer> offsets = Arrays.asList(0, 1, -1, 10, -10, 100, -100);

    // Size limit to avoid out of memory errors; r.last() > 0 to avoid bitmaps with last > Integer.MAX_VALUE
    verifyInvariance(r -> r.isEmpty() || (r.last() > 0 && r.last() < 1 << 30), bitmap -> {
      BitSet reference = new BitSet();
      bitmap.iterator().forEachRemaining(reference::set);

      for (int next : bitmap) {
        for (int offset : offsets) {
          int pos = next + offset;
          if (pos >= 0) {
            assertEquals(reference.nextClearBit(pos), bitmap.nextAbsentValue(pos));
            assertEquals(reference.previousClearBit(pos), bitmap.previousAbsentValue(pos));
          }
        }
      }
    });
  }
}
