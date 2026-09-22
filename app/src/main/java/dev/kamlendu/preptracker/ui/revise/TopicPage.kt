package dev.kamlendu.preptracker.ui.revise

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import dev.kamlendu.preptracker.revision.Card
import dev.kamlendu.preptracker.revision.RevisionContent
import dev.kamlendu.preptracker.revision.Topic
import org.json.JSONObject

/**
 * One topic, rendered as a single WebView.
 *
 * Compose has no maths typesetting, and the alternative — flattening fractions and integrals into
 * Unicode — produces exactly the kind of half-legible formula that is worse than no formula at
 * all when you are checking something you half-remember. So the cards are laid out as HTML with
 * KaTeX's own markup, already typeset at build time, and styled from the Compose colour scheme so
 * the page is indistinguishable from the screens around it.
 *
 * One WebView for the whole topic, not one per card: a LazyColumn of WebViews costs a renderer
 * process each and scrolls badly.
 *
 * The only JavaScript is the bridge below. Tapping a card's remark button calls into Compose,
 * which opens a real bottom sheet with a real keyboard, and the saved note is written back into
 * the page by [RemarkPage.setRemark].
 */
class RemarkPage(private val webView: WebView) {
    fun setRemark(cardId: String, text: String?) {
        val js = "setRemark(${JSONObject.quote(cardId)}, ${
            if (text.isNullOrBlank()) "null" else JSONObject.quote(text)
        })"
        webView.evaluateJavascript(js, null)
    }
}

private class RemarkBridge(val onEdit: (String) -> Unit) {
    @JavascriptInterface
    fun edit(cardId: String) = onEdit(cardId)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TopicPage(
    subjectSlug: String,
    topic: Topic,
    remarks: Map<String, String>,
    palette: PagePalette,
    onEditRemark: (cardId: String, cardTitle: String) -> Unit,
    onReady: (RemarkPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Built once per topic: re-rendering the document on every recomposition would throw away the
    // scroll position, which on a page this long is the whole reading state.
    val html = remember(topic.slug) { buildHtml(subjectSlug, topic, remarks, palette) }
    val titles = remember(topic.slug) {
        topic.cards.associate { RevisionContent.cardId(subjectSlug, topic.slug, it.slug) to it.title }
    }

    // Keyed on the topic so moving to the next one builds a fresh WebView. Without it the
    // AndroidView factory would never run again and the page would still show the old topic.
    key(topic.slug) {
        AndroidView(
            modifier = modifier,
            onRelease = { it.destroy() },
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.textZoom = 100
                    // Nothing here is remote. No network, no file-system access beyond the assets the
                    // base URL points at.
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    isVerticalScrollBarEnabled = true
                    addJavascriptInterface(
                        RemarkBridge { cardId -> post { onEditRemark(cardId, titles[cardId].orEmpty()) } },
                        "Remarks",
                    )
                    loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                    onReady(RemarkPage(this))
                }
            },
        )
    }
}

/** The handful of theme colours the page needs, resolved on the Compose side. */
data class PagePalette(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val accent: Color,
    val warn: Color,
    val outline: Color,
)

private fun Color.css(): String = String.format("#%06X", 0xFFFFFF and toArgb())

private fun Color.rgba(alpha: Double): String {
    val argb = toArgb()
    return "rgba(${(argb shr 16) and 0xFF}, ${(argb shr 8) and 0xFF}, ${argb and 0xFF}, $alpha)"
}

