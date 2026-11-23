package com.bloomcycle.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloomcycle.app.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val PrimaryPink = Color(0xFFE27B9B)
val AccentPink = Color(0xFFA94368)
val SoftPink = Color(0xFFFBE7ED)
val BackgroundLight = Color(0xFFFFF5F5)
val BackgroundDark = Color(0xFF1F1A24)
val CardLight = Color(0xFFFFFFFF)
val CardDark = Color(0xFF2A232F)
val InkLight = Color(0xFF534A62)
val InkDark = Color(0xFFF2E8F0)
val MutLight = Color(0xFF7A6A7F)
val MutDark = Color(0xFFBBAAC0)

@Composable
fun BloomTheme(
    themeMode: String = "auto",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = PrimaryPink,
            secondary = AccentPink,
            background = BackgroundDark,
            surface = CardDark,
            onBackground = InkDark,
            onSurface = InkDark
        )
    } else {
        lightColorScheme(
            primary = PrimaryPink,
            secondary = AccentPink,
            background = BackgroundLight,
            surface = CardLight,
            onBackground = InkLight,
            onSurface = InkLight
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun BloomFlowerLogo(modifier: Modifier = Modifier, sizeDp: Int = 80) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(SoftPink),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp * 0.6).dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val petalRadius = size.width / 4.5f
            val distance = size.width / 4.2f

            for (i in 0 until 5) {
                val angle = Math.toRadians((i * 72 - 90).toDouble())
                val x = center.x + distance * Math.cos(angle).toFloat()
                val y = center.y + distance * Math.sin(angle).toFloat()
                drawCircle(color = PrimaryPink, radius = petalRadius, center = Offset(x, y))
            }
            drawCircle(color = Color(0xFFFADADD), radius = petalRadius * 0.7f, center = center)
        }
    }
}

