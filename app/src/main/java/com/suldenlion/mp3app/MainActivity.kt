package com.suldenlion.mp3app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.common.util.concurrent.MoreExecutors
import com.suldenlion.mp3app.model.MusicItem
import com.suldenlion.mp3app.service.PlaybackService
import com.suldenlion.mp3app.ui.theme.MP3AppTheme
import com.suldenlion.mp3app.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

class MainActivity : ComponentActivity() {

    private var mediaController: MediaController? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MP3AppTheme {
                Mp3AppNavigator(mediaController = mediaController)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        super.onStop()
        mediaController?.release()
        mediaController = null
    }
}

@Composable
fun Mp3AppNavigator(mediaController: MediaController?) {
    val navController = rememberNavController()
    val musicViewModel: MusicViewModel = viewModel()

    val startDestination = if (mediaController?.currentMediaItem != null) {
        "nowPlaying"
    } else {
        "musicList"
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("musicList") {
            PermissionScreen(
                mediaController = mediaController,
                navController = navController
            )
        }
        composable("nowPlaying") {
            NowPlayingScreen(
                mediaController = mediaController,
                navController = navController,
                musicViewModel = musicViewModel
            )
        }
        composable(
            route = "editLyrics/{musicId}",
            arguments = listOf(navArgument("musicId") { type = NavType.StringType })
        ) { backStackEntry ->
            EditLyricsScreen(
                navController = navController,
                musicViewModel = musicViewModel,
                musicId = backStackEntry.arguments?.getString("musicId")
            )
        }
    }
}


@Composable
fun PermissionScreen(mediaController: MediaController?, navController: NavController) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
            if (!isGranted) {
                Toast.makeText(context, "권한이 거부되었습니다. 앱의 일부 기능이 제한될 수 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (hasPermission) {
            MusicListScreen(mediaController = mediaController, navController = navController)
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "음악 파일을 재생하기 위해 저장소 접근 권한이 필요합니다.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(permission) }) {
                    Text(text = "권한 요청")
                }
            }
        }
    }
}

@Composable
fun MusicListScreen(
    musicViewModel: MusicViewModel = viewModel(),
    mediaController: MediaController?,
    navController: NavController
) {
    val musicList by musicViewModel.musicList.collectAsState()
    val isLoading by musicViewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        musicViewModel.loadMusic()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator()
        } else if (musicList.isEmpty()) {
            Text("음악 파일을 찾을 수 없습니다.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(musicList.size) { index ->
                    val musicItem = musicList[index]
                    MusicListItem(
                        musicItem = musicItem,
                        musicList = musicList,
                        index = index,
                        mediaController = mediaController,
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
fun MusicListItem(
    musicItem: MusicItem,
    musicList: List<MusicItem>,
    index: Int,
    mediaController: MediaController?,
    navController: NavController
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                mediaController?.let {
                    val mediaItems = musicList.map { item ->
                        MediaItem.Builder()
                            .setMediaId(item.id.toString())
                            .setUri(item.contentUri)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(item.title)
                                    .setArtist(item.artist)
                                    .build()
                            )
                            .build()
                    }
                    it.setMediaItems(mediaItems, index, 0L)
                    it.prepare()
                    it.play()
                }
                navController.navigate("nowPlaying")
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Music Note",
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = musicItem.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = musicItem.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    mediaController: MediaController?,
    navController: NavController,
    musicViewModel: MusicViewModel
) {
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(mediaController?.duration?.coerceAtLeast(0L) ?: 0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    val currentLyrics by musicViewModel.currentLyrics.collectAsState()

    DisposableEffect(mediaController) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                currentMediaItem = mediaController?.currentMediaItem
                duration = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                currentMediaItem?.mediaId?.toLongOrNull()?.let {
                    musicViewModel.loadLyrics(it)
                }
            }
        }
        mediaController?.addListener(listener)
        isPlaying = mediaController?.isPlaying ?: false
        currentMediaItem = mediaController?.currentMediaItem
        // 초기 duration 설정 로직을 onMediaMetadataChanged 로 이동
        currentMediaItem?.mediaId?.toLongOrNull()?.let {
            musicViewModel.loadLyrics(it)
        }
        onDispose {
            mediaController?.removeListener(listener)
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = mediaController?.currentPosition ?: 0L
            delay(1000L)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to List")
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                currentMediaItem?.mediaId?.let {
                    navController.navigate("editLyrics/$it")
                }
            }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Lyrics")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Song Info
        Text(
            text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "곡 정보 없음",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "아티스트 정보 없음",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Lyrics
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Text(text = currentLyrics?.lyrics ?: "등록된 가사가 없습니다.")
            }
        }

        // Seek Bar
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { currentPosition = it.toLong() },
            valueRange = 0f..duration.toFloat().coerceAtLeast(0f),
            onValueChangeFinished = { mediaController?.seekTo(currentPosition) })
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatDuration(currentPosition))
            Text(text = formatDuration(duration))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { mediaController?.seekToPreviousMediaItem() }) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(48.dp)
                )
            }
            IconButton(onClick = { if (isPlaying) mediaController?.pause() else mediaController?.play() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(64.dp)
                )
            }
            IconButton(onClick = { mediaController?.seekToNextMediaItem() }) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
fun EditLyricsScreen(
    navController: NavController,
    musicViewModel: MusicViewModel,
    musicId: String?
) {
    val currentLyrics by musicViewModel.currentLyrics.collectAsState()
    var lyricsText by remember(currentLyrics) { mutableStateOf(currentLyrics?.lyrics ?: "") }

    LaunchedEffect(musicId) {
        musicId?.toLongOrNull()?.let {
            musicViewModel.loadLyrics(it)
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("가사 편집", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        TextField(
            value = lyricsText,
            onValueChange = { lyricsText = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { navController.popBackStack() }) {
                Text("취소")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                musicId?.toLongOrNull()?.let {
                    musicViewModel.saveLyrics(it, lyricsText)
                }
                navController.popBackStack()
            }) {
                Text("저장")
            }
        }
    }
}


private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}