private fun esc(s: String) = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun buildHtml(
    subjectSlug: String,
    topic: Topic,
    remarks: Map<String, String>,
    p: PagePalette,
): String {
    // The formula panel's tint, flattened to one solid colour so the scroll shadows above have
    // something opaque to fade into.
    val formulaBg = lerp(p.surface, p.accent, 0.08f)

    val cards = topic.cards.joinToString("") { card ->
        cardHtml(RevisionContent.cardId(subjectSlug, topic.slug, card.slug), card, remarks)
    }

    return """
<!doctype html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<link rel="stylesheet" href="katex/katex.min.css">
<style>
  :root { color-scheme: ${if (p.background.luminance() < 0.5) "dark" else "light"}; }
  * { -webkit-tap-highlight-color: transparent; }
  body {
    margin: 0; padding: 4px 16px 32px;
    background: ${p.background.css()}; color: ${p.onSurface.css()};
    font: 14px/1.65 -apple-system, Roboto, "Segoe UI", sans-serif;
    overflow-wrap: break-word;
  }
  .syl { color: ${p.onSurfaceVariant.css()}; font-size: 12px; line-height: 1.55; margin: 0 0 14px; }
  .syl b { color: ${p.onSurfaceVariant.css()}; font-weight: 600; }
  section {
    background: ${p.surface.css()}; border-radius: 20px; padding: 16px; margin-bottom: 14px;
  }
  h2 { font-size: 16px; font-weight: 600; margin: 0 0 8px; line-height: 1.4; }
  h2 p, .body p, .note p, .pit p { margin: 0 0 8px; }
  h2 p:last-child, .body p:last-child, .note p:last-child, .pit p:last-child { margin-bottom: 0; }
  .body { color: ${p.onSurface.rgba(0.88)}; }
  ul.f { list-style: none; margin: 12px 0 0; padding: 0; }
  ul.f li {
    border-left: 2px solid ${p.accent.css()}; background: ${formulaBg.css()};
    border-radius: 10px; padding: 8px 12px; margin-top: 8px;
  }
  /* A formula too wide for the screen scrolls sideways. Left to itself that is invisible — the
     expression just ends mid-symbol and looks like a bug — so these gradients sit at the edges
     and show only while there is more to scroll to. The two cover layers scroll with the content
     and hide the shadow once an edge is reached; the shadows themselves stay put. */
  .disp {
    --sb: ${p.surface.css()};
    overflow-x: auto; overflow-y: hidden; padding: 2px 0;
    background:
      linear-gradient(to right, var(--sb) 40%, rgba(0,0,0,0)) left center,
      linear-gradient(to left, var(--sb) 40%, rgba(0,0,0,0)) right center,
      radial-gradient(farthest-side at 0 50%, rgba(0,0,0,.40), rgba(0,0,0,0)) left center,
      radial-gradient(farthest-side at 100% 50%, rgba(0,0,0,.40), rgba(0,0,0,0)) right center;
    background-repeat: no-repeat;
    background-size: 26px 100%, 26px 100%, 13px 100%, 13px 100%;
    background-attachment: local, local, scroll, scroll;
  }
  ul.f li .disp { --sb: ${formulaBg.css()}; }
  .katex-display { margin: 0; text-align: left; }
  .katex-display > .katex { text-align: left; }
  /* Slightly under 1em: it keeps a few more of the longer expressions inside the screen. */
  .katex { font-size: 0.97em; }
  .note { color: ${p.onSurfaceVariant.css()}; font-size: 12px; margin-top: 4px; }
  .pit {
    border-left: 2px solid ${p.warn.css()}; background: ${p.warn.rgba(0.10)};
    border-radius: 10px; padding: 8px 12px; margin-top: 12px; font-size: 13px;
  }
  .pit .lbl {
    display: block; font-size: 10px; font-weight: 700; letter-spacing: .06em;
    text-transform: uppercase; color: ${p.warn.css()}; margin-bottom: 4px;
  }
  .rem { margin-top: 14px; }
  .rem button {
    font: inherit; font-size: 12px; color: ${p.onSurfaceVariant.css()};
    background: transparent; border: 1px solid ${p.outline.css()};
    border-radius: 10px; padding: 7px 12px; cursor: pointer;
  }
  .rem .has {
    border: 1px solid ${p.accent.rgba(0.45)}; background: ${p.accent.rgba(0.07)};
    border-radius: 12px; padding: 10px 12px; cursor: pointer; display: block; width: 100%;
    text-align: left; color: ${p.onSurface.css()}; font: inherit; font-size: 13px;
  }
  .rem .has .lbl {
    display: block; font-size: 10px; font-weight: 700; letter-spacing: .06em;
    text-transform: uppercase; color: ${p.accent.css()}; margin-bottom: 4px;
  }
</style>
</head><body>
<p class="syl"><b>Syllabus:</b> ${esc(topic.syllabus)}</p>
$cards
<script>
  // A formula wider than the screen is clipped, and a clipped formula is worse than a small one:
  // it ends mid-symbol and reads as a bug. So anything that overflows is shrunk just enough to
  // fit, by dropping the container's font size - KaTeX sizes everything in em, so the whole
  // expression scales and the panel reflows around it. (`zoom` looked simpler and does not work
  // here: KaTeX's display wrapper is a block that fills its container, so zooming it scales the
  // container too and the overflow survives.) Below 72% it would be too small to read, and those
  // are left to scroll sideways instead - the edge shadows show that they can.
  function fitFormulas() {
    var boxes = document.querySelectorAll(".disp");
    for (var i = 0; i < boxes.length; i++) {
      var box = boxes[i];
      box.style.fontSize = "";
      if (box.scrollWidth - box.clientWidth <= 1) continue;
      var scale = Math.max(box.clientWidth / box.scrollWidth, 0.72);
      var base = parseFloat(getComputedStyle(box).fontSize);
      box.style.fontSize = (base * scale * 0.98).toFixed(2) + "px";
    }
  }
  window.addEventListener("load", fitFormulas);
  window.addEventListener("resize", fitFormulas);
  // The maths fonts land after first paint and change every width, so measure again once in.
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(fitFormulas);

  function esc(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }
  // Called from Compose after a note is saved or cleared, so the page never has to be rebuilt.
  function setRemark(cardId, text) {
    var box = document.getElementById("rem:" + cardId);
    if (!box) return;
    box.innerHTML = text
      ? '<button class="has" onclick="Remarks.edit(' + JSON.stringify(cardId) + ')">' +
        '<span class="lbl">Your remark</span>' + esc(text).replace(/\n/g, "<br>") + '</button>'
      : '<button onclick="Remarks.edit(' + JSON.stringify(cardId) + ')">+ Add a remark</button>';
  }
</script>
</body></html>
""".trimIndent()
}

