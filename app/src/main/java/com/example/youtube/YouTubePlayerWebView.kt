package com.example.youtube

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

class YouTubePlayerController {
    internal var webView: WebView? = null

    fun goHome() {
        webView?.loadUrl("https://m.youtube.com")
    }

    fun goBack() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        }
    }

    fun reload() {
        webView?.reload()
    }

    fun loadVideo(videoId: String) {
        val safeId = videoId.replace("'", "")
        webView?.evaluateJavascript("if (window.loadDlmsVideo) { window.loadDlmsVideo('$safeId'); }", null)
    }

    fun play() {
        webView?.evaluateJavascript(
            """
            if (window.dlmsPlayer && window.dlmsPlayer.playVideo) {
                window.dlmsPlayer.playVideo();
            } else {
                var v = document.querySelector('video');
                if (v) v.play();
            }
            """.trimIndent(),
            null
        )
    }

    fun pause() {
        webView?.evaluateJavascript(
            """
            if (window.dlmsPlayer && window.dlmsPlayer.pauseVideo) {
                window.dlmsPlayer.pauseVideo();
            } else {
                var v = document.querySelector('video');
                if (v) v.pause();
            }
            """.trimIndent(),
            null
        )
    }

    fun seekBy(deltaSec: Float) {
        webView?.evaluateJavascript(
            """
            if (window.dlmsSeekBy) {
                window.dlmsSeekBy($deltaSec);
            } else {
                var v = document.querySelector('video');
                if (v) v.currentTime = Math.max(0, v.currentTime + $deltaSec);
            }
            """.trimIndent(),
            null
        )
    }

    fun setMuted(muted: Boolean) {
        val fn = if (muted) "mute()" else "unMute()"
        webView?.evaluateJavascript(
            """
            if (window.dlmsPlayer && window.dlmsPlayer.$fn) {
                window.dlmsPlayer.$fn;
            } else {
                var v = document.querySelector('video');
                if (v) v.muted = $muted;
            }
            """.trimIndent(),
            null
        )
    }

    fun setVolume(volumePercent: Int) {
        val vol = volumePercent.coerceIn(0, 100)
        val frac = vol / 100f
        webView?.evaluateJavascript(
            """
            if (window.dlmsPlayer && window.dlmsPlayer.setVolume) {
                window.dlmsPlayer.setVolume($vol);
            } else {
                var v = document.querySelector('video');
                if (v) v.volume = $frac;
            }
            """.trimIndent(),
            null
        )
    }
}

