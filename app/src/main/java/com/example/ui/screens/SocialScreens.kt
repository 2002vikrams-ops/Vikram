package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CallHistoryEntity
import com.example.data.ContactEntity
import com.example.data.GameStatsEntity
import com.example.data.MessageEntity
import com.example.ui.CallState
import com.example.ui.SocialViewModel
import java.text.SimpleDateFormat
import java.util.*

// Navigation screens
sealed interface AppScreen {
    object Dashboard : AppScreen
    data class ChatDetails(val contactId: String) : AppScreen
}

// Inner Tabs
enum class HomeTab(val title: String, val icon: ImageVector, val unselectedIcon: ImageVector) {
    CHATS("Chats", Icons.Default.Chat, Icons.Outlined.Chat),
    SNAPS("Snaps", Icons.Default.PhotoCamera, Icons.Outlined.PhotoCamera),
    CALLS("Calls", Icons.Default.Call, Icons.Outlined.Call),
    GAMES("Games Hub", Icons.Default.SportsEsports, Icons.Outlined.SportsEsports)
}

/**
 * Main Orchestrator Screen supporting dynamic animation transitions.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainSocialApp(viewModel: SocialViewModel) {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Dashboard) }

    // Observers
    val activeCall by viewModel.activeCallState.collectAsState()

    // Full Screen calling overlay overrides everything else
    if (activeCall is CallState.Active) {
        CallOverlay(
            activeCall = activeCall as CallState.Active,
            onEndCall = { viewModel.endCall() },
            onToggleMute = { viewModel.toggleCallMute() },
            onToggleSpeaker = { viewModel.toggleCallSpeaker() },
            onToggleCamera = { viewModel.toggleCallCamera() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState is AppScreen.ChatDetails) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "ScreenTransitions"
        ) { screen ->
            when (screen) {
                is AppScreen.Dashboard -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onOpenChat = { contactId ->
                            viewModel.selectContactForChat(contactId)
                            currentScreen = AppScreen.ChatDetails(contactId)
                        }
                    )
                }
                is AppScreen.ChatDetails -> {
                    ChatScreen(
                        contactId = screen.contactId,
                        viewModel = viewModel,
                        onBack = {
                            viewModel.selectContactForChat(null)
                            currentScreen = AppScreen.Dashboard
                        }
                    )
                }
            }
        }
    }
}

/**
 * Holds Tabs Navigation inside standard M3 Scaffold layout
 */
@Composable
fun DashboardScreen(
    viewModel: SocialViewModel,
    onOpenChat: (contactId: String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(HomeTab.CHATS) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("dashboard_nav_bar"),
                tonalElevation = 8.dp
            ) {
                HomeTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.icon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                HomeTab.CHATS -> ChatsTab(viewModel, onOpenChat)
                HomeTab.SNAPS -> SnapsTab(viewModel, onOpenChat)
                HomeTab.CALLS -> CallsTab(viewModel)
                HomeTab.GAMES -> GamesTab(viewModel)
            }
        }
    }
}