private fun cardHtml(cardId: String, card: Card, remarks: Map<String, String>): String {
    val formulas = if (card.formulas.isEmpty()) "" else card.formulas.joinToString(
        prefix = "<ul class=\"f\">", postfix = "</ul>", separator = ""
    ) { f ->
        "<li><div class=\"disp\">${f.exprHtml}</div>" +
            (f.noteHtml?.let { "<div class=\"note\">$it</div>" } ?: "") + "</li>"
    }

    val pitfall = card.pitfallHtml?.let {
        "<div class=\"pit\"><span class=\"lbl\">Where marks are lost</span>$it</div>"
    } ?: ""

    val note = remarks[cardId]
    val quoted = JSONObject.quote(cardId)
    val remark = if (note.isNullOrBlank()) {
        "<button onclick='Remarks.edit($quoted)'>+ Add a remark</button>"
    } else {
        "<button class=\"has\" onclick='Remarks.edit($quoted)'>" +
            "<span class=\"lbl\">Your remark</span>${esc(note).replace("\n", "<br>")}</button>"
    }

    return "<section><h2>${card.titleHtml}</h2><div class=\"body\">${card.bodyHtml}</div>" +
        formulas + pitfall + "<div class=\"rem\" id=\"rem:$cardId\">$remark</div></section>"
}

/** Rough perceptual luminance — only used to pick the page's `color-scheme`. */
private fun Color.luminance(): Double {
    val argb = toArgb()
    val r = ((argb shr 16) and 0xFF) / 255.0
    val g = ((argb shr 8) and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
