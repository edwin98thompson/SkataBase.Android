package com.example.myfirstapp.training

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.content.pm.PackageManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.myfirstapp.AppGraph
import com.example.myfirstapp.MainActivity
import com.example.myfirstapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrainingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<Intent>(Channel.UNLIMITED)
    private lateinit var recorder: MotionTrainingRecorder
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(applicationContext)
        recorder = MotionTrainingRecorder(applicationContext, serviceScope)
        createNotificationChannel()

        serviceScope.launch {
            recorder.state.collect { state ->
                _sessionState.value = state
                if (foregroundStarted) updateNotification(state)
            }
        }
        serviceScope.launch {
            for (command in commands) handleCommand(command)
        }
        serviceScope.launch {
            while (true) {
                val recordingIsActive = recorder.state.value is TrainingSessionState.Running ||
                        recorder.state.value is TrainingSessionState.Paused
                val connection = AppGraph.bleManager.connectionState.value
                val canReconnect = connection is com.example.myfirstapp.model.ConnectionState.Disconnected ||
                        connection is com.example.myfirstapp.model.ConnectionState.Error
                if (recordingIsActive && canReconnect && !AppGraph.bleManager.isScanning.value) {
                    AppGraph.bleManager.startAutoConnect()
                }
                kotlinx.coroutines.delay(2_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: return START_NOT_STICKY
        if (command.action == ACTION_START) {
            startAsForeground()
        }
        commands.trySend(command)
        return START_NOT_STICKY
    }

    private suspend fun handleCommand(intent: Intent) {
        when (intent.action) {
            ACTION_START -> {
                if (recorder.state.value is TrainingSessionState.Running ||
                    recorder.state.value is TrainingSessionState.Paused
                ) return

                val config = TrainingConfig(
                    startThreshold = intent.getFloatExtra(EXTRA_START_THRESHOLD, 100f),
                    endThreshold = intent.getFloatExtra(EXTRA_END_THRESHOLD, 50f),
                    preEventSamples = intent.getIntExtra(EXTRA_PRE_SAMPLES, 25),
                    postEventSamples = intent.getIntExtra(EXTRA_POST_SAMPLES, 25)
                )
                try {
                    recorder.start(AppGraph.repository.imuPacketFlow, config)
                    val connection = AppGraph.bleManager.connectionState.value
                    if (connection is com.example.myfirstapp.model.ConnectionState.Disconnected ||
                        connection is com.example.myfirstapp.model.ConnectionState.Error
                    ) {
                        AppGraph.bleManager.startAutoConnect()
                    }
                } catch (failure: Exception) {
                    _sessionState.value = TrainingSessionState.Error(
                        failure.message ?: "Could not start training"
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }

            ACTION_PAUSE -> recorder.pause()
            ACTION_RESUME -> recorder.resume()
            ACTION_STOP -> {
                foregroundStarted = false
                recorder.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startAsForeground() {
        if (foregroundStarted) return
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Starting training session"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        foregroundStarted = true
    }

    private fun updateNotification(state: TrainingSessionState) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(notificationText(state)))
    }

    private fun notificationText(state: TrainingSessionState): String = when (state) {
        is TrainingSessionState.Running ->
            if (state.activeEvent == null) "Training: waiting for movement" else "Recording event ${state.activeEvent}"
        is TrainingSessionState.Paused -> "Training paused"
        is TrainingSessionState.Completed -> "Training complete"
        is TrainingSessionState.Error -> "Training error"
        TrainingSessionState.Idle -> "Training session"
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val state = _sessionState.value
        val action = if (state is TrainingSessionState.Paused) ACTION_RESUME else ACTION_PAUSE
        val actionLabel = if (state is TrainingSessionState.Paused) "Resume" else "Pause"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("IMU data training")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, actionLabel, servicePendingIntent(action, REQUEST_PAUSE))
            .addAction(0, "Stop", servicePendingIntent(ACTION_STOP, REQUEST_STOP))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TrainingService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Data training",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        commands.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "imu_training"
        private const val NOTIFICATION_ID = 8301
        private const val REQUEST_OPEN_APP = 8302
        private const val REQUEST_PAUSE = 8303
        private const val REQUEST_STOP = 8304

        const val ACTION_START = "com.example.myfirstapp.training.START"
        const val ACTION_PAUSE = "com.example.myfirstapp.training.PAUSE"
        const val ACTION_RESUME = "com.example.myfirstapp.training.RESUME"
        const val ACTION_STOP = "com.example.myfirstapp.training.STOP"

        const val EXTRA_START_THRESHOLD = "start_threshold"
        const val EXTRA_END_THRESHOLD = "end_threshold"
        const val EXTRA_PRE_SAMPLES = "pre_samples"
        const val EXTRA_POST_SAMPLES = "post_samples"

        private val _sessionState =
            MutableStateFlow<TrainingSessionState>(TrainingSessionState.Idle)
        val sessionState: StateFlow<TrainingSessionState> = _sessionState.asStateFlow()
    }
}
