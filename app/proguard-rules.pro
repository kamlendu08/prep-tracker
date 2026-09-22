# Room generates implementations reflectively named after the @Database class.
-keep class dev.kamlendu.preptracker.data.AppDatabase_Impl { *; }

# The notification listener and the SMS receiver are instantiated by the system from the manifest.
-keep class dev.kamlendu.preptracker.expense.TxnNotificationListener { *; }
-keep class dev.kamlendu.preptracker.expense.SmsReceiver { *; }
-keep class dev.kamlendu.preptracker.timer.TimerService { *; }

# The revision page's remark bridge is only ever called from JavaScript, so R8 sees no call site.
# proguard-android-optimize.txt already carries this rule; it is repeated here because losing it
# silently turns "Add a remark" into a button that does nothing, and only in release builds.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