@Composable
fun MainAppScreen(repository: BloomRepository) {
    var activeTab by remember { mutableStateOf("home") }
    var authMode by remember { mutableStateOf("welcome") }
    var selectedArticleId by remember { mutableStateOf<Int?>(null) }
    var discussionMode by remember { mutableStateOf("list") }
    var selectedDiscussionId by remember { mutableStateOf<String?>(null) }
    var reportModalTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }

    var refreshCounter by remember { mutableStateOf(0) }

    val isDark = when (repository.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val cardBg = if (isDark) CardDark else CardLight
    val textColor = if (isDark) InkDark else InkLight
    val mutColor = if (isDark) MutDark else MutLight

    BloomTheme(themeMode = repository.theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isDark) BackgroundDark else BackgroundLight
        ) {
            val user = repository.currentUser
            val loggedIn = repository.isLoggedIn
            LaunchedEffect(Unit) {
                repository.restoreSession()
            }
            LaunchedEffect(activeTab, user?.id) {
                if (activeTab == "admin") repository.refreshAdminData()
            }

            if (!loggedIn || user == null) {
                if (authMode == "welcome") {
                    WelcomeScreen(
                        onRegisterClick = { authMode = "register" },
                        onLoginClick = { authMode = "login" }
                    )
                } else {
                    AuthScreen(
                        isRegister = authMode == "register",
                        repository = repository,
                        onBack = { authMode = "welcome" },
                        onSuccess = {
                            authMode = "welcome"
                            activeTab = "home"
                            refreshCounter++
                        }
                    )
                }
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = cardBg,
                            contentColor = textColor
                        ) {
                            val navItems = mutableListOf(
                                Triple("home", "Beranda", Icons.Default.Home),
                                Triple("cal", "Kalender", Icons.Default.DateRange),
                                Triple("note", "Catatan", Icons.Default.Edit),
                                Triple("dis", "Diskusi", Icons.Default.Forum),
                                Triple("art", "Artikel", Icons.Default.Book),
                                Triple("me", "Profil", Icons.Default.Person)
                            )
                            if (user.role == "admin") {
                                navItems.add(Triple("admin", "Admin", Icons.Default.AdminPanelSettings))
                            }

                            navItems.forEach { (tabKey, label, icon) ->
                                NavigationBarItem(
                                    selected = activeTab == tabKey,
                                    onClick = {
                                        activeTab = tabKey
                                        selectedArticleId = null
                                        discussionMode = "list"
                                    },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryPink,
                                        selectedTextColor = PrimaryPink,
                                        unselectedIconColor = mutColor,
                                        unselectedTextColor = mutColor,
                                        indicatorColor = SoftPink
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Crossfade(targetState = activeTab, label = "TabSwitch") { tab ->
                            when (tab) {
                                "home" -> HomeScreen(repository = repository, onQuickLog = { activeTab = "cal" })
                                "cal" -> CalendarScreen(repository = repository)
                                "note" -> NotesScreen(repository = repository)
                                "dis" -> DiscussionsScreen(
                                    repository = repository,
                                    mode = discussionMode,
                                    selectedId = selectedDiscussionId,
                                    onModeChange = { mode, id ->
                                        discussionMode = mode
                                        selectedDiscussionId = id
                                    },
                                    onReport = { title, id -> reportModalTarget = Pair(title, id) }
                                )
                                "art" -> ArticlesScreen(
                                    repository = repository,
                                    selectedArticleId = selectedArticleId,
                                    onSelectArticle = { id -> selectedArticleId = id },
                                    onReport = { title, id -> reportModalTarget = Pair(title, id) }
                                )
                                "me" -> ProfileScreen(
                                    repository = repository,
                                    onLogout = { refreshCounter++ },
                                    onReportBug = { reportModalTarget = Pair("Bug Aplikasi", "app") }
                                )
                                "admin" -> AdminScreen(repository = repository)
                            }
                        }

                        reportModalTarget?.let { (targetTitle, targetId) ->
                            ReportModalDialog(
                                targetTitle = targetTitle,
                                targetId = targetId,
                                onDismiss = { reportModalTarget = null },
                                onSubmit = { category, desc ->
                                    repository.addReport(
                                        targetType = "general",
                                        targetTitle = targetTitle,
                                        targetId = targetId,
                                        category = category,
                                        description = desc
                                    )
                                    reportModalTarget = null
                                    refreshCounter++
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BloomFlowerLogo(sizeDp = 96)
        Spacer(modifier = Modifier.height(16.dp))
        Text("BloomCycle", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Catat, pantau siklus menstruasimu, dan berbagi pengalaman di ruang diskusi secara aman.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
        ) {
            Text("Daftar Akun Baru", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Saya Sudah Punya Akun", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AuthScreen(
    isRegister: Boolean,
    repository: BloomRepository,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var ageStr by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (isRegister) "Daftar Akun Baru" else "Masuk Akun",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryPink
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (isRegister) "Buat akun online dengan nama, umur, email, dan kata sandi." else "Masuk menggunakan email dan kata sandi akun BloomCycle.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isRegister) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama Lengkap / Panggilan") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = ageStr,
                onValueChange = { ageStr = it },
                label = { Text("Umur (tahun)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Aktif") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Kata Sandi") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        if (isRegister) {
            Text("Gunakan 10–72 byte.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
        }
        Spacer(modifier = Modifier.height(16.dp))

        errorMessage?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {
                scope.launch {
                    isSubmitting = true
                    errorMessage = null
                    val err = if (isRegister) {
                        repository.register(name, ageStr.toIntOrNull() ?: -1, email, password)
                    } else {
                        repository.login(email, password)
                    }
                    isSubmitting = false
                    if (err == null) {
                        onSuccess()
                    } else {
                        errorMessage = err
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink),
            enabled = !isSubmitting
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(if (isRegister) "Daftar" else "Masuk", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HomeScreen(repository: BloomRepository, onQuickLog: () -> Unit) {
    val user = repository.currentUser
    val stats = repository.calculateStats()
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SoftPink),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BloomFlowerLogo(sizeDp = 56)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Halo, ${user?.name ?: "Sahabat"}!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkLight)
                        Text("Semangat jalani harimu! BloomCycle siap membantumu memantau siklus.", fontSize = 12.sp, color = MutLight)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status Siklus", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (stats.nextPeriodStart != null) {
                        val diffDays = repository.diffDays(todayStr, stats.nextPeriodStart)
                        val msg = when {
                            diffDays > 1 -> "$diffDays hari lagi menuju haid berikutnya"
                            diffDays == 1 -> "1 hari lagi, besok kemungkinan haid dimulai"
                            diffDays == 0 -> "Perkiraan haid dimulai hari ini"
                            else -> "${Math.abs(diffDays)} hari lewat dari perkiraan."
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SoftPink)
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(Math.abs(diffDays).toString(), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = AccentPink)
                                Text(msg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Perkiraan mulai ${stats.nextPeriodStart}", fontSize = 12.sp, color = MutLight)
                            }
                        }
                    } else {
                        Text("Belum ada data catatan haid. Tambahkan tanggal awal haid di Kalender.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onQuickLog,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
                    ) {
                        Text("Catat Haid Sekarang", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Analisis Siklus", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Rata-rata panjang siklus: ${stats.averageCycleLength?.let { "$it hari" } ?: "Belum cukup data"}", fontSize = 13.sp)
                    Text("• Durasi haid rata-rata: ${stats.averagePeriodDuration?.let { "$it hari" } ?: "Belum cukup data"}", fontSize = 13.sp)
                    Text("• Variasi siklus: ${stats.cycleVariation?.let { "$it hari" } ?: "Belum ada"}", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(repository: BloomRepository) {
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    var selectedDate by remember { mutableStateOf(todayStr) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Kalender Haid", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
        Spacer(modifier = Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tanggal Terpilih: $selectedDate", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val isRecorded = repository.isPeriodDay(selectedDate)
                Text(
                    if (isRecorded) "Tanggal ini tercatat sebagai hari haid." else "Tandai tanggal ini sebagai awal atau akhir haid.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { repository.addPeriodStart(selectedDate) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
                    ) {
                        Text("Awal Haid", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { repository.addPeriodEnd(selectedDate) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
                    ) {
                        Text("Akhir Haid", fontSize = 12.sp)
                    }
                }
                if (isRecorded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { repository.removePeriod(selectedDate) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hapus Record", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Riwayat Tanggal Haid", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (repository.periods.isEmpty()) {
                    Text("Belum ada riwayat haid.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                } else {
                    repository.periods.forEach { record ->
                        Text("• Awal: ${record.startDate} ${record.endDate?.let { " - Akhir: $it" } ?: ""}", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun NotesScreen(repository: BloomRepository) {
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    var flow by remember { mutableStateOf("Ringan") }
    var pain by remember { mutableStateOf("Ringan") }
    var mood by remember { mutableStateOf("Tenang") }
    var textNote by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Catatan Harian", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
        }

        item {
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tambah Catatan ($todayStr)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Aliran / Perdarahan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Ringan", "Sedang", "Banyak").forEach { f ->
                            FilterChip(
                                selected = flow == f,
                                onClick = { flow = f },
                                label = { Text(f) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tingkat Nyeri", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Ringan", "Sedang", "Kuat").forEach { p ->
                            FilterChip(
                                selected = pain == p,
                                onClick = { pain = p },
                                label = { Text(p) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Suasana Hati", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Tenang", "Biasa", "Emosional").forEach { m ->
                            FilterChip(
                                selected = mood == m,
                                onClick = { mood = m },
                                label = { Text(m) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = textNote,
                        onValueChange = { textNote = it },
                        label = { Text("Catatan Opsional") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            repository.saveNote(
                                DailyNote(
                                    date = todayStr,
                                    flow = flow,
                                    pain = pain,
                                    mood = mood,
                                    text = textNote
                                )
                            )
                            textNote = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
                    ) {
                        Text("Simpan Catatan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("Riwayat Catatan", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        items(repository.notes) { note ->
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(note.date, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = { repository.deleteNote(note.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text("Aliran: ${note.flow} | Nyeri: ${note.pain} | Mood: ${note.mood}", fontSize = 12.sp, color = PrimaryPink)
                    if (note.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(note.text, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DiscussionsScreen(
    repository: BloomRepository,
    mode: String,
    selectedId: String?,
    onModeChange: (String, String?) -> Unit,
    onReport: (String, String?) -> Unit
) {
    when (mode) {
        "new" -> {
            var title by remember { mutableStateOf("") }
            var category by remember { mutableStateOf("Kesehatan") }
            var text by remember { mutableStateOf("") }

            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                IconButton(onClick = { onModeChange("list", null) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                }
                Text("Tanya / Buat Diskusi Baru", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Pertanyaan") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Kategori", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Kesehatan", "Tips", "Pengalaman").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Detail Pertanyaan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (title.isNotBlank() && text.isNotBlank()) {
                            repository.addDiscussion(title, category, text)
                            onModeChange("list", null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
                ) {
                    Text("Kirim Diskusi", fontWeight = FontWeight.Bold)
                }
            }
        }
        "detail" -> {
            val post = repository.discussions.find { it.id == selectedId }
            if (post == null) {
                onModeChange("list", null)
                return
            }
            var answerText by remember { mutableStateOf("") }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    IconButton(onClick = { onModeChange("list", null) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                    Text(post.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
                    Text("Oleh ${post.authorName} (${post.authorEmail}) • ${post.date}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(post.text, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { onReport("Diskusi: ${post.title}", post.id) }) {
                        Icon(Icons.Default.Flag, contentDescription = "Laporkan", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Laporkan Pertanyaan", fontSize = 12.sp)
                    }
                }

                item {
                    Text("Jawaban (${post.answers.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                items(post.answers) { ans ->
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${ans.authorName} (${ans.authorEmail})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(ans.text, fontSize = 13.sp)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = { answerText = it },
                        label = { Text("Tulis Jawaban Kamu") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (answerText.isNotBlank()) {
                                repository.addDiscussionAnswer(post.id, answerText)
                                answerText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
                    ) {
                        Text("Kirim Jawaban", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        else -> {
            var selectedCategory by remember { mutableStateOf("Semua") }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Ruang Diskusi Komunitas", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
                    Text("Berbagi dan bertanya seputar menstruasi dengan aman.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onModeChange("new", null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
                    ) {
                        Text("+ Buat Pertanyaan Baru", fontWeight = FontWeight.Bold)
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Semua", "Kesehatan", "Tips", "Pengalaman")) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                val filtered = if (selectedCategory == "Semua") repository.discussions else repository.discussions.filter { it.category == selectedCategory }

                items(filtered) { post ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onModeChange("detail", post.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(post.category, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
                                Text(post.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            }
                            Text(post.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(post.text, fontSize = 13.sp, maxLines = 2)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${post.answers.size} Jawaban", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentPink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArticlesScreen(
    repository: BloomRepository,
    selectedArticleId: Int?,
    onSelectArticle: (Int?) -> Unit,
    onReport: (String, String?) -> Unit
) {
    if (selectedArticleId != null) {
        val article = repository.articles.find { it.id == selectedArticleId } ?: return
        val comments = repository.articleComments[selectedArticleId] ?: emptyList()
        var commentText by remember { mutableStateOf("") }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                IconButton(onClick = { onSelectArticle(null) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                }
                Text(article.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
                Spacer(modifier = Modifier.height(8.dp))
                article.body.forEach { paragraph ->
                    Text(paragraph, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Komentar (${comments.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            items(comments) { comment ->
                val isAdmin = comment.role == "admin" || comment.email.lowercase().contains("admin")
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isAdmin) SoftPink else MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().border(if (isAdmin) 1.dp else 0.dp, if (isAdmin) PrimaryPink else Color.Transparent, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(comment.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (isAdmin) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = AccentPink,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("ADMIN", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { onReport("Komentar oleh ${comment.name}", comment.id) }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Flag, contentDescription = "Laporkan", tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            }
                        }
                        Text(comment.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(comment.text, fontSize = 13.sp)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Tulis Komentar") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            repository.addComment(selectedArticleId, commentText)
                            commentText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
                ) {
                    Text("Kirim Komentar", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Artikel & Panduan", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
                Text("Bacaan seputar menstruasi, kesehatan, dan pola hidup sehat.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
            }

            items(repository.articles) { article ->
                val commentCount = repository.articleComments[article.id]?.size ?: 0
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectArticle(article.id) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Surface(color = SoftPink, shape = RoundedCornerShape(8.dp)) {
                            Text(article.tag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentPink, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(article.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(article.body.firstOrNull() ?: "", fontSize = 13.sp, maxLines = 2)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("$commentCount Komentar • Baca selengkapnya →", fontSize = 12.sp, color = PrimaryPink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(repository: BloomRepository, onLogout: () -> Unit, onReportBug: () -> Unit) {
    val user = repository.currentUser ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Profil Saya", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)

        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(SoftPink),
                    contentAlignment = Alignment.Center
                ) {
                    Text(user.name.firstOrNull()?.toString()?.uppercase() ?: "U", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AccentPink)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(user.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${user.age} tahun • ${user.email}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    if (user.role == "admin") {
                        Surface(color = AccentPink, shape = RoundedCornerShape(6.dp)) {
                            Text("ADMINISTRATOR", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Mode Tampilan (Tema)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to "Sistem", "light" to "Terang", "dark" to "Gelap").forEach { (valKey, label) ->
                        FilterChip(
                            selected = repository.theme == valKey,
                            onClick = { repository.setThemePreference(valKey) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Button(
            onClick = onReportBug,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
        ) {
            Icon(Icons.Default.BugReport, contentDescription = "Report")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Laporkan Bug / Pelanggaran", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = {
                repository.logout()
                onLogout()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Keluar Akun", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AdminScreen(repository: BloomRepository) {
    var activeTab by remember { mutableStateOf("reports") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Panel Admin", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryPink)
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = activeTab == "reports", onClick = { activeTab = "reports" }, label = { Text("Laporan (${repository.reports.size})") })
            FilterChip(selected = activeTab == "discussions", onClick = { activeTab = "discussions" }, label = { Text("Diskusi") })
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeTab == "reports") {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(repository.reports) { r ->
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Target: ${r.targetTitle} [${r.category}]", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Pelapor: ${r.reporterEmail} • ${r.date}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(r.description, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(repository.discussions) { d ->
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(d.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Oleh ${d.authorName} (${d.authorEmail})", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportModalDialog(
    targetTitle: String,
    targetId: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var category by remember { mutableStateOf("Pelanggaran Pengguna") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Laporkan Masalah") },
        text = {
            Column {
                Text("Target: $targetTitle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Kategori Laporan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                listOf("Pelanggaran Pengguna", "Konten Tidak Layak", "Bug Aplikasi", "Spam").forEach { cat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { category = cat }
                    ) {
                        RadioButton(selected = category == cat, onClick = { category = cat })
                        Text(cat, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Detail Laporan") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (description.isNotBlank()) onSubmit(category, description) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
            ) {
                Text("Kirim Laporan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
