// NoteEditorScreen.kt

package com.weproz.superreader

import android.graphics.Bitmap
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.OpenWith // Icon for Panning
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.outlined.LineStyle
import androidx.compose.material.icons.outlined.Square
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.navigation.NavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

// Actions for Undo/Redo
sealed class DrawingAction {
    data class DrawAction(val paths: List<DrawingPath>) : DrawingAction()
    data class EraseAction(val erasedPaths: List<DrawingPath>, val newPaths: List<DrawingPath>) :
        DrawingAction()

    data class ClearAction(val clearedPaths: List<DrawingPath>) : DrawingAction()
}


@OptIn(ExperimentalMaterial3Api::class) // Material3 API
@Composable

fun NoteEditorScreen(
    navController: NavController,
    noteTitle: String,
    notebookId: Int? = null,
    notebookDao: NotebookDao? = null,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isPanMode by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var showZoomSlider by remember { mutableStateOf(false) }

    val completedPaths = remember { mutableStateListOf<DrawingPath>() }
    val undoStack = remember { mutableStateListOf<DrawingAction>() }
    val redoStack = remember { mutableStateListOf<DrawingAction>() }
    var currentPath by remember { mutableStateOf<DrawingPath?>(null) }
    var isEraserOn by remember { mutableStateOf(false) }
    var penColor by remember { mutableStateOf(Color(0xFF6200EE)) }
    var penSize by remember { mutableFloatStateOf(5f) }
    var eraserSize by remember { mutableFloatStateOf(40f) }
    var selectedPenType by remember { mutableStateOf(PenType.BRUSH) }
    var showPenOptions by remember { mutableStateOf(false) }
    var showEraserOptions by remember { mutableStateOf(false) }
    var isErasing by remember { mutableStateOf(false) }
    var pathsBeforeErase by remember { mutableStateOf<List<DrawingPath>>(emptyList()) }
    var showGridDialog by remember { mutableStateOf(false) }
    var gridType by remember { mutableStateOf(GridType.NONE) }
    var showSaveDialog by remember { mutableStateOf(false) }

    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var initialContent by remember { mutableStateOf<List<DrawingPath>?>(null) }
    var initialGridType by remember { mutableStateOf(GridType.NONE) } // NEW: Track initial grid type

    // --- NEW: State to track if a drag gesture is valid ---
    var isDragInitiatedInBounds by remember { mutableStateOf(false) }

    val canvasBackgroundColor = Color.White
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun Int.toColor(): Color {
        return Color(
            red = android.graphics.Color.red(this) / 255f,
            green = android.graphics.Color.green(this) / 255f,
            blue = android.graphics.Color.blue(this) / 255f,
            alpha = android.graphics.Color.alpha(this) / 255f
        )
    }

    LaunchedEffect(notebookId, notebookDao) {
        if (notebookId != null && notebookDao != null) {
            try {
                notebookDao.getNotebookById(notebookId).collect { notebookData ->
                    notebookData?.let { data ->
                        initialGridType = data.gridType
                        gridType = data.gridType
                        data.content?.let { pathDataList ->
                            if (pathDataList.isNotEmpty() && completedPaths.isEmpty()) {
                                completedPaths.clear()
                                undoStack.clear()
                                redoStack.clear()
                                val loadedPaths = pathDataList.mapNotNull { pathData ->
                                    try {
                                        val path = Path()
                                        if (pathData.points.isNotEmpty()) {
                                            path.moveTo(
                                                pathData.points.first().first,
                                                pathData.points.first().second
                                            )
                                            pathData.points.drop(1).forEach { point ->
                                                path.lineTo(point.first, point.second)
                                            }
                                        }
                                        DrawingPath(
                                            path = path,
                                            color = pathData.color.toColor(),
                                            strokeWidth = pathData.strokeWidth,
                                            penType = pathData.penType
                                        )
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (loadedPaths.isNotEmpty()) {
                                    completedPaths.addAll(loadedPaths)
                                    // Store initial content for comparison
                                    initialContent = loadedPaths.toList()
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Handle error
            }
        }
    }

    // Track changes to detect if content has been modified
    LaunchedEffect(completedPaths.size, undoStack.size, redoStack.size, gridType) {
        if (initialContent != null || initialGridType != GridType.NONE) {
            val currentContent = completedPaths.toList()
            val contentChanged = !arePathsEqual(initialContent ?: emptyList(), currentContent)
            val gridTypeChanged = gridType != initialGridType
            hasUnsavedChanges = contentChanged || gridTypeChanged // NEW: Include grid type changes
        } else if (completedPaths.isNotEmpty() || gridType != GridType.NONE) {
            hasUnsavedChanges = true
        } else {
            hasUnsavedChanges = false
        }
    }

    DisposableEffect(notebookId, notebookDao, hasUnsavedChanges) {
        onDispose {
            if (notebookId != null && notebookDao != null && hasUnsavedChanges) {
                coroutineScope.launch {
                    try {
                        val notebookData = notebookDao.getNotebookById(notebookId).first()
                        notebookData?.let { existingNotebook ->
                            val pathDataList = completedPaths.map { drawingPath ->
                                PathData(
                                    points = extractPointsFromPath(drawingPath.path),
                                    color = drawingPath.color.toArgb(),
                                    strokeWidth = drawingPath.strokeWidth,
                                    penType = drawingPath.penType
                                )
                            }
                            val updatedNotebook = existingNotebook.copy(
                                content = pathDataList,
                                lastModified = System.currentTimeMillis(),
                                gridType = gridType
                            )
                            notebookDao.update(updatedNotebook)
                        }
                    } catch (_: Exception) {
                        // Handle error
                    }
                }
            }
        }
    }
    // Track changes to detect if content has been modified
    LaunchedEffect(completedPaths.size, undoStack.size, redoStack.size) {
        if (initialContent != null) {
            val currentContent = completedPaths.toList()
            hasUnsavedChanges = !arePathsEqual(initialContent!!, currentContent)
        } else if (completedPaths.isNotEmpty()) {
            hasUnsavedChanges = true
        } else {
            hasUnsavedChanges = false
        }
    }



    fun handleBackNavigation() {
        coroutineScope.launch {
            try {
                if (notebookId != null && notebookDao != null && hasUnsavedChanges) {
                    val notebookData = notebookDao.getNotebookById(notebookId).first()
                    notebookData?.let { existingNotebook ->
                        val pathDataList = completedPaths.map { drawingPath ->
                            PathData(
                                points = extractPointsFromPath(drawingPath.path),
                                color = drawingPath.color.toArgb(),
                                strokeWidth = drawingPath.strokeWidth,
                                penType = drawingPath.penType
                            )
                        }
                        val updatedNotebook = existingNotebook.copy(
                            content = pathDataList,
                            lastModified = System.currentTimeMillis(), // Only update if changes exist
                            gridType = gridType
                        )
                        notebookDao.update(updatedNotebook)
                    }
                }
            } catch (_: Exception) {
                // Handle error
            }
            navController.popBackStack()
        }
    }

    BackHandler {
        handleBackNavigation()
    }

    fun resetView() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    fun zoomIn() {
        scale = (scale * 1.2f).coerceAtMost(5f)
    }

    fun zoomOut() {
        scale = (scale / 1.2f).coerceAtLeast(0.5f)
    }

    val displayTitle = if (noteTitle.length > 10) {
        "${noteTitle.take(10)}..."
    } else {
        noteTitle
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                shadowElevation = 4.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { handleBackNavigation() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }

                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { isPanMode = !isPanMode },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.OpenWith,
                                contentDescription = "Pan Tool",
                                tint = if (isPanMode) activeColor else inactiveColor
                            )
                        }

                        IconButton(
                            onClick = { showZoomSlider = !showZoomSlider },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.ZoomInMap,
                                contentDescription = "Zoom Controls",
                                tint = if (showZoomSlider) activeColor else inactiveColor
                            )
                        }

                        IconButton(
                            onClick = {
                                if (undoStack.isNotEmpty()) {
                                    val lastAction = undoStack.removeAt(undoStack.lastIndex)
                                    when (lastAction) {
                                        is DrawingAction.DrawAction -> {
                                            completedPaths.removeAll(lastAction.paths)
                                            redoStack.add(lastAction)
                                        }

                                        is DrawingAction.EraseAction -> {
                                            completedPaths.removeAll(lastAction.newPaths)
                                            completedPaths.addAll(lastAction.erasedPaths)
                                            redoStack.add(lastAction)
                                        }

                                        is DrawingAction.ClearAction -> {
                                            completedPaths.addAll(lastAction.clearedPaths)
                                            redoStack.add(lastAction)
                                        }
                                    }
                                }
                            },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (undoStack.isNotEmpty()) activeColor else inactiveColor
                            )
                        }

                        IconButton(
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    val nextAction = redoStack.removeAt(redoStack.lastIndex)
                                    when (nextAction) {
                                        is DrawingAction.DrawAction -> {
                                            completedPaths.addAll(nextAction.paths)
                                            undoStack.add(nextAction)
                                        }

                                        is DrawingAction.EraseAction -> {
                                            completedPaths.removeAll(nextAction.erasedPaths)
                                            completedPaths.addAll(nextAction.newPaths)
                                            undoStack.add(nextAction)
                                        }

                                        is DrawingAction.ClearAction -> {
                                            completedPaths.clear()
                                            undoStack.add(nextAction)
                                        }
                                    }
                                }
                            },
                            enabled = redoStack.isNotEmpty(),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (redoStack.isNotEmpty()) activeColor else inactiveColor
                            )
                        }

                        Box {
                            IconButton(
                                onClick = {
                                    showPenOptions = !showPenOptions
                                    showEraserOptions = false
                                    isEraserOn = false
                                    isPanMode = false
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    when (selectedPenType) {
                                        PenType.BRUSH -> Icons.Filled.Brush
                                        PenType.HIGHLIGHTER -> Icons.Filled.Highlight
                                        PenType.SKETCH -> Icons.Filled.Edit
                                    },
                                    contentDescription = "Pen Options",
                                    tint = if (!isEraserOn && !isPanMode) activeColor else inactiveColor
                                )
                            }
                            DropdownMenu(
                                expanded = showPenOptions,
                                onDismissRequest = { showPenOptions = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    "Pen Type",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        top = 8.dp,
                                        bottom = 4.dp
                                    )
                                )
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                ) {
                                    listOf(
                                        Triple(PenType.BRUSH, Icons.Filled.Brush, "Brush"),
                                        Triple(
                                            PenType.HIGHLIGHTER,
                                            Icons.Filled.Highlight,
                                            "Highlighter"
                                        ),
                                        Triple(PenType.SKETCH, Icons.Filled.Edit, "Sketch")
                                    ).forEach { (type, icon, label) ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedPenType = type }
                                                .padding(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (type == selectedPenType) activeColor.copy(
                                                            alpha = 0.1f
                                                        ) else Color.Transparent
                                                    )
                                                    .border(
                                                        width = if (type == selectedPenType) 2.dp else 1.dp,
                                                        color = if (type == selectedPenType) activeColor else Color.Gray.copy(
                                                            alpha = 0.3f
                                                        ),
                                                        shape = CircleShape
                                                    ), contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    icon,
                                                    contentDescription = label,
                                                    tint = if (type == selectedPenType) activeColor else inactiveColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (type == selectedPenType) activeColor else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    "Color",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        top = 8.dp,
                                        bottom = 4.dp
                                    )
                                )
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                ) {
                                    listOf(
                                        Color(0xFF6200EE),
                                        Color.Black,
                                        Color(0xFFD32F2F),
                                        Color(0xFF388E3C),
                                        Color(0xFF1976D2),
                                        Color(0xFFF57C00),
                                        Color(0xFF7B1FA2)
                                    ).forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .padding(4.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    width = if (color == penColor) 3.dp else 0.dp,
                                                    color = activeColor,
                                                    shape = CircleShape
                                                )
                                                .clickable { penColor = color })
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    "Pen Size",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        top = 8.dp,
                                        bottom = 4.dp
                                    )
                                )
                                Slider(
                                    value = penSize,
                                    onValueChange = { penSize = it },
                                    valueRange = 2f..40f,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = {
                                    isEraserOn = true
                                    showEraserOptions = !showEraserOptions
                                    showPenOptions = false
                                    isPanMode = false
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Filled.AutoFixOff,
                                    contentDescription = "Eraser Options",
                                    tint = if (isEraserOn && !isPanMode) activeColor else inactiveColor
                                )
                            }
                            DropdownMenu(
                                expanded = showEraserOptions,
                                onDismissRequest = { showEraserOptions = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    "Eraser Size",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(
                                        start = 8.dp,
                                        top = 8.dp,
                                        bottom = 4.dp
                                    )
                                )
                                Slider(
                                    value = eraserSize,
                                    onValueChange = { eraserSize = it },
                                    valueRange = 10f..80f,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (completedPaths.isNotEmpty() && hasVisiblePaths(completedPaths)) {
                                    val currentState = completedPaths.toList()
                                    undoStack.add(DrawingAction.ClearAction(currentState))
                                    completedPaths.clear()
                                    redoStack.clear()
                                }
                            },
                            enabled = completedPaths.isNotEmpty() && hasVisiblePaths(completedPaths),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear All",
                                tint = if (completedPaths.isNotEmpty() && hasVisiblePaths(
                                        completedPaths
                                    )
                                ) activeColor else inactiveColor
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showGridDialog = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Dashboard,
                                    contentDescription = "More Options",
                                    tint = if (gridType != GridType.NONE || completedPaths.isNotEmpty()) activeColor else inactiveColor
                                )
                            }
                            DropdownMenu(
                                expanded = showGridDialog,
                                onDismissRequest = { showGridDialog = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    "Grid Type",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        top = 8.dp,
                                        bottom = 8.dp
                                    )
                                )
                                listOf(
                                    Triple(GridType.NONE, Icons.Outlined.Square, "No Grid"),
                                    Triple(GridType.SQUARE, Icons.Filled.Dashboard, "Square Grid"),
                                    Triple(GridType.DOT, Icons.Outlined.TextFields, "Dot Grid"),
                                    Triple(GridType.RULED, Icons.Outlined.LineStyle, "Ruled")
                                ).forEach { (type, icon, label) ->
                                    DropdownMenuItem(text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                icon,
                                                contentDescription = label,
                                                modifier = Modifier.size(20.dp),
                                                tint = if (type == gridType) activeColor else MaterialTheme.colorScheme.onSurface
                                            ); Text(
                                            text = label,
                                            modifier = Modifier.padding(start = 12.dp),
                                            color = if (type == gridType) activeColor else MaterialTheme.colorScheme.onSurface
                                        )
                                        }
                                    }, onClick = { gridType = type; showGridDialog = false })
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                DropdownMenuItem(text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Save,
                                            contentDescription = "Save Drawing",
                                            modifier = Modifier.size(20.dp),
                                            tint = if (completedPaths.isNotEmpty()) activeColor else inactiveColor
                                        ); Text(
                                        text = "Save Drawing",
                                        modifier = Modifier.padding(start = 12.dp),
                                        color = if (completedPaths.isNotEmpty()) activeColor else inactiveColor
                                    )
                                    }
                                }, onClick = {
                                    if (completedPaths.isNotEmpty()) {
                                        showGridDialog = false; showSaveDialog = true
                                    }
                                }, enabled = completedPaths.isNotEmpty())
                            }
                        }
                    }

                    if (showZoomSlider) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Zoom",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { zoomOut() },
                                    enabled = scale > 0.5f,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text(
                                        "-",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (scale > 0.5f) activeColor else inactiveColor
                                    )
                                }
                                Slider(
                                    value = scale,
                                    onValueChange = { scale = it },
                                    valueRange = 0.5f..5f,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { zoomIn() },
                                    enabled = scale < 5f,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text(
                                        "+",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (scale < 5f) activeColor else inactiveColor
                                    )
                                }
                                Text(
                                    text = "${(scale * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .clickable { resetView() }
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(isPanMode, isEraserOn, scale) {
                        if (isPanMode) {
                            detectDragGestures { change, dragAmount ->
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                                change.consume()
                            }
                        } else {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val transformedStart = Offset(
                                        (startOffset.x - offsetX) / scale,
                                        (startOffset.y - offsetY) / scale
                                    )

                                    // --- NEW: Boundary Check ---
                                    if (transformedStart.x >= 0 && transformedStart.x <= size.width &&
                                        transformedStart.y >= 0 && transformedStart.y <= size.height
                                    ) {
                                        isDragInitiatedInBounds = true
                                        if (isEraserOn) {
                                            if (!isErasing) {
                                                pathsBeforeErase = completedPaths.toList()
                                                isErasing = true
                                            }
                                            currentPath = null
                                        } else {
                                            currentPath = DrawingPath(
                                                path = Path().apply {
                                                    moveTo(
                                                        transformedStart.x,
                                                        transformedStart.y
                                                    )
                                                },
                                                color = penColor,
                                                strokeWidth = penSize / scale,
                                                penType = selectedPenType
                                            )
                                        }
                                    } else {
                                        isDragInitiatedInBounds = false
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (!isDragInitiatedInBounds) {
                                        change.consume()
                                        return@detectDragGestures
                                    }

                                    var transformedPos = Offset(
                                        (change.position.x - offsetX) / scale,
                                        (change.position.y - offsetY) / scale
                                    )

                                    // --- NEW: Clamp coordinates to bounds ---
                                    transformedPos = Offset(
                                        x = transformedPos.x.coerceIn(0f, size.width.toFloat()),
                                        y = transformedPos.y.coerceIn(0f, size.height.toFloat())
                                    )

                                    if (isEraserOn) {
                                        val eraserRadius = (eraserSize / 2) / scale
                                        completedPaths.toList().forEach { drawingPath ->
                                            if (isPathWithinEraserRadius(
                                                    drawingPath.path,
                                                    transformedPos,
                                                    eraserRadius
                                                )
                                            ) {
                                                completedPaths.remove(drawingPath)
                                                val pathSegments = splitPathAroundPoint(
                                                    drawingPath,
                                                    transformedPos,
                                                    eraserRadius
                                                )
                                                completedPaths.addAll(pathSegments)
                                            }
                                        }
                                    } else {
                                        currentPath?.let { path ->
                                            path.path.lineTo(transformedPos.x, transformedPos.y)
                                            currentPath = path.copy(
                                                path = Path().apply { addPath(path.path) }
                                            )
                                        }
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (isDragInitiatedInBounds) {
                                        if (!isEraserOn) {
                                            currentPath?.let { path ->
                                                val bounds = android.graphics.RectF()
                                                path.path.asAndroidPath()
                                                    .computeBounds(bounds, true)
                                                if (!bounds.isEmpty) {
                                                    completedPaths.add(path)
                                                    undoStack.add(
                                                        DrawingAction.DrawAction(
                                                            listOf(
                                                                path
                                                            )
                                                        )
                                                    )
                                                    redoStack.clear()
                                                }
                                            }
                                        } else if (isErasing) {
                                            val originalPaths = pathsBeforeErase
                                            val newPaths = completedPaths.toList()
                                            val modifiedPaths =
                                                originalPaths.filter { originalPath ->
                                                    !newPaths.any { it.path == originalPath.path }
                                                }
                                            if (modifiedPaths.isNotEmpty()) {
                                                undoStack.add(
                                                    DrawingAction.EraseAction(
                                                        modifiedPaths,
                                                        newPaths
                                                    )
                                                )
                                                redoStack.clear()
                                            }
                                            isErasing = false
                                        }
                                    }
                                    currentPath = null
                                    isDragInitiatedInBounds = false
                                },
                                onDragCancel = {
                                    currentPath = null
                                    isDragInitiatedInBounds = false
                                }
                            )
                        }
                    }
            ) {
                drawRect(color = canvasBackgroundColor, size = size)
                drawGridBackground(gridType)
                completedPaths.forEach { drawThePath(it) }
                currentPath?.let { drawThePath(it) }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Drawing") },
            text = { Text("Do you want to save this drawing as an image?") },
            confirmButton = {
                Button(onClick = {
                    showSaveDialog = false; coroutineScope.launch {
                    saveDrawingAsImage(
                        context,
                        completedPaths,
                        gridType,
                        canvasBackgroundColor
                    ) { success, filePath ->
                        if (success) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Drawing saved to: $filePath") }
                        } else {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Failed to save drawing") }
                        }
                    }
                }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } })
    }
}

