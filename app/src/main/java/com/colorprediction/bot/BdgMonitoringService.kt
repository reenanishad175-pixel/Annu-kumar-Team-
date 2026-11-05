package com.colorprediction.bot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

/**
 * A user-visible foreground monitor with its own WebView. The Android OS may
 * still defer work during Doze or battery restrictions; monitoring is not
 * guaranteed if the user force-stops the app or the system kills its process.
 */
class BdgMonitoringService : Service() {
    private var monitor: WebView? = null
    private val channelId = "bdg_monitoring"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "BDG Monitoring", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_color_bot_logo)
            .setContentTitle("Color Prediction Bot")
            .setContentText("BDG background monitoring is active")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(4107, notification)
        startMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitor == null) startMonitor()
        return START_STICKY
    }

    private fun startMonitor() {
        if (monitor != null) return
        val prefs = getSharedPreferences("bot_settings", MODE_PRIVATE)
        val url = prefs.getString("bdg_url", "https://bdgwin98.com") ?: "https://bdgwin98.com"
        monitor = WebView(applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = false
            addJavascriptInterface(MonitorBridge(), "AndroidBdg")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, pageUrl: String) {
                    super.onPageFinished(view, pageUrl)
                    view.evaluateJavascript(READER_SCRIPT, null)
                }
            }
            loadUrl(url)
        }
    }

    private inner class MonitorBridge {
        @JavascriptInterface
        fun onPageText(pageText: String) {
            val snapshot = BdgPageParser.parse(pageText) ?: return
            BdgAutomationController.process(this@BdgMonitoringService, snapshot)
        }
    }

    override fun onDestroy() {
        monitor?.apply {
            stopLoading()
            loadUrl("about:blank")
            removeJavascriptInterface("AndroidBdg")
            destroy()
        }
        monitor = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val READER_SCRIPT = """
            (function(){
              function read(){
                try {
                  if(!window.AndroidBdg || !document.body) return;
                  var pageText=document.body.innerText||'';
                  var visible=function(x){var r=x.getBoundingClientRect(),s=getComputedStyle(x);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'&&Number(s.opacity)!==0;};
                  var candidates=Array.from(document.querySelectorAll('[role=tab],[aria-selected="true"],[aria-pressed="true"],.van-tab--active,[class*="active"],[data-active="true"]')).filter(visible);
                  var activeMode=candidates.filter(function(x){var t=(x.innerText||x.textContent||'').trim(),cl=String(x.className||'');return (/true/i.test(x.getAttribute('aria-selected')||'')||/true/i.test(x.getAttribute('aria-pressed')||'')||/active/i.test(cl)||x.getAttribute('data-active')==='true')&&/(?:30\s*(?:sec|s)|1\s*(?:min|m)|3\s*(?:min|m)|5\s*(?:min|m))/i.test(t);}).map(function(x){return (x.innerText||x.textContent||'').trim();}).sort(function(a,b){return a.length-b.length;})[0];
                  if(activeMode) pageText+='\nACTIVE_GAME_MODE: Win Go '+activeMode;
                  var rows=Array.from(document.querySelectorAll('tr,li,[class*="record"],[class*="history"],[class*="item"],[class*="row"]')).filter(visible);
                  var normalized=rows.map(function(x){var t=(x.innerText||x.textContent||'').replace(/\s+/g,' ').trim();var ids=t.match(/\b\d{12,20}\b/g)||[],sides=t.match(/\b(?:BIG|SMALL)\b/gi)||[];if(ids.length!==1||sides.length!==1||t.length>240)return '';var digit=t.match(/\b[0-9]\b/);return ids[0]+' '+(digit?digit[0]+' ':'')+sides[0].toUpperCase();}).filter(Boolean);
                  if(normalized.length) pageText+='\n'+normalized.join('\n');
                  window.AndroidBdg.onPageText(pageText);
                } catch(e) {}
              }
              read();
              if(window.__colorBotReaderTimer) clearInterval(window.__colorBotReaderTimer);
              window.__colorBotReaderTimer=setInterval(read,2500);
              if(window.__colorBotObserver) window.__colorBotObserver.disconnect();
              window.__colorBotObserver=new MutationObserver(function(){read();});
              window.__colorBotObserver.observe(document.body,{childList:true,subtree:true});
            })();
        """
    }
}