// ==========================================
// 1. CHATS TAB
// ==========================================
@Composable
fun ChatsTab(
    viewModel: SocialViewModel,
    onOpenChat: (String) -> Unit
) {
    val contactList by viewModel.contacts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = contactList.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.bio.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App header display
        Text(
            text = "VIBE CONNECT",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Custom Modern Filled Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_search_bar"),
            placeholder = { Text("Search friends or bios...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Grid-Breaking Carousel for Quick Contact Actions on top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("QUICK RINGS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            contactList.forEach { contact ->
                Column(
                    modifier = Modifier
                        .clickable { viewModel.startCall(contact.id, "VIDEO") }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.Gray.copy(alpha = 0.2f), CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(contact.avatarUrl, fontSize = 28.sp)
                        }
                        if (contact.isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color(0xFF33FF33), CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = contact.name.substringBefore(" "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // Contact lists
        if (filteredContacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SentimentDissatisfied, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No friends match your query.", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredContacts) { contact ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(contact.id) }
                            .testTag("contact_card_${contact.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(contact.avatarUrl, fontSize = 26.sp)
                                }
                                if (contact.isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(Color(0xFFFFCC00), CircleShape) // Gold glow
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = contact.bio,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Quick actions buttons
                            IconButton(onClick = { viewModel.startCall(contact.id, "VIDEO") }) {
                                Icon(Icons.Default.Videocam, "Call ${contact.name}", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.startCall(contact.id, "AUDIO") }) {
                                Icon(Icons.Default.Call, "Voice Call", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. CHAT DETAILS SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactId: String,
    viewModel: SocialViewModel,
    onBack: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var activeSnapViewMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showDrawDialog by remember { mutableStateOf(false) }

    val contactList by viewModel.contacts.collectAsState()
    val chatMessages by viewModel.activeChatMessages.collectAsState()

    val contact = contactList.firstOrNull { it.id == contactId } ?: return

    // Screen Scaffold
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.Gray.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(contact.avatarUrl, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(contact.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = contact.statusText,
                                fontSize = 11.sp,
                                color = if (contact.isOnline) Color(0xFF33FF33) else Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    // Call triggering triggers VM calling sequence
                    IconButton(onClick = { viewModel.startCall(contactId, "AUDIO") }) {
                        Icon(Icons.Default.Call, "Audio Call")
                    }
                    IconButton(onClick = { viewModel.startCall(contactId, "VIDEO") }) {
                        Icon(Icons.Default.Videocam, "Video Call")
                    }
                    IconButton(onClick = {
                        viewModel.setTicTacToeOpponent(contactId)
                    }) {
                        Icon(Icons.Default.SportsEsports, "Challenge Tic-Tac-Toe")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Message feed list
            if (chatMessages.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    ) {
                        Text(
                            text = "✨ NEW CONVERSATION ✨",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This thread is encrypted & stored securely inside your offline SQLite state. Trade text and disappearing Snaps instantly!",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatMessages) { message ->
                        val isMe = message.senderId == "user"
                        ChatBubble(
                            message = message,
                            isMe = isMe,
                            onViewSnap = { activeSnapViewMessage = message },
                            onDelete = { viewModel.deleteMessage(message.id) }
                        )
                    }
                }
            }

            // Chat input row with media options
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Snap Drawing button
                    IconButton(
                        onClick = { showDrawDialog = true },
                        modifier = Modifier.testTag("draw_snap_icon_button")
                    ) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = "Send Custom Snap Drawing",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Text Field
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Write chat...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_text_input"),
                        shape = RoundedCornerShape(20.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = false
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button
                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.sendMessage(textInput)
                                textInput = ""
                            }
                        },
                        enabled = textInput.isNotBlank(),
                        modifier = Modifier.testTag("send_chat_button")
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send Message",
                            tint = if (textInput.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            }
        }
    }

    // Dynamic drawing modal
    if (showDrawDialog) {
        SnapDrawDialog(
            onDismiss = { showDrawDialog = false },
            onSendSnap = { serializedData, durationSeconds ->
                viewModel.sendMessage(
                    text = "Shared a Draw Snap 📸",
                    snapUri = serializedData,
                    snapDuration = durationSeconds
                )
                showDrawDialog = false
            }
        )
    }

    // Snap Disappearing modal
    activeSnapViewMessage?.let { msg ->
        DisappearingSnapViewer(
            snapMessage = msg,
            onFinished = {
                viewModel.viewSnap(msg.id)
                activeSnapViewMessage = null
            }
        )
    }
}

/**
 * Visual Chat Bubble formatting
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: MessageEntity,
    isMe: Boolean,
    onViewSnap: () -> Unit,
    onDelete: () -> Unit
) {
    val isSnap = message.snapUri != null
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val alignment = if (isMe) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = onDelete,
                onClick = {}
            ),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isSnap) {
                    // Disappearing Snap Bubble
                    Row(
                        modifier = Modifier
                            .clickable {
                                if (!message.isViewed) onViewSnap()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (message.isViewed) Icons.Default.MailOutline else Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = if (message.isViewed) Color.Gray else Color(0xFFFF3366)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (message.isViewed) "SNAP OPENED" else "TAP TO VIEW SNAP",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (message.isViewed) Color.Gray else Color(0xFFFF3366)
                            )
                            Text(
                                text = if (message.isViewed) "Self-destructed" else "disappearing timer: " + (if (message.snapDuration == 99) "Never" else "${message.snapDuration}s"),
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    // Normal text message
                    Text(
                        text = message.text,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Time label
                val formatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                Text(
                    text = formatter.format(Date(message.timestamp)),
                    fontSize = 9.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ==========================================
// 3. SNAPS GALLERY STORIES TAB
// ==========================================
@Composable
fun SnapsTab(
    viewModel: SocialViewModel,
    onOpenChat: (String) -> Unit
) {
    val snapList by viewModel.snaps.collectAsState()
    val contactList by viewModel.contacts.collectAsState()
    var showQuickDraw by remember { mutableStateOf(false) }
    var selectedContactDestiny by remember { mutableStateOf<String?>(null) }

    // Mock presets you can instantly share with anyone (as request guidelines demand no dead assets)
    val snapPresets = listOf(
        Pair("Golden Hour Scout 🌅", "0;15.0;100.0,200.0_150.0,240.0"),
        Pair("Cyberpunk Arcade Neon 🌆", "1;20.0;50.0,80.0_120.0,200.0"),
        Pair("Delicious Training Shake 🥑", "3;12.0;10.0,10.0_100.0,150.0")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SNAP ENGINE",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            // Paint Standalone Button
            Button(
                onClick = { showQuickDraw = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366))
            ) {
                Icon(Icons.Default.Gesture, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("PAINT SNAP")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset story templates you can instantly send to friends
        Text(
            text = "FAST SNAP STORY TEMPLATES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(120.dp)
        ) {
            items(snapPresets) { preset ->
                Card(
                    onClick = {
                        // Pick a destination contact
                        selectedContactDestiny = contactList.firstOrNull()?.id
                        viewModel.sendMessage(
                            text = "Presetted snap shared! -> " + preset.first,
                            snapUri = preset.second,
                            snapDuration = 5
                        )
                        selectedContactDestiny?.let { onOpenChat(it) }
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            preset.first,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SHARED SNAPS ARCHIVE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (snapList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(54.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "No snaps in database logs yet.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Text(
                        "Send a snap in active chats to see them stored here!",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(snapList) { msg ->
                    val senderName = contactList.firstOrNull { it.id == msg.senderId }?.name ?: "User"
                    val receiverName = contactList.firstOrNull { it.id == msg.contactId }?.name ?: "User"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { onOpenChat(msg.contactId) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFFF3366).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFFFF3366))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$senderName ➔ $receiverName",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (msg.isViewed) "Opened & deleted" else "Disappearing timer preset: ${msg.snapDuration}s • CLICK TO RE-OPEN CHAT",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            if (msg.isViewed) {
                                Icon(Icons.Default.Check, contentDescription = "Viewed", tint = Color.Green)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFFFF3366), CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickDraw) {
        // standalone drawing
        SnapDrawDialog(
            onDismiss = { showQuickDraw = false },
            onSendSnap = { serializedStrokes, durationS ->
                // Send it to the first contact by default
                val firstCId = contactList.firstOrNull()?.id ?: "gemini"
                viewModel.selectContactForChat(firstCId)
                viewModel.sendMessage(
                    text = "Shared draw snap!",
                    snapUri = serializedStrokes,
                    snapDuration = durationS
                )
                showQuickDraw = false
                onOpenChat(firstCId)
            }
        )
    }
}

// ==========================================
// 4. CALL LOGS TAB
// ==========================================
@Composable
fun CallsTab(viewModel: SocialViewModel) {
    val callHistoryList by viewModel.callLogs.collectAsState()
    val contactList by viewModel.contacts.collectAsState()

    var showDialpad by remember { mutableStateOf(false) }
    var dialInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CALL LOGS",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Row {
                IconButton(onClick = { showDialpad = true }) {
                    Icon(Icons.Default.Dialpad, contentDescription = "Open dialing pad", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { viewModel.clearAllCallHistory() }) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Clear logs", tint = Color.Red)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (callHistoryList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhoneCallback, contentDescription = null, modifier = Modifier.size(54.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No call records in SQLite logs.", color = Color.Gray)
                    Text("Calling friends displays records here.", color = Color.Gray, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(callHistoryList) { log ->
                    val contact = contactList.firstOrNull { it.id == log.contactId }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Call indicator type icon
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        color = if (log.direction == "MISSED") Color.Red.copy(alpha = 0.15f) else Color.Green.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (log.callType == "VIDEO") Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = null,
                                    tint = if (log.direction == "MISSED") Color.Red else Color.Green
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact?.name ?: "Unknown virtual line",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (log.direction == "OUTGOING") Icons.Default.CallMade else Icons.Default.CallReceived,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${log.direction} • duration: ${log.durationSeconds}s",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            // Timestamp format
                            val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                            Text(
                                text = formatter.format(Date(log.timestamp)),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialpad Overlay dialog
    if (showDialpad) {
        AlertDialog(
            onDismissRequest = { showDialpad = false },
            title = { Text("DIAL OUTWARD", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = dialInput.ifEmpty { "Enter Number" },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Dial Grid
                    val buttons = listOf(
                        "1", "2", "3",
                        "4", "5", "6",
                        "7", "8", "9",
                        "*", "0", "#"
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(230.dp)
                    ) {
                        items(buttons) { btn ->
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .clickable { dialInput += btn },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(btn, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { if (dialInput.isNotEmpty()) dialInput = dialInput.dropLast(1) }) {
                            Icon(Icons.Default.Backspace, contentDescription = "Backspace")
                        }
                        Button(
                            onClick = {
                                if (dialInput.isNotBlank()) {
                                    // Trigger call to the first contact as a mock connection
                                    viewModel.startCall("gemini", "AUDIO")
                                    showDialpad = false
                                }
                            },
                            enabled = dialInput.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Dial")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CALL")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialpad = false; dialInput = "" }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

// ==========================================
// 5. GAMES HUB TAB
// ==========================================
@Composable
fun GamesTab(viewModel: SocialViewModel) {
    val ticTacToeState by viewModel.ticTacToe.collectAsState()
    val memoryMatchState by viewModel.memoryMatch.collectAsState()
    val gameStatsList by viewModel.gameStats.collectAsState()

    var activeMiniGame by remember { mutableStateOf<String?>(null) } // "tic_tac_toe", "memory_match", or null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (activeMiniGame == null) {
            Text(
                text = "GAMES HUB",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scoreboard Summary Panel
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SQLITE GAMING SCOREBOARD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    gameStatsList.forEach { state ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(state.gameName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Wins: ${state.wins} | Losses: ${state.losses} | Draw: ${state.draws}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Catalog Section
            Text("SELECT SOCIAL GAME TO PLAY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))

            // Game 1 Card: Tic-Tac-Toe
            Card(
                onClick = {
                    viewModel.setTicTacToeOpponent("alice")
                    activeMiniGame = "tic_tac_toe"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("game_item_tic_tac_toe")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF00FFCC).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("❌", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tic-Tac-Toe Arena", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Challenge Alice or Gemini AI in real-time matchups", color = Color.Gray, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Arena")
                }
            }

            // Game 2 Card: Memory Match
            Card(
                onClick = {
                    viewModel.startMemoryMatchGame()
                    activeMiniGame = "memory_match"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("game_item_memory_match")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFFF3366).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👻", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Emoji Memory flipping", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Match corresponding symbols with lowest moves count", color = Color.Gray, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Match")
                }
            }

        } else if (activeMiniGame == "tic_tac_toe") {
            // RENDERING TIC TAC TOE SCREEN
            Column(modifier = Modifier.fillMaxSize()) {
                // Return header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeMiniGame = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Exit game")
                    }
                    Text("TIC-TAC-TOE", fontWeight = FontWeight.Bold)
                    Button(onClick = { viewModel.resetTicTacToe() }) {
                        Text("RESTART")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Opponent Switch segment picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Opponent:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row {
                        AssistChip(
                            onClick = { viewModel.setTicTacToeOpponent("alice") },
                            label = { Text("Alice") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (ticTacToeState.opponentId == "alice") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        AssistChip(
                            onClick = { viewModel.setTicTacToeOpponent("gemini") },
                            label = { Text("Gemini AI") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (ticTacToeState.opponentId == "gemini") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Arena State message
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        ticTacToeState.winner == "USER" -> {
                            Text("🏆 YOU WON! Opponent defeated!", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        ticTacToeState.winner == "OPPONENT" -> {
                            Text("💀 YOU LOST! Bot successfully claimed victory!", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        ticTacToeState.winner == "DRAW" -> {
                            Text("⚔️ MATCH DRAW! Both blocked perfectly!", color = Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        ticTacToeState.isThinking -> {
                            Text("🤖 Bot is calculating strategic options...", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                        }
                        ticTacToeState.isUserTurn -> {
                            Text("⚡ YOUR TURN! Pick your spot on the grid", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        else -> {
                            Text("Wait...")
                        }
                    }
                }

                // Grid 3x3 canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in 0..2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0..2) {
                                    val index = row * 3 + col
                                    val symbol = ticTacToeState.board[index]
                                    val isWinningCell = ticTacToeState.winningLine?.contains(index) == true

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (isWinningCell) Color(0xFF00FFCC).copy(alpha = 0.3f)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .border(
                                                width = if (isWinningCell) 3.dp else 1.dp,
                                                color = if (isWinningCell) Color(0xFF00FFCC) else Color.DarkGray,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable { viewModel.makeTicTacToeMove(index) }
                                            .testTag("ttt_cell_$index"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (symbol == "X") {
                                            Text(
                                                "X",
                                                fontSize = 44.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF00FFCC) // Neon Cyan
                                            )
                                        } else if (symbol == "O") {
                                            Text(
                                                "O",
                                                fontSize = 44.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFFF3366) // Neon Pink
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } else if (activeMiniGame == "memory_match") {
            // RENDERING EMOJI MATCH FLIPPING GAME
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeMiniGame = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Exit game")
                    }
                    Text("MY GAME ARENA", fontWeight = FontWeight.Bold)
                    Button(onClick = { viewModel.startMemoryMatchGame() }) {
                        Text("RESET")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Dashboard Bar
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MOVES", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(memoryMatchState.moves.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ELAPSED TIME", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("${memoryMatchState.secondsElapsed}s", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STATUS", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (memoryMatchState.isCompleted) "🏆 COMPLETE!" else "RUNNING",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (memoryMatchState.isCompleted) Color.Green else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4x4 interactive Cards Grid of 3D animated flips
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(memoryMatchState.cards) { card ->
                        // 3D Animated card flip value
                        val rotationY by animateFloatAsState(
                            targetValue = if (card.isFlipped || card.isMatched) 180f else 0f,
                            animationSpec = tween(400)
                        )

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    this.rotationY = rotationY
                                    cameraDistance = 12 * density
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (card.isMatched) Color(0xFF00FFCC).copy(alpha = 0.2f)
                                    else if (card.isFlipped) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                                .border(
                                    width = if (card.isMatched) 2.dp else 1.dp,
                                    color = if (card.isMatched) Color(0xFF00FFCC) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (!card.isMatched && !card.isFlipped) {
                                        viewModel.flipMemoryCard(card.id)
                                    }
                                }
                                .testTag("memory_card_${card.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (rotationY > 90f) {
                                // Front showing Emojis with mirrored flip compensation
                                Text(
                                    text = card.emoji,
                                    fontSize = 28.sp,
                                    modifier = Modifier.graphicsLayer { this.rotationY = 180f }
                                )
                            } else {
                                // Face down showing social icon
                                Icon(
                                    Icons.Default.DeviceUnknown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
