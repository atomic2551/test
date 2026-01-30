package com.quizapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.util.Base64

@Serializable
data class User(
    val id: Int = 0,
    val username: String,
    val email: String = "",
    val password: String = "",
    val role: String = "user"
)

@Serializable
data class Question(
    val id: Int = 0,
    val text: String,
    val options: List<String>,
    val correctAnswer: Int,
    val category: String,
    val difficulty: String = "medium",
    val explanation: String = ""
)

@Serializable
data class Quiz(
    val id: Int = 0,
    val title: String,
    val description: String,
    val questions: List<Question>,
    val timeLimit: Int = 0,
    val category: String,
    val createdBy: Int = 0
)

@Serializable
data class QuizResult(
    val id: Int = 0,
    val userId: Int,
    val quizId: Int,
    val score: Int,
    val totalQuestions: Int,
    val percentage: Double,
    val timeSpent: Int,
    val dateTime: String,
    val wrongAnswers: List<WrongAnswer>
)

@Serializable
data class WrongAnswer(
    val questionId: Int,
    val userAnswer: Int,
    val correctAnswer: Int
)

@Serializable
data class FileUploadRequest(
    val fileName: String,
    val content: String
)

@Serializable
data class AuthRequest(
    val username: String,
    val password: String
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val errors: List<String> = emptyList()
)

@Serializable
data class UiQuestion(
    val id: Int = 0,
    val text: String,
    val options: List<String>,
    var selectedAnswer: Int? = null,
    val correctAnswer: Int,
    val category: String,
    val explanation: String = ""
)

@Serializable
data class UiQuiz(
    val id: Int = 0,
    val title: String,
    val description: String,
    val questions: List<UiQuestion>,
    val timeLimit: Int = 0,
    val category: String,
    var currentQuestionIndex: Int = 0,
    var startTime: Long = 0,
    var endTime: Long = 0
)

@Serializable
data class UiResult(
    val quizId: Int,
    val score: Int,
    val totalQuestions: Int,
    val percentage: Double,
    val timeSpent: Int,
    val wrongAnswers: List<UiWrongAnswer>
)

@Serializable
data class UiWrongAnswer(
    val questionId: Int,
    val questionText: String,
    val userAnswer: String,
    val correctAnswer: String,
    val explanation: String
)

class ApiClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                }
            )
        }
    }

    private val baseUrl = "http://localhost:8080"

    suspend fun login(username: String, password: String): ApiResponse<User> {
        return client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(username, password))
        }.body()
    }

    suspend fun uploadQuestions(file: File): ApiResponse<List<Question>> {
        val content = Base64.getEncoder().encodeToString(file.readBytes())
        return client.post("$baseUrl/api/questions/upload") {
            contentType(ContentType.Application.Json)
            setBody(FileUploadRequest(file.name, content))
        }.body()
    }

    suspend fun getQuizzes(): ApiResponse<List<Quiz>> {
        return client.get("$baseUrl/api/quizzes").body()
    }

    suspend fun getQuiz(id: Int): ApiResponse<Quiz> {
        return client.get("$baseUrl/api/quizzes/$id").body()
    }

    suspend fun submitResult(result: QuizResult): ApiResponse<QuizResult> {
        return client.post("$baseUrl/api/results") {
            contentType(ContentType.Application.Json)
            setBody(result)
        }.body()
    }

    suspend fun getUserResults(userId: Int): ApiResponse<List<QuizResult>> {
        return client.get("$baseUrl/api/users/$userId/results").body()
    }

    suspend fun getStats(): ApiResponse<Stats> {
        return client.get("$baseUrl/api/stats/overview").body()
    }
}

@Composable
fun LoginScreen(apiClient: ApiClient, onLogin: (User) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Quiz Application", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )

        if (error != null) {
            Text(
                error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                isLoading = true
                error = null
                coroutineScope.launch {
                    val response = apiClient.login(username.trim(), password)
                    if (response.success && response.data != null) {
                        onLogin(response.data)
                    } else {
                        error = response.message.ifBlank { "Login failed" }
                    }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = username.isNotBlank() && password.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(16.dp))
            } else {
                Text("Login")
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { /* TODO: Register */ }) {
            Text("Don't have an account? Register")
        }
    }
}

