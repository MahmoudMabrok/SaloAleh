package tools.mo3ta.salo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import tools.mo3ta.salo.AndroidAppContext

actual fun showPlatformToast(message: String) {
    Toast.makeText(AndroidAppContext.get(), message, Toast.LENGTH_SHORT).show()
}

actual fun copyToClipboard(text: String) {
    val cm = AndroidAppContext.get().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("winner_code", text))
}
