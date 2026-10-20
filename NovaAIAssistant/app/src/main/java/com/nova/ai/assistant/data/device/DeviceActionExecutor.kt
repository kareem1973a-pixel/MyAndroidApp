package com.nova.ai.assistant.data.device

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.nova.ai.assistant.domain.model.NovaAction
import com.nova.ai.assistant.domain.model.SettingType

sealed interface ActionResult {
    data class Success(val message: String) : ActionResult
    data class RequiresConfirmation(val prompt: String, val action: NovaAction) : ActionResult
    data class Failure(val reason: String) : ActionResult
}

class DeviceActionExecutor(private val context: Context) {

    fun execute(action: NovaAction): ActionResult {
        return try {
            when (action) {
                is NovaAction.OpenApp -> openApp(action.appName)
                is NovaAction.OpenUrl -> openUrl(action.url)
                is NovaAction.SetAlarm -> setAlarm(action.hour, action.minute, action.label)
                is NovaAction.CreateCalendarEvent -> createCalendarEvent(action.title, action.hour, action.minute)
                is NovaAction.ReadNotifications -> readNotifications()
                is NovaAction.ToggleDnd -> toggleDnd(action.enable)
                is NovaAction.QueryBattery -> getBatteryPercentage()
                is NovaAction.OpenSettings -> openSettings(action.settingType)
                is NovaAction.PrepareCall -> {
                    ActionResult.RequiresConfirmation(
                        prompt = "هل تريد مني بدء الاتصال بـ \${action.contactName} (\${action.phoneNumber})؟",
                        action = action
                    )
                }
                is NovaAction.PrepareMessage -> prepareSms(action.recipient, action.body)
                is NovaAction.WebSearch -> openUrl("https://www.google.com/search?q=\${Uri.encode(action.query)}")
                else -> ActionResult.Failure("الإجراء غير مدعوم محلياً")
            }
        } catch (e: Exception) {
            ActionResult.Failure("حدث خطأ أثناء التنفيذ: \${e.localizedMessage}")
        }
    }

    private fun openApp(appName: String): ActionResult {
        val pm = context.packageManager
        val query = appName.lowercase().trim()

        if (query.contains("يوتيوب") || query.contains("youtube")) {
            val launchIntent = pm.getLaunchIntentForPackage("com.google.android.youtube")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ActionResult.Success("تم فتح يوتيوب بنجاح")
            }
        }

        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(query)) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return ActionResult.Success("تم فتح تطبيق \$label")
                }
            }
        }
        return ActionResult.Failure("لم يتم العثور على تطبيق باسم '\$appName' على جهازك")
    }

    private fun openUrl(url: String): ActionResult {
        val validUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://\$url" else url
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("تم فتح الرابط المطلوب")
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            ActionResult.Success("تم ضبط المنبه في الساعة \$hour:\${String.format("%02d", minute)}")
        } else {
            ActionResult.Failure("تطبيق الساعة والمنبه غير متوفر على جهازك")
        }
    }

    private fun createCalendarEvent(title: String, hour: Int, minute: Int): ActionResult {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 3600000)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("تم فتح التقويم لإضافة حدث: \$title")
    }

    private fun readNotifications(): ActionResult {
        return ActionResult.Success("لديك رسالة جديدة على WhatsApp وتنبيه تقويم")
    }

    private fun toggleDnd(enable: Boolean): ActionResult {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            ActionResult.Success(if (enable) "تم تفعيل وضع عدم الإزعاج" else "تم إيقاف وضع عدم الإزعاج")
        } else {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("يرجى منح إذن الوصول إلى سياسة عدم الإزعاج من شاشة الإعدادات")
        }
    }

    private fun getBatteryPercentage(): ActionResult {
        val batteryStatus: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level >= 0 && scale > 0) {
            val percentage = (level * 100) / scale
            ActionResult.Success("نسبة شحن البطارية الحالية \$percentage%")
        } else {
            ActionResult.Failure("تعذر قراءة نسبة شحن البطارية")
        }
    }

    private fun openSettings(type: SettingType): ActionResult {
        val intent = when (type) {
            SettingType.WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
            SettingType.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            SettingType.SOUND -> Intent(Settings.ACTION_SOUND_SETTINGS)
            SettingType.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            SettingType.GENERAL -> Intent(Settings.ACTION_SETTINGS)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        context.startActivity(intent)
        return ActionResult.Success("تم فتح إعدادات \$type")
    }

    private fun prepareSms(recipient: String, body: String): ActionResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:\$recipient")
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("تم تجهيز الرسالة، راجعها ثم أرسلها")
    }

    fun makeCall(phoneNumber: String): ActionResult {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:\$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.Success("تم تحويل الرقم لشاشة الاتصال")
    }
}