@Composable
fun MainScreen(user: User, apiClient: ApiClient) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz App - Welcome, ${user.username}") },
                actions = {
                    Button(onClick = { currentScreen = Screen.Profile }) {
                        Text("Profile")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                NavigationBar(modifier = Modifier.fillMaxWidth()) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.Dashboard,
                        onClick = { currentScreen = Screen.Dashboard },
                        icon = { Text("🏠") },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Quizzes,
                        onClick = { currentScreen = Screen.Quizzes },
                        icon = { Text("📝") },
                        label = { Text("Quizzes") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Upload,
                        onClick = { currentScreen = Screen.Upload },
                        icon = { Text("📁") },
                        label = { Text("Upload") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Results,
                        onClick = { currentScreen = Screen.Results },
                        icon = { Text("📊") },
                        label = { Text("Results") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(user, apiClient)
                Screen.Quizzes -> QuizListScreen(apiClient) { quizId ->
                    currentScreen = Screen.QuizTaking(quizId)
                }
                Screen.Upload -> FileUploadScreen(apiClient)
                Screen.Results -> ResultsScreen(user.id, apiClient)
                is Screen.QuizTaking -> QuizTakingScreen(
                    (currentScreen as Screen.QuizTaking).quizId,
                    user.id,
                    apiClient,
                    onBack = { currentScreen = Screen.Quizzes }
                )
                Screen.Profile -> ProfileScreen(user)
            }
        }
    }
}

@Composable
fun DashboardScreen(user: User, apiClient: ApiClient) {
    var stats by remember { mutableStateOf<Stats?>(null) }
    var recentQuizzes by remember { mutableStateOf<List<Quiz>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val quizzesResponse = apiClient.getQuizzes()
            if (quizzesResponse.success) {
                recentQuizzes = quizzesResponse.data ?: emptyList()
            }
            val statsResponse = apiClient.getStats()
            if (statsResponse.success) {
                stats = statsResponse.data
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Welcome back, ${user.username}!", style = MaterialTheme.typography.headlineSmall)
                    Text("Role: ${user.role}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (stats != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Overview", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Total quizzes: ${stats!!.totalQuizzes}")
                        Text("Average score: ${String.format(\"%.1f\", stats!!.averageScore)}%")
                        Text("Best category: ${stats!!.bestCategory}")
                        Text("Total time spent: ${stats!!.totalTimeSpent}s")
                    }
                }
            }
        }

        item {
            Text("Recent Quizzes", style = MaterialTheme.typography.titleLarge)
        }

        items(recentQuizzes) { quiz ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { /* Navigate to quiz */ }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(quiz.title, style = MaterialTheme.typography.titleMedium)
                    Text(quiz.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Questions: ${quiz.questions.size} • Category: ${quiz.category}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun QuizTakingScreen(quizId: Int, userId: Int, apiClient: ApiClient, onBack: () -> Unit) {
    var quiz by remember { mutableStateOf<UiQuiz?>(null) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun submitQuiz() {
        coroutineScope.launch {
            isSubmitting = true
            quiz?.let { uiQuiz ->
                val endTime = System.currentTimeMillis()
                val timeSpent = ((endTime - uiQuiz.startTime) / 1000).toInt()

                val correctAnswers = uiQuiz.questions.count { question ->
                    question.selectedAnswer == question.correctAnswer
                }

                val totalQuestions = uiQuiz.questions.size
                val percentage = (correctAnswers.toDouble() / totalQuestions) * 100

                val wrongAnswers = uiQuiz.questions.filter { question ->
                    question.selectedAnswer != question.correctAnswer
                }.map { question ->
                    WrongAnswer(
                        questionId = question.id,
                        userAnswer = question.selectedAnswer ?: -1,
                        correctAnswer = question.correctAnswer
                    )
                }

                val result = QuizResult(
                    userId = userId,
                    quizId = uiQuiz.id,
                    score = correctAnswers,
                    totalQuestions = totalQuestions,
                    percentage = percentage,
                    timeSpent = timeSpent,
                    dateTime = LocalDateTime.now().toString(),
                    wrongAnswers = wrongAnswers
                )

                val response = apiClient.submitResult(result)
                if (response.success) {
                    onBack()
                }
            }
            isSubmitting = false
        }
    }

    LaunchedEffect(quizId) {
        coroutineScope.launch {
            val response = apiClient.getQuiz(quizId)
            if (response.success) {
                val q = response.data
                if (q != null) {
                    quiz = UiQuiz(
                        id = q.id,
                        title = q.title,
                        description = q.description,
                        questions = q.questions.map { question ->
                            UiQuestion(
                                id = question.id,
                                text = question.text,
                                options = question.options,
                                correctAnswer = question.correctAnswer,
                                category = question.category,
                                explanation = question.explanation
                            )
                        },
                        timeLimit = q.timeLimit,
                        category = q.category,
                        startTime = System.currentTimeMillis()
                    )

                    if (quiz?.timeLimit ?: 0 > 0) {
                        timeRemaining = quiz!!.timeLimit * 60
                    }
                }
            }
        }
    }

    LaunchedEffect(quiz, timeRemaining) {
        if (timeRemaining > 0) {
            while (timeRemaining > 0) {
                delay(1000)
                timeRemaining--
            }
            submitQuiz()
        }
    }

    quiz?.let { currentQuiz ->
        if (currentQuestionIndex >= currentQuiz.questions.size) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Quiz Completed!", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { submitQuiz() }, enabled = !isSubmitting) {
                    Text("Submit Quiz")
                }
            }
        } else {
            val question = currentQuiz.questions[currentQuestionIndex]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Question ${currentQuestionIndex + 1}/${currentQuiz.questions.size}")
                    if (timeRemaining > 0) {
                        Text("Time: ${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}")
                    }
                    Text(
                        "Score: ${
                            currentQuiz.questions.take(currentQuestionIndex).count { q ->
                                q.selectedAnswer == q.correctAnswer
                            }
                        }"
                    )
                }

                Spacer(Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(question.text, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Category: ${question.category}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    question.options.forEachIndexed { index, option ->
                        val isSelected = question.selectedAnswer == index
                        val isCorrect = index == question.correctAnswer

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSelected && isCorrect -> MaterialTheme.colorScheme.primaryContainer
                                    isSelected -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            ),
                            onClick = {
                                question.selectedAnswer = index
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { question.selectedAnswer = index }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(option)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            if (currentQuestionIndex > 0) currentQuestionIndex--
                        },
                        enabled = currentQuestionIndex > 0
                    ) {
                        Text("Previous")
                    }

                    Button(
                        onClick = {
                            if (currentQuestionIndex < currentQuiz.questions.size - 1) {
                                currentQuestionIndex++
                            }
                        },
                        enabled = currentQuestionIndex < currentQuiz.questions.size - 1
                    ) {
                        Text(if (currentQuestionIndex == currentQuiz.questions.size - 1) "Finish" else "Next")
                    }
                }
            }
        }
    } ?: run {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun FileUploadScreen(apiClient: ApiClient) {
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadResult by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Upload Questions File", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("File Format:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    """
                    #Savol matni
                    +to'g'ri javob
                    -noto'g'ri javob 1
                    -noto'g'ri javob 2
                    -noto'g'ri javob 3
                    Kategoriya: [Kategoriya nomi]
                    Izoh: [Izoh matni] # ixtiyoriy
                    #
                """.trimIndent()
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(onClick = {
            selectedFile = File("questions.txt")
        }) {
            Text("Choose File")
        }

        if (selectedFile != null) {
            Spacer(Modifier.height(16.dp))
            Text("Selected: ${selectedFile!!.name}")
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                selectedFile?.let { file ->
                    isUploading = true
                    coroutineScope.launch {
                        val response = apiClient.uploadQuestions(file)
                        uploadResult = if (response.success) {
                            "✅ ${response.data?.size ?: 0} questions uploaded successfully!"
                        } else {
                            "❌ Upload failed: ${response.message}"
                        }
                        isUploading = false
                    }
                }
            },
            enabled = selectedFile != null && !isUploading
        ) {
            if (isUploading) {
                CircularProgressIndicator(Modifier.size(16.dp))
            } else {
                Text("Upload")
            }
        }

        if (uploadResult != null) {
            Spacer(Modifier.height(16.dp))
            Text(uploadResult.orEmpty())
        }
    }
}