private fun limitPathPoints(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    val maxPoints = 10000
    return if (points.size > maxPoints) {
        val step = points.size / maxPoints
        points.filterIndexed { index, _ -> index % step == 0 }.take(maxPoints)
    } else {
        points
    }
}

private fun extractPointsFromPath(path: Path): List<Pair<Float, Float>> {
    val points = mutableListOf<Pair<Float, Float>>()
    try {
        val androidPath = path.asAndroidPath()
        val pathMeasure = android.graphics.PathMeasure(androidPath, false)
        val length = pathMeasure.length
        var currentDistance = 0f
        val step = maxOf(5f, length / 500)

        while (currentDistance < length) {
            val point = FloatArray(2)
            pathMeasure.getPosTan(currentDistance, point, null)
            points.add(point[0] to point[1])
            currentDistance += step
        }
    } catch (_: Exception) {
        // Return empty
    }
    return limitPathPoints(points)
}

private fun hasVisiblePaths(paths: List<DrawingPath>): Boolean {
    return paths.any { drawingPath ->
        try {
            val bounds = android.graphics.RectF()
            drawingPath.path.asAndroidPath().computeBounds(bounds, true)
            !bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0
        } catch (_: Exception) {
            false
        }
    }
}

private fun saveDrawingAsImage(
    context: android.content.Context,
    paths: List<DrawingPath>,
    gridType: GridType,
    backgroundColor: Color,
    callback: (Boolean, String) -> Unit,
) {
    try {
        val width = 1080
        val height = 1920
        val bitmap = createBitmap(width, height)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(backgroundColor.toArgb())
        drawGridOnBitmap(canvas, gridType)
        paths.forEach { drawingPath -> drawPathOnBitmap(canvas, drawingPath) }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Drawing_$timeStamp.png"
        val picturesDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, "SuperReader")
        if (!appDir.exists()) appDir.mkdirs()

        val file = File(appDir, fileName)
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }

        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
            null
        )
        callback(true, file.absolutePath)
    } catch (_: Exception) {
        callback(false, "")
    }
}

