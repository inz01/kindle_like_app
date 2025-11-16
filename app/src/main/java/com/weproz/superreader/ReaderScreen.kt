// ReaderScreen.kt

package com.weproz.superreader

import androidx.compose.animation.AnimatedVisibility // Show/hide animation
import androidx.compose.animation.fadeIn // Fade in animation
import androidx.compose.animation.fadeOut // Fade out animation
import androidx.compose.foundation.background // Background color
import androidx.compose.foundation.layout.Box // Container layout
import androidx.compose.foundation.layout.Row // Horizontal layout
import androidx.compose.foundation.layout.fillMaxSize // Full screen
import androidx.compose.foundation.layout.fillMaxWidth // Full width
import androidx.compose.foundation.layout.height // Set height
import androidx.compose.foundation.layout.padding // Add spacing
import androidx.compose.foundation.shape.RoundedCornerShape // Rounded corners
import androidx.compose.material.icons.Icons // Material icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Back arrow
import androidx.compose.material.icons.filled.Search // Search icon
import androidx.compose.material3.ExperimentalMaterial3Api // Material3 API
import androidx.compose.material3.Icon // Icon component
import androidx.compose.material3.IconButton // Clickable icon
import androidx.compose.material3.Scaffold // Screen structure
import androidx.compose.material3.Surface // Background surface
import androidx.compose.material3.Text // Text component
import androidx.compose.runtime.Composable // UI function
import androidx.compose.runtime.getValue // State getter
import androidx.compose.runtime.mutableIntStateOf // Integer state
import androidx.compose.runtime.mutableStateOf // State holder
import androidx.compose.runtime.remember // Remember state
import androidx.compose.runtime.rememberCoroutineScope // Coroutine scope
import androidx.compose.runtime.setValue // State setter
import androidx.compose.ui.Alignment // Element alignment
import androidx.compose.ui.Modifier // UI modifier
import androidx.compose.ui.graphics.Color // Color values
import androidx.compose.ui.text.font.FontWeight // Text weight
import androidx.compose.ui.text.style.TextAlign // Text alignment
import androidx.compose.ui.text.style.TextOverflow // Text overflow
import androidx.compose.ui.unit.dp // Density pixels
import androidx.compose.ui.unit.sp // Scaled pixels
import androidx.compose.ui.viewinterop.AndroidView // Android view
import androidx.core.net.toUri // URI conversion
import androidx.navigation.NavController // Navigation controller
import com.github.barteksc.pdfviewer.PDFView // PDF viewer
import com.weproz.superreader.BookType.EPUB // EPUB format
import com.weproz.superreader.BookType.OTHER // Other formats
import com.weproz.superreader.BookType.PDF // PDF format
import kotlinx.coroutines.Job // Coroutine job
import kotlinx.coroutines.delay // Delay function
import kotlinx.coroutines.launch // Launch coroutine


@OptIn(ExperimentalMaterial3Api::class) // Material3 API

@Composable

