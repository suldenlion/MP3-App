package com.suldenlion.mp3app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import com.suldenlion.mp3app.viewmodel.PlaybackMode
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.background
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
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
                navController = navController,
                mediaController = mediaController,
                musicViewModel = musicViewModel
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
        composable("favorites") {
            FavoritesScreen(
                musicViewModel = musicViewModel,
                mediaController = mediaController,
                navController = navController
            )
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        composable("timerSettings") {
            TimerSettingsScreen(
                navController = navController,
                musicViewModel = musicViewModel
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerSettingsScreen(navController: NavController, musicViewModel: MusicViewModel) {
    val timerText by musicViewModel.timerText.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("타이머 설정") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(timerText, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Text("원하는 시간을 선택하세요.", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = {
                    musicViewModel.setTimer(15)
                    Toast.makeText(context, "15분 타이머가 설정되었습니다.", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }) {
                    Text("15분")
                }
                Button(onClick = {
                    musicViewModel.setTimer(30)
                    Toast.makeText(context, "30분 타이머가 설정되었습니다.", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }) {
                    Text("30분")
                }
                Button(onClick = {
                    musicViewModel.setTimer(60)
                    Toast.makeText(context, "60분 타이머가 설정되었습니다.", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }) {
                    Text("60분")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                musicViewModel.cancelTimer()
                Toast.makeText(context, "타이머가 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }) {
                Text("타이머 취소")
            }
        }
    }
}


@Composable
fun PermissionScreen(navController: NavController, mediaController: MediaController?, musicViewModel: MusicViewModel) {
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
            MusicListScreen(mediaController = mediaController, navController = navController, musicViewModel = musicViewModel)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeApi::class)
@Composable
fun MusicListScreen(
    musicViewModel: MusicViewModel,
    mediaController: MediaController?,
    navController: NavController
) {
    val musicList by musicViewModel.musicList.collectAsState()
    val isLoading by musicViewModel.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 검색 쿼리에 따라 음악 목록을 필터링합니다.
    val filteredMusicList = if (searchQuery.isBlank()) {
        musicList
    } else {
        musicList.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val alphabeticalIndex by remember(filteredMusicList) {
        derivedStateOf {
            generateAlphabeticalIndex(filteredMusicList).first
        }
    }
    val scrollMap by remember(filteredMusicList) {
        derivedStateOf {
            generateAlphabeticalIndex(filteredMusicList).second
        }
    }

    LaunchedEffect(Unit) {
        musicViewModel.loadMusic()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 음악") },
                actions = {
                    IconButton(onClick = { navController.navigate("favorites") }) {
                        Icon(Icons.Default.Favorite, contentDescription = "즐겨찾기 목록")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 검색 바
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("검색 (제목 또는 아티스트)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search Icon")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                singleLine = true
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else if (musicList.isEmpty()) {
                    Text("음악 파일을 찾을 수 없습니다.")
                } else if (filteredMusicList.isEmpty()) {
                    Text("검색 결과가 없습니다.")
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            state = listState,
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(filteredMusicList.size) { index ->
                                val musicItem = filteredMusicList[index]
                                MusicListItem(
                                    musicItem = musicItem,
                                    musicList = filteredMusicList,
                                    index = index,
                                    mediaController = mediaController,
                                    navController = navController
                                )
                            }
                        }
                        AlphabeticalScrollIndex(
                            alphabeticalIndex = alphabeticalIndex,
                            scrollMap = scrollMap,
                            listState = listState,
                            coroutineScope = coroutineScope
                        )
                    }
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
                            .setUri(Uri.parse(item.contentUri))
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
    val musicList by musicViewModel.musicList.collectAsState()

    val currentMusicId = currentMediaItem?.mediaId?.toLongOrNull()
    var isCurrentSongFavorite by remember { mutableStateOf(false) }
    val playbackMode by musicViewModel.playbackMode.collectAsState() // Collect playback mode

    LaunchedEffect(currentMusicId, musicList) {
        isCurrentSongFavorite = musicList.find { it.id == currentMusicId }?.isFavorite ?: false
    }

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to List")
            }
            Row {
                // 즐겨찾기 버튼
                IconButton(onClick = {
                    currentMusicId?.let { musicViewModel.toggleFavorite(it) }
                }) {
                    Icon(
                        imageVector = if (isCurrentSongFavorite) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isCurrentSongFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                // 가사 편집 버튼
                IconButton(onClick = {
                    currentMediaItem?.mediaId?.let {
                        navController.navigate("editLyrics/$it")
                    }
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Lyrics")
                }
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
            // Playback Mode Button
            IconButton(onClick = { musicViewModel.togglePlaybackMode() }) {
                val icon = when (playbackMode) {
                    PlaybackMode.REPEAT_ALL -> Icons.Default.Repeat
                    PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne
                    PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Playback Mode",
                    modifier = Modifier.size(48.dp)
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("timerSettings") }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Timer, contentDescription = "Timer Icon")
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("타이머 설정", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    musicViewModel: MusicViewModel,
    mediaController: MediaController?,
    navController: NavController
) {
    val allMusic by musicViewModel.musicList.collectAsState()
    val favoriteMusic = allMusic.filter { it.isFavorite }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("즐겨찾기") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(8.dp)
        ) {
            if (favoriteMusic.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("즐겨찾기한 곡이 없습니다.")
                    }
                }
            } else {
                items(favoriteMusic.size) { index ->
                    val musicItem = favoriteMusic[index]
                    MusicListItem(
                        musicItem = musicItem,
                        musicList = favoriteMusic, // 즐겨찾기 목록 내에서만 재생목록을 구성
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

private fun getKoreanInitial(char: Char): Char? {
    val unicode = char.code
    // Check if it's within the Hangul Syllables range
    if (unicode in 0xAC00..0xD7A3) {
        val initialIndex = (unicode - 0xAC00) / (21 * 28)
        val initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        return initials.getOrNull(initialIndex)
    }
    // For standalone Hangul Jamo
    if (char in 'ㄱ'..'ㅎ') return char
    if (char in '가'..'힣') return getKoreanInitial(char) // Should be covered by the first check, but as a safeguard
    return null
}

// 알파벳 인덱스 및 스크롤 맵을 생성하는 헬퍼 함수
fun generateAlphabeticalIndex(musicList: List<MusicItem>): Pair<List<Char>, Map<Char, Int>> {
    val alphabeticalIndex = mutableListOf<Char>()
    val scrollMap = mutableMapOf<Char, Int>()
    var lastChar: Char? = null

    musicList.forEachIndexed { index, musicItem ->
        val firstChar = musicItem.title.firstOrNull() ?: return@forEachIndexed
        val representativeChar: Char?

        val koreanInitial = getKoreanInitial(firstChar)
        if (koreanInitial != null) {
            representativeChar = koreanInitial
        } else if (firstChar.isLetter()) {
            representativeChar = firstChar.uppercaseChar()
        } else {
            representativeChar = '#'
        }

        if (representativeChar != lastChar) {
            if (!alphabeticalIndex.contains(representativeChar)) {
                alphabeticalIndex.add(representativeChar)
            }
            if (!scrollMap.containsKey(representativeChar)) {
                scrollMap[representativeChar] = index
            }
            lastChar = representativeChar
        }
    }

    // Sort the index: Hangul -> Alphabet -> Others
    val sortedIndex = alphabeticalIndex.sortedWith(compareBy { char ->
        when {
            getKoreanInitial(char) != null -> 0 // Korean initials first
            char.isLetter() -> 1 // Then Alphabets
            else -> 2 // Others last
        }
    })

    return Pair(sortedIndex, scrollMap)
}

@Composable
fun AlphabeticalScrollIndex(
    alphabeticalIndex: List<Char>,
    scrollMap: Map<Char, Int>,
    listState: LazyListState,
    coroutineScope: CoroutineScope
) {
    var selectedChar by remember { mutableStateOf<Char?>(null) }
    val density = LocalDensity.current.density
    val itemHeightPx = remember { 16.dp.value * density } // Assume item height of 16.dp

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(vertical = 8.dp)
            .pointerInput(alphabeticalIndex, scrollMap) {
                detectVerticalDragGestures(
                    onDragStart = { offset: androidx.compose.ui.geometry.Offset ->
                        val y = offset.y
                        val index = (y / itemHeightPx).toInt().coerceIn(0, alphabeticalIndex.size - 1)
                        selectedChar = alphabeticalIndex.getOrNull(index)
                        selectedChar?.let { char ->
                            scrollMap[char]?.let { itemIndex ->
                                coroutineScope.launch {
                                    listState.scrollToItem(itemIndex)
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        selectedChar = null
                    },
                    onDragCancel = {
                        selectedChar = null
                    },
                    onVerticalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                        val y = change.position.y
                        val index = (y / itemHeightPx).toInt().coerceIn(0, alphabeticalIndex.size - 1)
                        val newSelectedChar = alphabeticalIndex.getOrNull(index)
                        if (newSelectedChar != selectedChar) {
                            selectedChar = newSelectedChar
                            selectedChar?.let { char ->
                                scrollMap[char]?.let { itemIndex ->
                                    coroutineScope.launch {
                                        listState.scrollToItem(itemIndex)
                                    }
                                }
                            }
                        }
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        alphabeticalIndex.forEach { char ->
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedChar == char) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                fontWeight = if (selectedChar == char) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable {
                    scrollMap[char]?.let { itemIndex ->
                        coroutineScope.launch {
                            listState.scrollToItem(itemIndex)
                        }
                    }
                }
            )
        }
    }
}