private fun drawGridOnBitmap(canvas: android.graphics.Canvas, gridType: GridType) {
    val width = 1080
    val height = 1920
    val gridSize = 50f
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.LTGRAY
        alpha = 128
        strokeWidth = 1f
    }

    when (gridType) {
        GridType.SQUARE -> {
            for (i in 0..width step gridSize.toInt()) {
                canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), paint)
            }
            for (i in 0..height step gridSize.toInt()) {
                canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), paint)
            }
        }

        GridType.DOT -> {
            for (x in 0..width step gridSize.toInt()) {
                for (y in 0..height step gridSize.toInt()) {
                    canvas.drawCircle(x.toFloat(), y.toFloat(), 2f, paint)
                }
            }
        }

        GridType.RULED -> {
            for (i in 0..height step (gridSize * 1.5).toInt()) {
                canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), paint)
            }
        }

        GridType.NONE -> {}
    }
}

private fun drawPathOnBitmap(canvas: android.graphics.Canvas, drawingPath: DrawingPath) {
    val paint = android.graphics.Paint().apply {
        color = drawingPath.color.toArgb()
        strokeWidth = drawingPath.strokeWidth
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }

    when (drawingPath.penType) {
        PenType.HIGHLIGHTER -> paint.alpha = 76
        PenType.SKETCH -> paint.alpha = 128
        else -> paint.alpha = 255
    }

    canvas.drawPath(drawingPath.path.asAndroidPath(), paint)
}