fun ReaderScreen(
    // PDF reader screen

    uriString: String, // File path

    bookType: BookType, // File type

    navController: NavController, // Navigation controller

    filename: String, // File name

    isFocusModeActive: Boolean = false,  // Add this parameter


) {

    var isImmersiveMode by remember { mutableStateOf(false) } // Hide UI

    var pageCount by remember { mutableIntStateOf(0) } // Total pages

    var currentPage by remember { mutableIntStateOf(0) } // Current page

    var showPageNumberOnScroll by remember { mutableStateOf(false) } // Show page scroll

    val coroutineScope = rememberCoroutineScope() // Coroutine scope

    var hidePageNumberJob: Job? by remember { mutableStateOf(null) } // Hide job

    Scaffold( // Screen structure

// The Scaffold itself is transparent to show the dark background behind it

        containerColor = Color.Transparent, // Transparent background

        topBar = { // Top app bar
            if (!isImmersiveMode) { // Show if not immersive
                Surface( // Header surface
                    color = Color.Black.copy(alpha = 0.3f), // Semi-transparent black
                    modifier = Modifier.fillMaxWidth() // Full width
                ) {
                    Row( // Horizontal layout
                        modifier = Modifier
                            .fillMaxWidth() // Full width
                            .height(52.dp) // Header height
                            .padding(horizontal = 4.dp), // Side padding
                        verticalAlignment = Alignment.CenterVertically // Center vertically
                    ) {
                        // Back button (left)
                        IconButton(onClick = { navController.popBackStack() }) { // Back navigation
                            Icon( // Back icon
                                Icons.AutoMirrored.Filled.ArrowBack, // Back arrow
                                contentDescription = "Back", // Accessibility text
                                tint = Color.White // White color
                            )
                        }
                        // Title (middle)
                        Text( // File name
                            text = filename, // Display name
                            maxLines = 1, // Single line
                            overflow = TextOverflow.Ellipsis, // Truncate long names
                            color = Color.White, // White text
                            // ✅ CHANGED: Weight makes the title fill all available space
                            modifier = Modifier
                                .weight(1f) // Take space
                                .padding(horizontal = 12.dp) // Side padding
                        )
                        // ✅ NEW: Search button (right)
                        IconButton(onClick = { /* TODO: Implement Search Action */ }) { // Search function
                            Icon( // Search icon
                                Icons.Default.Search, // Search icon
                                contentDescription = "Search in document", // Accessibility text
                                tint = Color.White // White color
                            )
                        }
                    }
                }
            }
        },

        bottomBar = { /* No bottom bar */ } // No bottom navigation

    ) { paddingValues -> // Screen padding

// ✅ NEW: This Box now has a dark background

        Box( // Container layout

            modifier = Modifier

                .padding(paddingValues) // Apply padding

                .fillMaxSize() // Full screen

                .background(Color.DarkGray) // This color shows around the PDF pages

        ) {

            when (bookType) { // Switch file type

                PDF -> { // PDF handling

                    AndroidView( // PDF viewer

                        factory = { context -> PDFView(context, null) }, // Create PDF view

                        update = { pdfView -> // Configure PDF view

                            val loader =
                                if (uriString.startsWith("file:///android_asset/")) { // Asset file

                                    val assetFileName =
                                        uriString.removePrefix("file:///android_asset/") // Remove prefix

                                    pdfView.fromAsset(assetFileName) // Load from assets

                                } else {

                                    pdfView.fromUri(uriString.toUri()) // Load from URI

                                }



                            loader

// ✅ CONFIRMED: These settings provide the separate page view

                                .pageSnap(true) // Snap to pages

                                .autoSpacing(true) // Auto page spacing

                                .pageFling(true) // Page fling animation

                                .fitEachPage(true) // Fit pages

// Ensure night mode is off so pages stay white

                                .nightMode(false) // Disable night mode

                                .onTap { // Tap gesture

                                    isImmersiveMode = !isImmersiveMode // Toggle UI

                                    true // Consume tap

                                }

                                .onPageChange { page, _ -> // Page change

                                    currentPage = page // Update current page

                                }

                                .onLoad { nbPages -> // PDF loaded

                                    pageCount = nbPages // Set page count

                                }

                                .onPageScroll { page, positionOffset -> // Page scroll
                                    if (isImmersiveMode) { // In immersive mode

                                        hidePageNumberJob?.cancel() // Cancel hide job

                                        showPageNumberOnScroll = true // Show page number

                                        hidePageNumberJob =
                                            coroutineScope.launch { // Start hide timer

                                                delay(1500L) // Wait 1.5 seconds

                                                showPageNumberOnScroll = false // Hide page number

                                            }

                                    }
                                }

                                .load() // Load PDF

                        },

                        modifier = Modifier.fillMaxSize() // Full screen

                    )

                }

// ... other book types

                EPUB, OTHER -> { // Unsupported formats

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { // Center content

                        Text(text = "This format is not yet supported.") // Error message

                    }

                }

            }



            AnimatedVisibility( // Animated page counter

                visible = !isImmersiveMode && pageCount > 0, // Show conditions

                modifier = Modifier.align(Alignment.BottomCenter), // Bottom center

                enter = fadeIn(), // Fade in

                exit = fadeOut() // Fade out

            ) {

                Surface(
                    // Page counter background

                    modifier = Modifier.padding(bottom = 20.dp), // Bottom spacing

                    shape = RoundedCornerShape(50), // Pill shape

                    color = Color.White.copy(alpha = 0.85f), // Semi-transparent white

                    tonalElevation = 6.dp, // Shadow elevation

                ) {

                    Text( // Page counter text

                        text = "${currentPage + 1} / $pageCount", // Current/total pages

                        modifier = Modifier.padding(
                            horizontal = 20.dp,
                            vertical = 6.dp
                        ), // Text padding

                        color = Color.Black, // Black text

                        fontSize = 14.sp, // Text size

                        fontWeight = FontWeight.Medium, // Text weight

                        textAlign = TextAlign.Center // Center aligned

                    )

                }

            }


// Overlay during immersive scroll

            AnimatedVisibility( // Scroll page counter

                visible = showPageNumberOnScroll, // Show on scroll

                modifier = Modifier.align(Alignment.BottomCenter), // Bottom center

                enter = fadeIn(), // Fade in

                exit = fadeOut() // Fade out

            ) {

                Surface(
                    // Scroll counter background

                    modifier = Modifier.padding(bottom = 36.dp), // Bottom spacing

                    shape = RoundedCornerShape(12.dp), // Rounded corners

                    color = Color.White.copy(alpha = 0.9f), // Semi-transparent white

                    tonalElevation = 8.dp, // Shadow elevation

                ) {

                    Text( // Scroll counter text

                        text = "${currentPage + 1} / $pageCount", // Current/total pages

                        modifier = Modifier.padding(
                            horizontal = 24.dp,
                            vertical = 8.dp
                        ), // Text padding

                        color = Color.Black, // Black text

                        fontSize = 16.sp, // Text size

                        fontWeight = FontWeight.SemiBold, // Text weight

                        textAlign = TextAlign.Center // Center aligned

                    )

                }

            }

        }

    }

}