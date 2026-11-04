package com.annukumar.team

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalPatternAnalyzerTest {
 @Test fun trendBaseOnlyMatchesThreeSameResults() {
  assertEquals("BIG", PatternAnalyzer.analyzeNewestFirst(listOf("BIG","BIG","BIG")).signal)
  assertEquals("SMALL", PatternAnalyzer.analyzeNewestFirst(listOf("SMALL","SMALL","SMALL")).signal)
 }
 @Test fun oneByOneOnlyMatchesAlternatingTriples() {
  assertEquals("SMALL", PatternAnalyzer.analyzeNewestFirst(listOf("BIG","SMALL","BIG")).signal)
  assertEquals("BIG", PatternAnalyzer.analyzeNewestFirst(listOf("SMALL","BIG","SMALL")).signal)
 }
 @Test fun allOtherPatternsReturnSkip() {
  assertNull(PatternAnalyzer.analyzeNewestFirst(listOf("BIG","BIG","SMALL")).signal)
  assertNull(PatternAnalyzer.analyzeNewestFirst(listOf("SMALL","SMALL","BIG")).signal)
  assertNull(PatternAnalyzer.analyzeNewestFirst(listOf("BIG","SMALL","SMALL")).signal)
  assertNull(PatternAnalyzer.analyzeNewestFirst(listOf("SMALL","BIG","BIG")).signal)
 }
 @Test fun fewerThanThreeRowsReturnSkip() {
  assertNull(PatternAnalyzer.analyzeNewestFirst(listOf("BIG","SMALL")).signal)
 }
}