private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

private fun splitPathAroundPoint(
    drawingPath: DrawingPath,
    erasePoint: Offset,
    eraserRadius: Float,
): List<DrawingPath> {
    val androidPath = drawingPath.path.asAndroidPath()
    val pathMeasure = android.graphics.PathMeasure(androidPath, false)
    val length = pathMeasure.length
    val segments = mutableListOf<DrawingPath>()
    var currentDistance = 0f
    val step = 5f
    var segmentStart: Offset? = null
    var currentSegment = Path()

    while (currentDistance < length) {
        val point = FloatArray(2)
        pathMeasure.getPosTan(currentDistance, point, null)
        val currentPoint = Offset(point[0], point[1])

        val distanceToErasePoint =
            sqrt((currentPoint.x - erasePoint.x).pow(2) + (currentPoint.y - erasePoint.y).pow(2))

        if (distanceToErasePoint > eraserRadius) {
            if (segmentStart == null) {
                segmentStart = currentPoint
                currentSegment.moveTo(currentPoint.x, currentPoint.y)
            } else {
                currentSegment.lineTo(currentPoint.x, currentPoint.y)
            }
        } else {
            if (segmentStart != null) {
                segments.add(drawingPath.copy(path = Path().apply { addPath(currentSegment) }))
                currentSegment = Path()
                segmentStart = null
            }
        }
        currentDistance += step
    }

    if (segmentStart != null) {
        segments.add(drawingPath.copy(path = Path().apply { addPath(currentSegment) }))
    }
    return segments
}

