# Room generates implementations reflectively named after the @Database class.
-keep class dev.kamlendu.preptracker.data.AppDatabase_Impl { *; }

# The notification listener and the SMS receiver are instantiated by the system from the manifest.
-keep class dev.kamlendu.preptracker.expense.TxnNotificationListener { *; }
-keep class dev.kamlendu.preptracker.expense.SmsReceiver { *; }
-keep class dev.kamlendu.preptracker.timer.TimerService { *; }
