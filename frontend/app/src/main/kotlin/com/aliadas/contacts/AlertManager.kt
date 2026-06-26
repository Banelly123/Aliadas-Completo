package com.aliadas.contacts

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import com.aliadas.network.RetrofitClient
import com.aliadas.utils.SessionManager
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AlertManager {

    private var isAlertActive = false
    private var alertThread: Thread? = null

    fun sendPanicAlert(context: Context) {
        if (isAlertActive) return
        isAlertActive = true
        getLocationAndSendSms(context)
    }

    fun stopAlert(context: Context) {
        isAlertActive = false
        alertThread?.interrupt()
    }

    fun isAlertActive() = isAlertActive

    private fun getLocationAndSendSms(context: Context) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            sendSmsToContacts(context, null, null)
            return
        }
        fusedClient.lastLocation.addOnSuccessListener { location ->
            sendSmsToContacts(context, location?.latitude, location?.longitude)
        }.addOnFailureListener {
            sendSmsToContacts(context, null, null)
        }
    }

    private fun sendSmsToContacts(context: Context, lat: Double?, lng: Double?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = SessionManager.getBearerToken(context)
                val contacts = RetrofitClient.api.getContacts(token).body() ?: return@launch

                val message = buildSmsMessage(context, lat, lng)

                contacts.forEach { contact ->
                    sendSms(contact.phone, message)
                }

                // Repeat every 2 minutes until stopped
                alertThread = Thread {
                    while (isAlertActive) {
                        Thread.sleep(120_000)
                        if (!isAlertActive) break
                        contacts.forEach { sendSms(it.phone, message) }
                    }
                }
                alertThread?.start()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildSmsMessage(context: Context, lat: Double?, lng: Double?): String {
        val locationPart = if (lat != null && lng != null) {
            "📍 Ubicación: https://maps.google.com/?q=$lat,$lng"
        } else {
            "📍 Ubicación no disponible"
        }

        val battery = getBatteryLevel(context)
        val networkInfo = getNetworkInfo(context)
        val lastUnlock = getLastUnlockTime(context)

        return """
🚨 ALERTA DE EMERGENCIA - Aliadas

Una persona de tu confianza necesita ayuda.

$locationPart
🔋 Batería: $battery%
📶 Red: $networkInfo
🔓 Último desbloqueo: $lastUnlock

Si no recibes noticias de ella, contacta al 911.
        """.trimIndent()
    }

    private fun sendSms(phone: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getNetworkInfo(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "Sin conexión"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "Sin conexión"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                "WiFi: ${wifiManager.connectionInfo.ssid.replace("\"", "")}"
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                "Datos móviles: ${tm.networkOperatorName}"
            }
            else -> "Desconocida"
        }
    }

    private fun getLastUnlockTime(context: Context): String {
        val prefs = context.getSharedPreferences("aliadas_unlock", Context.MODE_PRIVATE)
        val time = prefs.getLong("last_unlock", 0L)
        return if (time == 0L) "No disponible" else {
            val sdf = java.text.SimpleDateFormat("HH:mm dd/MM/yyyy", java.util.Locale.getDefault())
            sdf.format(java.util.Date(time))
        }
    }
}

// ── BroadcastReceiver para llamadas entrantes ──────────────────────────────
class CallReceiver : BroadcastReceiver() {
    private var callStartTime: Long = 0
    private val RING_TIMEOUT_MS = 10_000L // 10 segundos ≈ 4 tonos

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // A trusted contact is calling
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
                if (isTrustedContact(context, incomingNumber)) {
                    callStartTime = System.currentTimeMillis()
                    scheduleAlert(context)
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered - cancel alert
                AlertManager.stopAlert(context)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended or missed - if it's been ringing for 10s+, trigger alert
                if (callStartTime > 0 && System.currentTimeMillis() - callStartTime >= RING_TIMEOUT_MS) {
                    AlertManager.sendPanicAlert(context)
                }
                callStartTime = 0
            }
        }
    }

    private fun isTrustedContact(context: Context, phone: String): Boolean {
        // Check against cached contacts in SharedPreferences
        val prefs = context.getSharedPreferences("aliadas_contacts", Context.MODE_PRIVATE)
        val phones = prefs.getStringSet("trusted_phones", emptySet()) ?: emptySet()
        val normalizedIncoming = phone.replace(Regex("[^0-9]"), "").takeLast(10)
        return phones.any { it.replace(Regex("[^0-9]"), "").takeLast(10) == normalizedIncoming }
    }

    private fun scheduleAlert(context: Context) {
        Thread {
            Thread.sleep(RING_TIMEOUT_MS)
            val prefs = context.getSharedPreferences("aliadas_call", Context.MODE_PRIVATE)
            val stillRinging = prefs.getBoolean("ringing", false)
            if (stillRinging) AlertManager.sendPanicAlert(context)
        }.start()
    }
}