private fun isPathWithinEraserRadius(path: Path, eraserPos: Offset, radius: Float): Boolean {
    val androidPath = path.asAndroidPath()
    val pathMeasure = android.graphics.PathMeasure(androidPath, false)
    val length = pathMeasure.length
    val step = 5f
    var currentDistance = 0f
    while (currentDistance < length) {
        val point = FloatArray(2)
        pathMeasure.getPosTan(currentDistance, point, null)
        val pathPoint = Offset(point[0], point[1])
        val distance = sqrt((pathPoint.x - eraserPos.x).pow(2) + (pathPoint.y - eraserPos.y).pow(2))
        if (distance <= radius) return true
        currentDistance += step
    }
    return false
}

private fun DrawScope.drawGridBackground(gridType: GridType) {
    val gridSize = 50f
    val strokeWidth = 1.dp.toPx()
    val lineColor = Color.LightGray.copy(alpha = 0.5f)
    when (gridType) {
        GridType.SQUARE -> {
            for (i in 0..size.width.toInt() step gridSize.toInt())
                drawLine(
                    lineColor,
                    Offset(i.toFloat(), 0f),
                    Offset(i.toFloat(), size.height),
                    strokeWidth
                )
            for (i in 0..size.height.toInt() step gridSize.toInt())
                drawLine(
                    lineColor,
                    Offset(0f, i.toFloat()),
                    Offset(size.width, i.toFloat()),
                    strokeWidth
                )
        }

        GridType.DOT -> {
            for (x in 0..size.width.toInt() step gridSize.toInt())
                for (y in 0..size.height.toInt() step gridSize.toInt())
                    drawCircle(lineColor, 2.dp.toPx(), Offset(x.toFloat(), y.toFloat()))
        }

        GridType.RULED -> {
            for (i in 0..size.height.toInt() step (gridSize * 1.5).toInt())
                drawLine(
                    lineColor,
                    Offset(0f, i.toFloat()),
                    Offset(size.width, i.toFloat()),
                    strokeWidth
                )
        }

        GridType.NONE -> {}
    }
}