private class YouTubeJsBridge(
    private val onStateChanged: (YouTubePlaybackStatus) -> Unit,
    private val onProgress: (Float, Float) -> Unit,
    private val onError: (String) -> Unit
) {
    @JavascriptInterface
    fun onPlayerState(stateCode: Int) {
        val status = when (stateCode) {
            1 -> YouTubePlaybackStatus.PLAYING
            2 -> YouTubePlaybackStatus.PAUSED
            3 -> YouTubePlaybackStatus.BUFFERING
            0 -> YouTubePlaybackStatus.ENDED
            else -> YouTubePlaybackStatus.UNSTARTED
        }
        onStateChanged(status)
    }

    @JavascriptInterface
    fun onPlayerTime(currentSec: Float, durationSec: Float) {
        onProgress(currentSec, durationSec)
    }

    @JavascriptInterface
    fun onPlayerError(code: Int) {
        val msg = when (code) {
            2 -> "Invalid YouTube video ID."
            5 -> "HTML5 player error on this video."
            100 -> "Video not found or private."
            101, 150 -> "Video owner disabled embedded playback. Switching to Direct YouTube."
            else -> "YouTube player error ($code)."
        }
        onError(msg)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbeddedWebView(
    viewMode: YouTubeViewMode,
    activeVideoId: String?,
    submittedQuery: String,
    navigationNonce: Long,
    controller: YouTubePlayerController,
    onVideoDetected: (String) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    onStateChanged: (YouTubePlaybackStatus) -> Unit,
    onProgress: (Float, Float) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(Color.parseColor("#0B0E13"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webChromeClient = WebChromeClient()
            addJavascriptInterface(
                YouTubeJsBridge(onStateChanged, onProgress, onError),
                "DlmsBridge"
            )
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url ?: return false
                    val host = url.host.orEmpty().lowercase()
                    if (host.contains("youtube.com") || host.contains("youtu.be") ||
                        host.contains("googlevideo.com") || host.contains("accounts.google.com")
                    ) {
                        val extractedId = YouTubeUrlParser.extractVideoId(url.toString())
                        if (extractedId != null) {
                            onVideoDetected(extractedId)
                        }
                        return false
                    }
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onCanGoBackChanged(view?.canGoBack() == true)
                    if (url != null) {
                        YouTubeUrlParser.extractVideoId(url)?.let { onVideoDetected(it) }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        onError("YouTube unavailable: ${error?.description ?: "Network error"}")
                    }
                }
            }
        }
    }

    DisposableEffect(webView) {
        controller.webView = webView
        onDispose {
            controller.webView = null
            webView.destroy()
        }
    }

    LaunchedEffect(viewMode, activeVideoId, submittedQuery, navigationNonce) {
        if (viewMode == YouTubeViewMode.DIRECT_YOUTUBE) {
            val targetUrl = when {
                submittedQuery.isNotBlank() ->
                    "https://m.youtube.com/results?search_query=${Uri.encode(submittedQuery)}"
                !activeVideoId.isNullOrBlank() ->
                    "https://m.youtube.com/watch?v=${Uri.encode(activeVideoId)}"
                else ->
                    "https://m.youtube.com"
            }
            webView.loadUrl(targetUrl)
        } else {
            val html = buildYouTubeIframeHtml(activeVideoId.orEmpty())
            webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

private fun buildYouTubeIframeHtml(initialVideoId: String): String {
    val safeId = initialVideoId.replace("'", "")
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            html, body {
              margin: 0; padding: 0; width: 100%; height: 100%;
              background: #0B0E13; overflow: hidden;
              display: flex; align-items: center; justify-content: center;
              font-family: sans-serif; color: #90A4AE;
            }
            #player { width: 100%; height: 100%; }
            #placeholder {
              text-align: center; padding: 16px; font-size: 13px; letter-spacing: 0.5px;
            }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <div id="placeholder" style="display:${if (safeId.isEmpty()) "block" else "none"};">
            Select a video in Direct YouTube or paste a YouTube URL / Video ID above.
          </div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var dlmsPlayer = null;
            var pendingId = '$safeId';
            function onYouTubeIframeAPIReady() {
              if (!pendingId) return;
              createPlayer(pendingId);
            }
            function createPlayer(videoId) {
              document.getElementById('placeholder').style.display = 'none';
              dlmsPlayer = new YT.Player('player', {
                width: '100%',
                height: '100%',
                videoId: videoId,
                playerVars: {
                  'playsinline': 1,
                  'rel': 0,
                  'modestbranding': 1,
                  'origin': 'https://www.youtube.com'
                },
                events: {
                  'onStateChange': function(e) {
                    if (window.DlmsBridge) window.DlmsBridge.onPlayerState(e.data);
                  },
                  'onError': function(e) {
                    if (window.DlmsBridge) window.DlmsBridge.onPlayerError(e.data);
                  }
                }
              });
            }
            window.loadDlmsVideo = function(videoId) {
              pendingId = videoId;
              if (dlmsPlayer && dlmsPlayer.loadVideoById) {
                document.getElementById('placeholder').style.display = 'none';
                dlmsPlayer.loadVideoById(videoId);
              } else if (window.YT && window.YT.Player) {
                createPlayer(videoId);
              }
            };
            window.dlmsSeekBy = function(delta) {
              if (dlmsPlayer && dlmsPlayer.getCurrentTime) {
                var t = Math.max(0, dlmsPlayer.getCurrentTime() + delta);
                dlmsPlayer.seekTo(t, true);
              }
            };
            setInterval(function() {
              if (dlmsPlayer && dlmsPlayer.getCurrentTime && dlmsPlayer.getDuration) {
                var c = dlmsPlayer.getCurrentTime() || 0;
                var d = dlmsPlayer.getDuration() || 0;
                if (window.DlmsBridge) window.DlmsBridge.onPlayerTime(c, d);
              }
            }, 500);
          </script>
        </body>
        </html>
    """.trimIndent()
}
