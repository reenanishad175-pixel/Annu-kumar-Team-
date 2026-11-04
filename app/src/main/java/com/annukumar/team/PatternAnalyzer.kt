package com.annukumar.team

/** Personal APK: only Trend Base 3 BIG/3 SMALL and One by One. Other patterns SKIP. */
object PatternAnalyzer {
    data class Summary(val signal:String?, val reason:String, val big:Int, val small:Int, val count:Int, val strategy:String?=null, val latestThree:List<String> = emptyList())
    private data class Strategy(val name:String, val pattern:List<String>, val signal:String)
    private val strategies = listOf(
        Strategy("ONE_BY_ONE_BIG_SMALL_BIG", listOf("BIG","SMALL","BIG"), "SMALL"),
        Strategy("ONE_BY_ONE_SMALL_BIG_SMALL", listOf("SMALL","BIG","SMALL"), "BIG"),
        Strategy("TREND_BASE_BIG", listOf("BIG","BIG","BIG"), "BIG"),
        Strategy("TREND_BASE_SMALL", listOf("SMALL","SMALL","SMALL"), "SMALL")
    )
    fun analyzeNewestFirst(rawValues:List<String>):Summary {
        val values=rawValues.map{it.trim().uppercase()}.filter{it=="BIG"||it=="SMALL"}.take(3)
        val big=values.count{it=="BIG"}; val small=values.size-big
        if(values.size<3) return Summary(null,"3 सत्यापित रिजल्ट नहीं मिले • SKIP",big,small,values.size,"SKIP",values)
        val match=strategies.firstOrNull{it.pattern==values}
        return if(match!=null) Summary(match.signal,"${match.name} मैच हुई",big,small,values.size,match.name,values)
        else Summary(null,"दोनों रणनीतियों में पैटर्न नहीं मिला • SKIP",big,small,values.size,"SKIP",values)
    }
    fun analyze(raw:String):Summary=analyzeNewestFirst(raw.split(Regex("[,\\s]+")).map{it.trim().uppercase()}.filter{it=="BIG"||it=="SMALL"})
}
