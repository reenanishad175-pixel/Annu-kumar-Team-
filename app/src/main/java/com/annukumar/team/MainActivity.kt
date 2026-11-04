package com.annukumar.team

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

/** Personal read-only BDG dashboard. No Telegram or wager automation. */
class MainActivity : Activity() {
 private val handler=Handler(Looper.getMainLooper())
 private val prefs by lazy { getSharedPreferences("personal_dashboard", MODE_PRIVATE) }
 private lateinit var web:WebView
 private lateinit var status:TextView
 private lateinit var prediction:TextView
 private lateinit var detail:TextView
 private lateinit var history:TextView
 private var pageReady=false
 private var inFlight=false
 private var mode="Bingo"
 private val navy=Color.rgb(9,15,29)
 private val panel=Color.rgb(20,30,49)
 private val accent=Color.rgb(39,211,175)
 private val muted=Color.rgb(174,188,207)
 private val poll=object:Runnable {
  override fun run() {
   if(::web.isInitialized && pageReady && web.visibility==View.VISIBLE && !isFinishing && !inFlight) {
    inFlight=true
    web.evaluateJavascript(BdgHistoryReader.HISTORY_READER_JS) { raw ->
     inFlight=false
     if(!isFinishing && raw!=null && raw!="null") consume(raw)
    }
   }
   handler.postDelayed(this,2500)
  }
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=navy
  window.navigationBarColor=navy
  setContentView(screen())
  if(savedInstanceState==null) web.loadUrl("https://bdgwin68.com/#/login")
  handler.postDelayed(poll,2500)
 }
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun label(s:String,size:Float,color:Int=Color.WHITE)=TextView(this).apply {
  text=s; textSize=size; setTextColor(color)
 }
 private fun screen():View {
  val root=FrameLayout(this)
  web=WebView(this).apply {
   settings.javaScriptEnabled=true
   settings.domStorageEnabled=true
   settings.loadsImagesAutomatically=true
   settings.useWideViewPort=true
   settings.loadWithOverviewMode=true
   settings.builtInZoomControls=true
   settings.displayZoomControls=false
   settings.setSupportZoom(true)
   webChromeClient=WebChromeClient()
   webViewClient=object:WebViewClient() {
    override fun onPageStarted(v:WebView?,u:String?,f:android.graphics.Bitmap?) { pageReady=false; status.text="BDG वेबसाइट लोड हो रही है…" }
    override fun onPageFinished(v:WebView?,u:String?) {
     pageReady=true
     status.text="BDG पेज तैयार है • वेबसाइट पर लॉगिन करें"
     v?.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}m.content='width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';})()",null)
     v?.evaluateJavascript(BdgHistoryReader.NETWORK_HISTORY_HOOK_JS,null)
    }
    override fun onReceivedError(v:WebView?,r:WebResourceRequest?,e:WebResourceError?) {
     if(r?.isForMainFrame==true){pageReady=false;status.text="BDG वेबसाइट नहीं खुली • इंटरनेट जाँचें"}
    }
   }
  }
  root.addView(web,FrameLayout.LayoutParams(-1,-1))
  val card=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(dp(12),dp(9),dp(12),dp(9))
   setBackgroundColor(panel)
   elevation=dp(10).toFloat()
  }
  card.addView(label("ANNU PERSONAL • TWO STRATEGIES",15f,accent))
  status=label("BDG वेबसाइट लोड हो रही है…",12f,muted)
  status.setPadding(0,dp(5),0,dp(5)); card.addView(status)
  prediction=label("अगला Period: — • SKIP",18f,accent)
  prediction.setTypeface(null,android.graphics.Typeface.BOLD); card.addView(prediction)
  detail=label("Trend Base 3 Big / 3 Small • One by One",12f,muted)
  detail.setPadding(0,dp(4),0,dp(3)); card.addView(detail)
  history=label("History: डेटा का इंतज़ार",12f,muted); card.addView(history)
  val lp=FrameLayout.LayoutParams(-1,-2,Gravity.TOP)
  lp.setMargins(dp(8),dp(8),dp(8),0)
  root.addView(card,lp)
  return root
 }
 private fun consume(raw:String) {
  try {
   val decoded=org.json.JSONTokener(raw).nextValue() as? String ?: return
   if(decoded=="WAIT_BINGO"){status.text="BDG में Win Go / Bingo पेज खोलें"; showSkip("गेम पेज नहीं मिला");return}
   if(decoded=="WAIT_PANEL"){status.text="चुने हुए गेम का History panel नहीं मिला";showSkip("History panel नहीं मिला");return}
   if(decoded=="WAIT_MODE"){status.text="30 सेकंड / 1 / 3 / 5 मिनट का गेम चुनें";showSkip("गेम अवधि चुनें");return}
   val obj=JSONObject(decoded); mode=obj.optString("mode","Bingo")
   val arr=obj.optJSONArray("rows")?:JSONArray()
   val parsed=mutableListOf<Pair<String,String>>()
   for(i in 0 until arr.length()) {
    val row=arr.optJSONObject(i)?:continue
    val p=row.optString("period"); val side=row.optString("size").uppercase()
    if(p.matches(Regex("[0-9]{8,24}")) && (side=="BIG"||side=="SMALL") && parsed.none{it.first==p}) parsed.add(p to side)
   }
   status.text=if(parsed.isEmpty()) "$mode • History नहीं मिली" else "$mode • ${parsed.size}/10 रिजल्ट पढ़े गए"
   update(parsed)
  } catch(_:Exception) { status.text="BDG History अभी पढ़ी नहीं जा सकी";showSkip("History पढ़ने में त्रुटि") }
 }
 private fun showSkip(why:String) { prediction.text="अगला Period: — • SKIP";detail.text=why }
 private fun update(raw:List<Pair<String,String>>) {
  val ordered=raw.sortedByDescending { try{java.math.BigInteger(it.first)}catch(_:Exception){java.math.BigInteger.ZERO} }
  if(ordered.isEmpty()){showSkip("कम से कम 3 सत्यापित रिजल्ट चाहिए");history.text="History: —";return}
  val target=try{java.math.BigInteger(ordered.first().first).add(java.math.BigInteger.ONE).toString()}catch(_:Exception){ordered.first().first}
  val preview=PatternAnalyzer.analyzeNewestFirst(ordered.take(3).map{it.second})
  val old=DashboardTrendSkipTracker.State(
   pendingPeriod=prefs.getString("pending_period","").orEmpty(),
   pendingPick=prefs.getString("pending_pick","").orEmpty(),
   pendingStrategy=prefs.getString("pending_strategy","").orEmpty(),
   skipRemaining=prefs.getInt("skip_remaining",0),
   lastSkippedPeriod=prefs.getString("last_skipped_period","").orEmpty(),
   lastSkipNumber=prefs.getInt("last_skip_number",0)
  )
  val result=DashboardTrendSkipTracker.update(ordered,target,old,preview.signal,preview.strategy)
  prefs.edit()
   .putString("pending_period",result.state.pendingPeriod)
   .putString("pending_pick",result.state.pendingPick)
   .putString("pending_strategy",result.state.pendingStrategy)
   .putInt("skip_remaining",result.state.skipRemaining)
   .putString("last_skipped_period",result.state.lastSkippedPeriod)
   .putInt("last_skip_number",result.state.lastSkipNumber).apply()
  if(result.skipThisPeriod) {
   prediction.text="अगला Period: $target • SKIP ${result.skipNumber}/3"
   detail.text="Trend Base LOSS • ${result.skipRemainingAfterThisPeriod} SKIP बाकी"
  } else if(preview.signal!=null) {
   prediction.text="अगला Period: $target • ${preview.signal}"
   detail.text=if(preview.strategy?.startsWith("TREND_BASE")==true) "Strategy: Trend Base 3 Big / 3 Small" else "Strategy: One by One"
  } else {
   prediction.text="अगला Period: $target • SKIP"
   detail.text=preview.reason
  }
  history.text="पिछले ${ordered.size}: "+ordered.joinToString(" › "){it.second}
 }
 override fun onResume(){super.onResume();if(::web.isInitialized)web.onResume()}
 override fun onDestroy(){
  handler.removeCallbacksAndMessages(null)
  if(::web.isInitialized){web.stopLoading();web.loadUrl("about:blank");web.removeAllViews();web.destroy()}
  super.onDestroy()
 }
}
