package com.suldenlion.mp3app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.Notification
import android.os.CountDownTimer
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.suldenlion.mp3app.MainActivity
import com.suldenlion.mp3app.R
import com.suldenlion.mp3app.viewmodel.PlaybackMode // PlaybackMode enum import
import java.util.concurrent.TimeUnit

@androidx.media3.common.util.UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var notificationManager: PlayerNotificationManager
    private var countDownTimer: CountDownTimer? = null


    companion object {
        const val ACTION_SET_TIMER = "com.suldenlion.mp3app.ACTION_SET_TIMER"
        const val ACTION_CANCEL_TIMER = "com.suldenlion.mp3app.ACTION_CANCEL_TIMER"
        const val EXTRA_TIMER_MINUTES = "com.suldenlion.mp3app.EXTRA_TIMER_MINUTES"

        const val ACTION_SET_PLAYBACK_MODE = "com.suldenlion.mp3app.ACTION_SET_PLAYBACK_MODE"
        const val EXTRA_PLAYBACK_MODE = "com.suldenlion.mp3app.EXTRA_PLAYBACK_MODE"
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            if (isPlaying) {
                // Fg Service 시작 (이미 PlayerNotificationManager가 처리)
            } else {
                // Fg Service 중지 및 알림 제거
                stopForeground(true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        player = ExoPlayer.Builder(this).build()
        // 기본 재생 모드 설정
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = false

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        player.addListener(playerListener)

        // 알림 설정
        setupNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_TIMER -> {
                val minutes = intent.getLongExtra(EXTRA_TIMER_MINUTES, 0)
                if (minutes > 0) {
                    val millis = TimeUnit.MINUTES.toMillis(minutes)
                    countDownTimer?.cancel()
                    countDownTimer = object : CountDownTimer(millis, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            // Can be used to show remaining time in notification
                        }

                        override fun onFinish() {
                            player.pause()
                        }
                    }.start()
                }
            }
            ACTION_CANCEL_TIMER -> {
                countDownTimer?.cancel()
            }
            ACTION_SET_PLAYBACK_MODE -> {
                val modeString = intent.getStringExtra(EXTRA_PLAYBACK_MODE)
                modeString?.let {
                    val playbackMode = PlaybackMode.valueOf(it)
                    when (playbackMode) {
                        PlaybackMode.REPEAT_ALL -> {
                            player.repeatMode = Player.REPEAT_MODE_ALL
                            player.shuffleModeEnabled = false
                        }
                        PlaybackMode.REPEAT_ONE -> {
                            player.repeatMode = Player.REPEAT_MODE_ONE
                            player.shuffleModeEnabled = false
                        }
                        PlaybackMode.SHUFFLE -> {
                            player.repeatMode = Player.REPEAT_MODE_ALL // Shuffle implies repeat all usually
                            player.shuffleModeEnabled = true
                        }
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
            mediaSession = null
        }
        countDownTimer?.cancel()
        notificationManager.setPlayer(null)
        super.onDestroy()
    }

    private fun setupNotification() {
        val channelId = "mp3_playback_channel"
        val channelName = "MP3 Playback"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager = PlayerNotificationManager.Builder(this, 1, channelId)
            .setNotificationListener(object: PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing) {
                        startForeground(notificationId, notification)
                    } else {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }

                override fun onNotificationCancelled(
                    notificationId: Int,
                    dismissedByUser: Boolean
                ) {
                    stopSelf()
                }
            })
            .build()

        notificationManager.setPlayer(player)
        notificationManager.setMediaSessionToken(mediaSession!!.sessionCompatToken)
    }
}
