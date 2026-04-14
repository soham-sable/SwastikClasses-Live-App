package com.example.swastikclassespro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import org.json.JSONObject

// --- 1. DATA MODELS ---
data class Student(
    val id: String = "",
    val name: String = "",
    val personalCode: String = "",
    val parentNumber: String = "",
    val attendance: String = "100%",
    val lastMarks: String = "N/A",
    val streamType: String = "General",
    val targetExam: String = "2026 Batch"
)

// --- 2. MAIN ACTIVITY ---
class MainActivity : ComponentActivity(), PaymentResultListener {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var lastSelectedBook by mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("SwastikDebug", "MainActivity onCreate started")
        try {
            Checkout.preload(applicationContext)
            android.util.Log.d("SwastikDebug", "Razorpay preloaded")
        } catch (e: Exception) {
            android.util.Log.e("SwastikDebug", "Razorpay preload failed", e)
        }

        setContent {
            android.util.Log.d("SwastikDebug", "setContent starting")
            var screenState by rememberSaveable { mutableStateOf("login") }
            var loggedInStudent by remember { mutableStateOf<Student?>(null) }
            var isLoading by remember { mutableStateOf(false) }

            SwastikTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF4F7FA)) {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF1A237E))
                        }
                    } else {
                        when (screenState) {
                            "login" -> LoginPage { code ->
                                if (code.uppercase() == "ADMIN777") {
                                    screenState = "admin"
                                } else {
                                    isLoading = true
                                    db.collection("students").whereEqualTo("personalCode", code.uppercase()).get()
                                        .addOnSuccessListener { query ->
                                            if (!query.isEmpty) {
                                                loggedInStudent = query.documents[0].toObject(Student::class.java)
                                                screenState = "dashboard"
                                            } else {
                                                Toast.makeText(this, "Invalid Code", Toast.LENGTH_SHORT).show()
                                            }
                                            isLoading = false
                                        }.addOnFailureListener {
                                            isLoading = false
                                            Toast.makeText(this, "Error connecting to server", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                            "dashboard" -> if (loggedInStudent != null) {
                                StudentDashboard(
                                    db = db,
                                    student = loggedInStudent!!,
                                    onLogout = { screenState = "login"; loggedInStudent = null },
                                    onOrderClick = { book ->
                                        lastSelectedBook = book
                                        startPayment(book.second, book.first)
                                    }
                                )
                            }
                            "admin" -> AdminDashboard(
                                db = db,
                                onLogout = { screenState = "login" },
                                onNotify = { student, type -> sendWhatsAppNotification(student, type) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sendWhatsAppNotification(student: Student, type: String) {
        val cleanNumber = student.parentNumber.replace("[^0-9]".toRegex(), "")
        val msg = if (type == "HOMEWORK") {
            "Swastik Classes: Ward ${student.name} homework pending."
        } else {
            "Swastik Classes: Ward ${student.name} is ABSENT."
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(msg)}"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPayment(amountString: String, bookName: String) {
        val checkout = Checkout()
        checkout.setKeyID("rzp_test_ScG1CDR92mOcoN")
        try {
            val options = JSONObject().apply {
                put("name", "Swastik Classes")
                put("description", bookName)
                put("currency", "INR")
                put("amount", amountString.replace("[^0-9]".toRegex(), "").toInt() * 100)
                put("theme.color", "#1A237E")
            }
            checkout.open(this, options)
        } catch (e: Exception) {
            Toast.makeText(this, "Payment error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPaymentSuccess(id: String?) {
        Toast.makeText(this, "Order Confirmed!", Toast.LENGTH_LONG).show()
    }

    override fun onPaymentError(code: Int, desc: String?) {
        Toast.makeText(this, "Payment Failed", Toast.LENGTH_SHORT).show()
    }
}

// --- 3. STUDENT DASHBOARD ---
@Composable
fun StudentDashboard(db: FirebaseFirestore, student: Student, onLogout: () -> Unit, onOrderClick: (Pair<String, String>) -> Unit) {
    var tab by remember { mutableStateOf("home") }
    var notice by remember { mutableStateOf("No active notices.") }
    val navy = Color(0xFF1A237E)

    LaunchedEffect(student.personalCode) {
        val parts = student.personalCode.split("-")
        if (parts.size >= 2) {
            db.collection("broadcasts").document("${parts[0]}-${parts[1]}").addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) notice = doc.getString("message") ?: "No notices."
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(listOf(navy, Color(0xFF3F51B5))),
                    RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                )
                .padding(24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                Column {
                    Text("NAMASTE,", color = Color.White.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(student.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
                TextButton(onClick = onLogout) {
                    Text("Logout", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = navy.copy(0.05f)),
            border = BorderStroke(1.dp, navy.copy(0.2f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, null, tint = navy)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("NOTICE BOARD", fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = navy)
                    Text(notice, fontSize = 14.sp)
                }
            }
        }

        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(50.dp)
                .background(Color.White, RoundedCornerShape(25.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("home" to "Home", "store" to "Store").forEach { (id, label) ->
                val isSelected = tab == id
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .background(if (isSelected) navy else Color.Transparent, RoundedCornerShape(20.dp))
                        .clickable { tab = id },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color.Gray)
                }
            }
        }

        LazyColumn(Modifier.padding(16.dp)) {
            if (tab == "home") {
                item {
                    CardItem("ID", student.personalCode, Icons.Default.Info, Color(0xFF448AFF))
                    CardItem("Stream", student.streamType, Icons.Default.Edit, Color(0xFF4CAF50))
                    CardItem("Attendance", student.attendance, Icons.Default.CheckCircle, Color(0xFFFFA000))
                    CardItem("Target", student.targetExam, Icons.Default.Star, Color(0xFFE91E63))
                }
            } else {
                val mtgBooks = listOf(
                    Triple("Swastik Special MTG Mathematics", "1100", "https://m.media-amazon.com/images/I/51s85+GKM5L.jpg"),
                    Triple("Swastik Special MTG Biology", "950", "https://assets.booksbybsf.com/products/MTGObjectiveNCERTatyourFINGERTPISforNEETAIIMSBiologyBestNEETBooksBasedonNCERTPatternLatestRevisedEdition2022.webp"),
                    Triple("Swastik Special MTG Physics", "1050", "https://m.media-amazon.com/images/I/71rCfUpd27L.jpg"),
                    Triple("Swastik Special MTG Chemistry", "1000", "https://rukminim2.flixcart.com/image/416/416/jvtujrk0/book/7/6/5/mtg-objective-ncert-at-your-fingertips-chemistry-2019-20-original-imafgnbuk5n8zcmp.jpeg"),
                    Triple("Swastik Special MTG PCMB Combo", "2650", "https://m.media-amazon.com/images/I/51s85+GKM5L.jpg")
                )
                items(mtgBooks) { (title, price, url) ->
                    StoreItem(title, "₹$price", url) { onOrderClick(title to price) }
                }
            }
        }
    }
}

// --- 4. ADMIN DASHBOARD ---
@Composable
fun AdminDashboard(db: FirebaseFirestore, onLogout: () -> Unit, onNotify: (Student, String) -> Unit) {
    var tab by remember { mutableStateOf("database") }
    var students by remember { mutableStateOf(listOf<Student>()) }
    var showAdmission by remember { mutableStateOf(false) }
    var selectedBroadClass by remember { mutableStateOf("F0-26") }
    var broadcastMsg by remember { mutableStateOf("") }
    val navy = Color(0xFF1A237E)

    LaunchedEffect(Unit) {
        db.collection("students").addSnapshotListener { s, _ ->
            if (s != null) students = s.toObjects(Student::class.java)
        }
    }

    if (showAdmission) {
        AdmissionDialog(
            onDismiss = { showAdmission = false },
            onConfirm = { student ->
                db.collection("students").add(student)
                showAdmission = false
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Admin Portal", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = navy)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showAdmission = true }) {
                    Icon(Icons.Default.Add, null, tint = navy)
                }
                TextButton(onClick = onLogout) {
                    Text("Logout", color = Color.Red)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            listOf("database" to "Students", "broadcast" to "Notice Board").forEach { (id, label) ->
                val isSelected = tab == id
                Button(
                    onClick = { tab = id },
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) navy else Color.LightGray.copy(0.3f)
                    )
                ) {
                    Text(label, color = if (isSelected) Color.White else navy, fontSize = 12.sp)
                }
            }
        }

        if (tab == "database") {
            LazyColumn {
                items(students) { s ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(s.name.uppercase(), fontWeight = FontWeight.Bold)
                            Text(s.personalCode, fontSize = 11.sp, color = Color.Gray)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = { onNotify(s, "HOMEWORK") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D))
                                ) {
                                    Text("H", color = Color.Black)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { onNotify(s, "ABSENT") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                ) {
                                    Text("A")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text("Broadcast to Class:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    val classes = listOf(
                        "F8-26" to "8th", "F9-26" to "9th", "F0-26" to "10th",
                        "SM11-26" to "11-PCM", "SB11-26" to "11-PCB", "C11-26" to "11-Com",
                        "SM12-26" to "12-PCM", "SB12-26" to "12-PCB", "C12-26" to "12-Com"
                    )
                    classes.forEach { (id, label) ->
                        AdminPill(label, selectedBroadClass == id) { selectedBroadClass = id }
                    }
                }
                OutlinedTextField(
                    value = broadcastMsg,
                    onValueChange = { broadcastMsg = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        db.collection("broadcasts").document(selectedBroadClass).set(mapOf("message" to broadcastMsg))
                        broadcastMsg = ""
                        // No easy way to get context here without passing it, using a simpler approach or just removing toast for now
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = navy)
                ) {
                    Text("Update Notice")
                }
            }
        }
    }
}

// --- 5. UI COMPONENTS & HELPERS ---
@Composable
fun StoreItem(n: String, p: String, url: String, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = url,
                contentDescription = n,
                modifier = Modifier.size(60.dp).padding(4.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
                Text(p, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))
            ) {
                Text("Order", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun AdminPill(l: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF1A237E) else Color.White,
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        Text(
            text = l,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = if (isSelected) Color.White else Color.Black,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
fun CardItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accentColor: Color) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(accentColor.copy(0.1f), CircleShape),
                Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LoginPage(onLogin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Swastik Classes", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
        Spacer(Modifier.height(40.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Code") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Button(
            onClick = { onLogin(code) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))
        ) {
            Text("Login")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdmissionDialog(onDismiss: () -> Unit, onConfirm: (Student) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedStd by remember { mutableStateOf("10th") }
    var selectedCourse by remember { mutableStateOf("PCM") }

    val code = remember(selectedStd, selectedCourse) {
        val prefix = when (selectedStd) {
            "8th" -> "F8"
            "9th" -> "F9"
            "10th" -> "F0"
            "11th" -> if (selectedCourse == "PCM") "SM11" else if (selectedCourse == "PCB") "SB11" else "C11"
            "12th" -> if (selectedCourse == "PCM") "SM12" else if (selectedCourse == "PCB") "SB12" else "C12"
            else -> "S"
        }
        "$prefix-26-${(100..999).random()}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Student") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                Spacer(Modifier.height(8.dp))
                Text("Select Class:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("8th", "9th", "10th", "11th", "12th").forEach {
                        AdminPill(it, selectedStd == it) { selectedStd = it }
                    }
                }
                if (selectedStd == "11th" || selectedStd == "12th") {
                    Spacer(Modifier.height(8.dp))
                    Text("Select Stream:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("PCM", "PCB", "Commerce").forEach {
                            AdminPill(it, selectedCourse == it) { selectedCourse = it }
                        }
                    }
                }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))
                ) {
                    Text("Generated ID: $code", modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Parent Phone No") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && phone.isNotBlank()) {
                    onConfirm(Student(name = name, personalCode = code, parentNumber = phone, streamType = selectedCourse))
                }
            }) {
                Text("Register")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SwastikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1A237E),
            secondary = Color(0xFF3F51B5),
            tertiary = Color(0xFF0D47A1)
        ),
        content = content
    )
}