@Composable
fun ResultsScreen(userId: Int, apiClient: ApiClient) {
    var results by remember { mutableStateOf<List<QuizResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val response = apiClient.getUserResults(userId)
            if (response.success) {
                results = response.data ?: emptyList()
            }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Your Results", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
            }

            if (results.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No results yet. Take a quiz!")
                        }
                    }
                }
            } else {
                items(results) { result ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Quiz #${result.quizId}", style = MaterialTheme.typography.titleMedium)
                                Text(result.dateTime.substringBefore("T"))
                            }

                            Spacer(Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = result.percentage.toFloat() / 100,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Score: ${result.score}/${result.totalQuestions}")
                                Text("${String.format("%.1f", result.percentage)}%")
                                Text("Time: ${result.timeSpent}s")
                            }

                            if (result.wrongAnswers.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Text("Wrong answers: ${result.wrongAnswers.size}")

                                var expanded by remember { mutableStateOf(false) }

                                Button(
                                    onClick = { expanded = !expanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (expanded) "Hide Details" else "Show Details")
                                }

                                if (expanded) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        result.wrongAnswers.forEachIndexed { index, wrong ->
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text("Question ${index + 1}")
                                                    Text("Your answer: ${wrong.userAnswer}")
                                                    Text("Correct answer: ${wrong.correctAnswer}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuizListScreen(apiClient: ApiClient, onQuizSelected: (Int) -> Unit) {
    var quizzes by remember { mutableStateOf<List<Quiz>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val response = apiClient.getQuizzes()
            if (response.success) {
                quizzes = response.data ?: emptyList()
            }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Available Quizzes", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
            }

            if (quizzes.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No quizzes available. Upload some questions!")
                        }
                    }
                }
            } else {
                items(quizzes) { quiz ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onQuizSelected(quiz.id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(quiz.title, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(quiz.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Questions: ${quiz.questions.size}")
                                Text("Category: ${quiz.category}")
                                if (quiz.timeLimit > 0) {
                                    Text("Time: ${quiz.timeLimit} min")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(user: User) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        user.username.firstOrNull()?.uppercase() ?: "U",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(user.username, style = MaterialTheme.typography.headlineMedium)
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
                Text("Role: ${user.role}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(onClick = { /* Logout */ }) {
            Text("Logout")
        }
    }
}

sealed class Screen {
    data object Dashboard : Screen()
    data object Quizzes : Screen()
    data object Upload : Screen()
    data object Results : Screen()
    data object Profile : Screen()
    data class QuizTaking(val quizId: Int) : Screen()
}

@Serializable
data class Stats(
    val totalQuizzes: Int,
    val averageScore: Double,
    val bestCategory: String,
    val totalTimeSpent: Int
)

fun main() = application {
    var currentUser by remember { mutableStateOf<User?>(null) }
    val apiClient = remember { ApiClient() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Quiz Application"
    ) {
        MaterialTheme {
            if (currentUser == null) {
                LoginScreen(apiClient) { user ->
                    currentUser = user
                }
            } else {
                MainScreen(currentUser!!, apiClient)
            }
        }
    }
}
