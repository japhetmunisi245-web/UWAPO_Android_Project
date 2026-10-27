package com.uwapo.app.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.uwapo.app.MainActivity
import com.uwapo.app.R

object ReminderManager {

    fun sendDueDateNotification(context: Context, unpaidCount: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "Kikumbusho cha Mchango wa UWAPO / Due Reminder"
        val body = if (unpaidCount > 0) {
            "Wanachama $unpaidCount hawajalipa mchango wa TZS 10,000 wa mwezi huu."
        } else {
            "Tarehe ya mwisho ya mchango wa mwezi huu inakaribia."
        }

        val notification = NotificationCompat.Builder(context, "uwapo_due_reminders")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                manager.notify(1001, notification)
            }
        } catch (_: Exception) { /* Ignore if notification permission is denied */ }
    }

    fun sendSmsReminder(context: Context, phone: String, memberName: String, monthStr: String) {
        val cleanPhone = phone.replace(" ", "").replace("-", "")
        val text = "Hujambo $memberName, tafadhali kumbuka kulipia mchango wa UWAPO wa TZS 10,000 wa $monthStr. Ahsante!"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(cleanPhone)}")
            putExtra("sms_body", text)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) { /* Handle missing SMS app gracefully */ }
    }
}
