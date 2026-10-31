package he.spotshift.llo.mock

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import he.spotshift.llo.MainActivity
import he.spotshift.llo.R
import he.spotshift.llo.data.Spot
import he.spotshift.llo.data.coord6
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MockSession(
    val spotId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

/** Shared, observable mock-location state so every screen sees the same session. */
object MockState {
    private val _session = MutableStateFlow<MockSession?>(null)
    val session: StateFlow<MockSession?> = _session.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    internal fun setSession(session: MockSession?) {
        _session.value = session
    }

    internal fun setError(message: String?) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }
}

object MockLocationPermission {

    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openDeveloperOptions(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
    }
}

/**
 * Keeps feeding the platform a mocked GPS / network fix while a foreground
 * notification makes the state visible and lets the user stop it anywhere.
 */
class MockLocationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val providers = mutableListOf<String>()
    private var session: MockSession? = null

    @Volatile
    private var intervalMs = 1_000L

    private val ticker = object : Runnable {
        override fun run() {
            publishLocation()
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_INTERVAL) {
            if (session == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            applyInterval(intent)
            if (providers.isNotEmpty()) {
                handler.removeCallbacks(ticker)
                handler.post(ticker)
            }
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }

        applyInterval(intent)

        val spotId = intent.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY
        val name = intent.getStringExtra(EXTRA_NAME) ?: "模拟位置"
        val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        if (lat.isNaN() || lng.isNaN()) {
            stopEverything()
            return START_NOT_STICKY
        }

        val next = MockSession(spotId, name, lat, lng)
        session = next
        if (!startInForeground(next)) return START_NOT_STICKY

        val result = runCatching { ensureProviders() }
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            Log.w(TAG, "Unable to mock location", error)
            MockState.setError(
                if (error is SecurityException) {
                    "系统拒绝了模拟定位。请确认已在开发者选项中将 SpotShift 选为模拟位置信息应用。"
                } else {
                    "无法启动模拟定位：${error?.message ?: "未知错误"}"
                }
            )
            stopEverything()
            return START_NOT_STICKY
        }

        MockState.setError(null)
        MockState.setSession(next)
        updateNotification(next)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    private fun applyInterval(intent: Intent) {
        val seconds = intent.getIntExtra(EXTRA_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS)
            .coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)
        intervalMs = seconds * 1_000L
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        cleanupProviders()
        MockState.setSession(null)
        super.onDestroy()
    }

    private fun startInForeground(target: MockSession): Boolean {
        val notification = buildNotification(target)
        return runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                },
            )
        }.onFailure {
            Log.w(TAG, "startForeground failed", it)
            MockState.setError("无法启动前台定位服务，请检查定位权限后重试。")
            stopSelf()
        }.isSuccess
    }

    private fun ensureProviders() {
        if (providers.isNotEmpty()) return
        val manager = getSystemService(LocationManager::class.java) ?: error("LocationManager unavailable")
        val specs = listOf(
            ProviderSpec(
                name = LocationManager.GPS_PROVIDER,
                requiresNetwork = false,
                requiresSatellite = true,
                requiresCell = false,
                accuracy = Criteria.ACCURACY_FINE,
            ),
            ProviderSpec(
                name = LocationManager.NETWORK_PROVIDER,
                requiresNetwork = true,
                requiresSatellite = false,
                requiresCell = false,
                accuracy = Criteria.ACCURACY_COARSE,
            ),
        )
        specs.forEach { spec ->
            if (manager.getProvider(spec.name) == null) return@forEach
            addProvider(manager, spec)
            manager.setTestProviderEnabled(spec.name, true)
            providers += spec.name
        }
        if (providers.isEmpty()) error("No location provider available")
    }

    @Suppress("DEPRECATION")
    private fun addProvider(manager: LocationManager, spec: ProviderSpec) {
        fun add() = manager.addTestProvider(
            spec.name,
            spec.requiresNetwork,
            spec.requiresSatellite,
            spec.requiresCell,
            false,
            true,
            true,
            true,
            Criteria.POWER_LOW,
            spec.accuracy,
        )
        try {
            add()
        } catch (e: IllegalArgumentException) {
            // A stale test provider can survive an app crash; reset and retry once.
            runCatching { manager.removeTestProvider(spec.name) }
            add()
        }
    }

    private fun publishLocation() {
        val target = session ?: return
        val manager = getSystemService(LocationManager::class.java) ?: return
        providers.forEach { provider ->
            runCatching {
                manager.setTestProviderLocation(provider, buildLocation(provider, target))
            }.onFailure { error ->
                Log.w(TAG, "setTestProviderLocation($provider) failed", error)
                if (error is SecurityException) {
                    MockState.setError("模拟定位权限已被系统收回，请重新在开发者选项中选择 SpotShift。")
                    stopEverything()
                }
            }
        }
    }

    private fun buildLocation(provider: String, target: MockSession): Location =
        Location(provider).apply {
            latitude = target.lat
            longitude = target.lng
            accuracy = if (provider == LocationManager.GPS_PROVIDER) 3f else 8f
            altitude = 25.0
            speed = 0f
            bearing = 0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = 5f
                speedAccuracyMetersPerSecond = 0.3f
                bearingAccuracyDegrees = 1f
            }
            extras = Bundle().apply { putInt("satellites", 12) }
        }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "模拟定位",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示当前正在模拟的位置，并允许快速停止"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(target: MockSession): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_spot)
            .setContentTitle("SpotShift 正在模拟定位")
            .setContentText("${target.name} · ${target.lat.coord6()}, ${target.lng.coord6()}")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "停止模拟", stop)
            .build()
    }

    private fun updateNotification(target: MockSession) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(target))
        }
    }

    private fun cleanupProviders() {
        val manager = getSystemService(LocationManager::class.java) ?: return
        providers.forEach { provider ->
            runCatching { manager.setTestProviderEnabled(provider, false) }
            runCatching { manager.removeTestProvider(provider) }
        }
        providers.clear()
    }

    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        cleanupProviders()
        MockState.setSession(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private data class ProviderSpec(
        val name: String,
        val requiresNetwork: Boolean,
        val requiresSatellite: Boolean,
        val requiresCell: Boolean,
        val accuracy: Int,
    )

    companion object {
        private const val TAG = "MockLocationService"
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 42
        private const val DEFAULT_INTERVAL_SECONDS = 1
        private const val MIN_INTERVAL_SECONDS = 1
        private const val MAX_INTERVAL_SECONDS = 30
        private const val ACTION_START = "he.spotshift.llo.action.START_MOCK"
        private const val ACTION_STOP = "he.spotshift.llo.action.STOP_MOCK"
        private const val ACTION_INTERVAL = "he.spotshift.llo.action.UPDATE_INTERVAL"
        private const val EXTRA_ID = "spot_id"
        private const val EXTRA_NAME = "spot_name"
        private const val EXTRA_LAT = "spot_lat"
        private const val EXTRA_LNG = "spot_lng"
        private const val EXTRA_INTERVAL_SECONDS = "interval_seconds"

        fun start(context: Context, spot: Spot, intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, spot.id)
                putExtra(EXTRA_NAME, spot.name)
                putExtra(EXTRA_LAT, spot.lat)
                putExtra(EXTRA_LNG, spot.lng)
                putExtra(EXTRA_INTERVAL_SECONDS, intervalSeconds)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateInterval(context: Context, seconds: Int) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_INTERVAL
                putExtra(EXTRA_INTERVAL_SECONDS, seconds)
            }
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MockLocationService::class.java))
        }
    }
}