private fun DrawScope.drawThePath(drawingPath: DrawingPath) {
    when (drawingPath.penType) {
        PenType.SKETCH -> {
            repeat(2) {
                val jitterX = Random.nextFloat() * 2f - 1f
                val jitterY = Random.nextFloat() * 2f - 1f
                drawPath(
                    path = Path().apply { addPath(drawingPath.path, Offset(jitterX, jitterY)) },
                    color = drawingPath.color.copy(alpha = 0.5f),
                    style = Stroke(
                        width = drawingPath.strokeWidth / 1.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        PenType.HIGHLIGHTER -> {
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color.copy(alpha = 0.3f),
                style = Stroke(
                    width = drawingPath.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        else -> {
            drawPath(
                path = drawingPath.path,
                color = drawingPath.color,
                style = Stroke(
                    width = drawingPath.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

// Helper function to compare if two path lists are equal
private fun arePathsEqual(paths1: List<DrawingPath>, paths2: List<DrawingPath>): Boolean {
    if (paths1.size != paths2.size) return false

    return paths1.zip(paths2).all { (path1, path2) ->
        path1.color == path2.color &&
                path1.strokeWidth == path2.strokeWidth &&
                path1.penType == path2.penType &&
                arePathsEqual(path1.path, path2.path)
    }
}

// Helper function to compare if two Path objects are equal
private fun arePathsEqual(path1: Path, path2: Path): Boolean {
    val points1 = extractPointsFromPath(path1)
    val points2 = extractPointsFromPath(path2)

    if (points1.size != points2.size) return false

    // Allow small floating point differences
    return points1.zip(points2).all { (p1, p2) ->
        abs(p1.first - p2.first) < 0.1f &&
                abs(p1.second - p2.second) < 0.1f
    }
}