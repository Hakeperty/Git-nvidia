package com.anivault.ui.screens.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anivault.data.model.Chapter
import com.anivault.data.model.Manga
import com.anivault.data.model.ReadingMode
import kotlinx.coroutines.delay

/**
 * Manga Reader Screen with support for:
 * - Vertical scroll mode (webtoon style)
 * - Paginated mode (page by page)
 * - Right-to-left reading
 * - Chapter navigation
 * - Progress saving
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaReaderScreen(
    initialChapter: Chapter,
    mangaInfo: Manga?,
    viewModel: MangaReaderViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // State from ViewModel
    val readerState by viewModel.state.collectAsState()

    // Local state
    var isControlsVisible by remember { mutableStateOf(true) }
    var showChapterList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }

    // Initialize reader
    LaunchedEffect(initialChapter.id) {
        viewModel.setChapter(initialChapter, mangaInfo)

        // Keep screen on
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Controls visibility timer
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(3000)
            isControlsVisible = false
        }
    }

    // Fullscreen toggle
    fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        if (isFullScreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Reader content based on mode
            when (readerState.readingMode) {
                ReadingMode.VERTICAL_SCROLL -> {
                    VerticalScrollReader(
                        pages = readerState.pageUrls,
                        currentPage = readerState.currentPage,
                        totalPages = readerState.totalPages,
                        isControlsVisible = isControlsVisible,
                        onToggleControls = { isControlsVisible = !isControlsVisible },
                        onPageChanged = { viewModel.setCurrentPage(it) }
                    )
                }
                ReadingMode.PAGINATED -> {
                    PaginatedReader(
                        pages = readerState.pageUrls,
                        currentPage = readerState.currentPage,
                        totalPages = readerState.totalPages,
                        isControlsVisible = isControlsVisible,
                        onToggleControls = { isControlsVisible = !isControlsVisible },
                        onPageChanged = { viewModel.setCurrentPage(it) },
                        onPreviousPage = { viewModel.navigatePage(-1) },
                        onNextPage = { viewModel.navigatePage(1) }
                    )
                }
                ReadingMode.RIGHT_TO_LEFT -> {
                    PaginatedReader(
                        pages = readerState.pageUrls.reversed(),
                        currentPage = readerState.currentPage,
                        totalPages = readerState.totalPages,
                        isControlsVisible = isControlsVisible,
                        onToggleControls = { isControlsVisible = !isControlsVisible },
                        onPageChanged = { viewModel.setCurrentPage(it) },
                        onPreviousPage = { viewModel.navigatePage(1) }, // Reverse navigation
                        onNextPage = { viewModel.navigatePage(-1) },
                        rightToLeft = true
                    )
                }
            }

            // Top controls overlay
            if (isControlsVisible) {
                TopControlsOverlay(
                    chapterTitle = readerState.currentChapter?.title ?: "Chapter ${readerState.currentChapter?.chapterNumber}",
                    chapterNumber = readerState.currentChapter?.chapterNumber ?: 0f,
                    totalPages = readerState.totalPages,
                    currentPage = readerState.currentPage,
                    onToggleFullScreen = { toggleFullScreen() },
                    onOpenChapterList = { showChapterList = true },
                    onOpenSettings = { showSettings = true }
                )
            }

            // Bottom page indicator
            if (isControlsVisible) {
                BottomPageIndicator(
                    currentPage = readerState.currentPage,
                    totalPages = readerState.totalPages,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Loading indicator
            if (readerState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFE50914)
                    )
                }
            }
        }
    }

    // Chapter list dialog
    if (showChapterList) {
        ChapterListDialog(
            isOpen = showChapterList,
            onClose = { showChapterList = false },
            chapters = readerState.chapters,
            currentChapter = readerState.currentChapter,
            mangaInfo = readerState.seriesInfo,
            onChapterSelect = { chapter ->
                viewModel.setChapter(chapter, mangaInfo)
                showChapterList = false
            }
        )
    }

    // Settings dialog
    if (showSettings) {
        ReaderSettingsDialog(
            isOpen = showSettings,
            onClose = { showSettings = false },
            readingMode = readerState.readingMode,
            onReadingModeChange = { mode ->
                viewModel.setReadingMode(mode)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VerticalScrollReader(
    pages: List<String>,
    currentPage: Int,
    totalPages: Int,
    isControlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onPageChanged: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    // Update current page based on scroll position
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (index != currentPage && index < pages.size) {
                    onPageChanged(index)
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onToggleControls)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(pages) { pageUrl ->
                AsyncImage(
                    model = pageUrl,
                    contentDescription = "Manga page",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .background(Color.DarkGray)
                )
            }
        }
    }
}

@Composable
private fun PaginatedReader(
    pages: List<String>,
    currentPage: Int,
    totalPages: Int,
    isControlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    rightToLeft: Boolean = false
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = currentPage,
        pageCount = { totalPages }
    )

    // Sync with ViewModel
    LaunchedEffect(pagerState.currentPage) {
        snapshotFlow { pagerState.currentPage }
            .collect { index ->
                if (index != currentPage && index < pages.size) {
                    onPageChanged(index)
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onToggleControls)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageContent = { index ->
                if (index < pages.size) {
                    AsyncImage(
                        model = pages[index],
                        contentDescription = "Manga page $index",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray)
                    )
                }
            }
        )

        // Navigation buttons for paginated mode
        if (isControlsVisible) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side - previous page
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onPreviousPage)
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Page",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                    )
                }

                // Right side - next page
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onNextPage)
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Page",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopControlsOverlay(
    chapterTitle: String,
    chapterNumber: Float,
    totalPages: Int,
    currentPage: Int,
    onToggleFullScreen: () -> Unit,
    onOpenChapterList: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                )
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chapter info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Chapter ${chapterNumber.toInt()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = chapterTitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }

            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
            IconButton(onClick = onOpenChapterList) {
                Icon(Icons.Default.List, contentDescription = "Chapters", tint = Color.White)
            }
            IconButton(onClick = onToggleFullScreen) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
            }
        }
    }
}

@Composable
private fun BottomPageIndicator(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$currentPage",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "/ $totalPages",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ChapterListDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    mangaInfo: Manga?,
    onChapterSelect: (Chapter) -> Unit
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .heightIn(max = 600.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mangaInfo?.title ?: "Chapters",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Divider(color = Color.Gray)

                Spacer(Modifier.height(8.dp))

                // Chapter list
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    LazyColumn {
                        items(chapters.sortedByDescending { it.chapterNumber }) { chapter ->
                            ChapterListItem(
                                chapter = chapter,
                                isCurrent = chapter.id == currentChapter?.id,
                                onClick = { onChapterSelect(chapter) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterListItem(
    chapter: Chapter,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(80.dp)
        ) {
            Text(
                text = "Ch. ${chapter.chapterNumber.toInt()}",
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) Color(0xFFE50914) else Color.White,
                fontSize = 14.sp
            )
            if (chapter.volumeNumber != null) {
                Text(
                    text = "Vol. ${chapter.volumeNumber}",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = chapter.title ?: "Chapter ${chapter.chapterNumber.toInt()}",
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 2
            )

            if (chapter.scanlator != null) {
                Text(
                    text = chapter.scanlator,
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }

        // Read status
        if (chapter.status == ChapterStatus.READ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Read",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        } else if (chapter.status == ChapterStatus.IN_PROGRESS) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = "In Progress",
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ReaderSettingsDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    readingMode: ReadingMode,
    onReadingModeChange: (ReadingMode) -> Unit
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(300.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Reading Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Reading Mode",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = readingMode == ReadingMode.VERTICAL_SCROLL,
                        onClick = { onReadingModeChange(ReadingMode.VERTICAL_SCROLL) },
                        label = { Text("Vertical", color = Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE50914),
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = readingMode == ReadingMode.PAGINATED,
                        onClick = { onReadingModeChange(ReadingMode.PAGINATED) },
                        label = { Text("Page", color = Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE50914),
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = readingMode == ReadingMode.RIGHT_TO_LEFT,
                        onClick = { onReadingModeChange(ReadingMode.RIGHT_TO_LEFT) },
                        label = { Text("R→L", color = Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE50914),
                            selectedLabelColor = Color.White
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Text("Close")
                }
            }
        }
    }
}

// HorizontalPager composable using LazyRow for simplicity
@Composable
private fun HorizontalPager(
    state: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier,
    pageContent: @Composable (Int) -> Unit
) {
    LazyRow(
        state = LazyListState(state.currentPage),
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState()),
        userScrollEnabled = true
    ) {
        items(state.pageCount) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
            ) {
                pageContent(index)
            }
        }
    }
}
