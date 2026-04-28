package tools.mo3ta.salo.ui

import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

actual fun showPlatformToast(message: String) {
    val alert = UIAlertController.alertControllerWithTitle(null, message, 0)
    alert.addAction(UIAlertAction.actionWithTitle("OK", UIAlertActionStyleDefault, null))
    UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(alert, true, null)
}

actual fun copyToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}
