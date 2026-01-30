package com.quizapp.backend

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.double
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.integer
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.text
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.varchar
import java.time.LocalDateTime
import java.util.Base64

@Serializable
data class User(
    val id: Int = 0,
    val username: String,
    val email: String,
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
    val createdBy: Int
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
data class StatsOverview(
    val totalQuizzes: Int,
    val averageScore: Double,
    val bestCategory: String,
    val totalTimeSpent: Int
)

object DatabaseFactory {
    fun init() {
        val database = Database.connect("jdbc:sqlite:quiz.db", driver = "org.sqlite.JDBC")

        transaction(database) {
            SchemaUtils.create(
                Users,
                Questions,
                Quizzes,
                QuizQuestions,
                QuizResults,
                WrongAnswers
            )
        }
    }
}

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 100).uniqueIndex()
    val password = varchar("password", 100)
    val role = varchar("role", 20).default("user")
    val createdAt = varchar("created_at", 50)

    override val primaryKey = PrimaryKey(id)
}

object Questions : Table("questions") {
    val id = integer("id").autoIncrement()
    val text = text("text")
    val options = text("options")
    val correctAnswer = integer("correct_answer")
    val category = varchar("category", 50)
    val difficulty = varchar("difficulty", 20)
    val explanation = text("explanation").nullable()
    val createdAt = varchar("created_at", 50)

    override val primaryKey = PrimaryKey(id)
}

object Quizzes : Table("quizzes") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 100)
    val description = text("description")
    val timeLimit = integer("time_limit").default(0)
    val category = varchar("category", 50)
    val createdBy = integer("created_by").references(Users.id)
    val createdAt = varchar("created_at", 50)

    override val primaryKey = PrimaryKey(id)
}

object QuizQuestions : Table("quiz_questions") {
    val quizId = integer("quiz_id").references(Quizzes.id)
    val questionId = integer("question_id").references(Questions.id)
    val orderIndex = integer("order_index")
}

object QuizResults : Table("quiz_results") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val quizId = integer("quiz_id").references(Quizzes.id)
    val score = integer("score")
    val totalQuestions = integer("total_questions")
    val percentage = double("percentage")
    val timeSpent = integer("time_spent")
    val dateTime = varchar("date_time", 50)

    override val primaryKey = PrimaryKey(id)
}

object WrongAnswers : Table("wrong_answers") {
    val resultId = integer("result_id").references(QuizResults.id)
    val questionId = integer("question_id").references(Questions.id)
    val userAnswer = integer("user_answer")
    val correctAnswer = integer("correct_answer")
}

class QuizService {
    suspend fun registerUser(user: User): ApiResponse<User> {
        return transaction {
            try {
                val existingUser = Users.select { Users.username eq user.username }.firstOrNull()
                if (existingUser != null) {
                    return@transaction ApiResponse(false, "Username already exists")
                }

                val id = Users.insertAndGetId {
                    it[username] = user.username
                    it[email] = user.email
                    it[password] = hashPassword(user.password)
                    it[role] = user.role
                    it[createdAt] = LocalDateTime.now().toString()
                }

                ApiResponse(true, "User registered successfully", user.copy(id = id.value))
            } catch (e: Exception) {
                ApiResponse(false, "Registration failed: ${e.message}")
            }
        }
    }

    suspend fun authenticate(username: String, password: String): ApiResponse<User> {
        return transaction {
            val user = Users.select { Users.username eq username }.firstOrNull()
                ?: return@transaction ApiResponse(false, "User not found")

            if (!verifyPassword(password, user[Users.password])) {
                return@transaction ApiResponse(false, "Invalid password")
            }

            val userData = User(
                id = user[Users.id].value,
                username = user[Users.username],
                email = user[Users.email],
                role = user[Users.role]
            )

            ApiResponse(true, "Authentication successful", userData)
        }
    }

    suspend fun uploadQuestionsFromFile(content: String, category: String): ApiResponse<List<Question>> {
        val questions = mutableListOf<Question>()

        try {
            val lines = content.lines()
            var currentQuestion: QuestionBuilder? = null

            for (line in lines) {
                when {
                    line.startsWith("#") && line.length > 1 -> {
                        currentQuestion?.let { builder ->
                            if (builder.isValid()) {
                                questions.add(builder.build())
                            }
                        }
                        currentQuestion = QuestionBuilder(line.substring(1).trim())
                    }
                    line.startsWith("+") -> {
                        currentQuestion?.addOption(line.substring(1).trim(), true)
                    }
                    line.startsWith("-") -> {
                        currentQuestion?.addOption(line.substring(1).trim(), false)
                    }
                    line.startsWith("Kategoriya:") -> {
                        currentQuestion?.setCategory(line.substringAfter("Kategoriya:").trim())
                    }
                    line.startsWith("Izoh:") -> {
                        currentQuestion?.setExplanation(line.substring(5).trim())
                    }
                    line == "#" -> {
                        currentQuestion?.let { builder ->
                            if (builder.isValid()) {
                                questions.add(builder.build())
                            }
                        }
                        currentQuestion = null
                    }
                }
            }

            val savedQuestions = mutableListOf<Question>()
            transaction {
                questions.forEach { question ->
                    val id = Questions.insertAndGetId {
                        it[text] = question.text
                        it[options] = Json.encodeToString(
                            ListSerializer(String.serializer()),
                            question.options
                        )
                        it[correctAnswer] = question.correctAnswer
                        it[category] = question.category
                        it[difficulty] = question.difficulty
                        it[explanation] = question.explanation
                        it[createdAt] = LocalDateTime.now().toString()
                    }

                    savedQuestions.add(question.copy(id = id.value))
                }
            }

            return ApiResponse(true, "${questions.size} questions uploaded", savedQuestions)
        } catch (e: Exception) {
            return ApiResponse(false, "File parsing failed: ${e.message}")
        }
    }

    private class QuestionBuilder(val text: String) {
        private val options = mutableListOf<String>()
        private var correctIndex = -1
        private var explanation = ""
        private var difficulty = "medium"
        private var category = ""

        fun addOption(text: String, isCorrect: Boolean) {
            options.add(text)
            if (isCorrect) {
                correctIndex = options.size - 1
            }
        }

        fun setExplanation(exp: String) {
            explanation = exp
        }

        fun setDifficulty(diff: String) {
            difficulty = diff
        }

        fun setCategory(value: String) {
            category = value
        }

        fun isValid(): Boolean {
            return text.isNotEmpty() && options.size >= 2 && correctIndex != -1
        }

        fun build(): Question {
            return Question(
                text = text,
                options = options,
                correctAnswer = correctIndex,
                category = category,
                difficulty = difficulty,
                explanation = explanation
            )
        }
    }

    suspend fun createQuiz(quiz: Quiz): ApiResponse<Quiz> {
        return transaction {
            try {
                val quizId = Quizzes.insertAndGetId {
                    it[title] = quiz.title
                    it[description] = quiz.description
                    it[timeLimit] = quiz.timeLimit
                    it[category] = quiz.category
                    it[createdBy] = quiz.createdBy
                    it[createdAt] = LocalDateTime.now().toString()
                }

                quiz.questions.forEachIndexed { index, question ->
                    QuizQuestions.insert {
                        it[QuizQuestions.quizId] = quizId.value
                        it[QuizQuestions.questionId] = question.id
                        it[QuizQuestions.orderIndex] = index
                    }
                }

                ApiResponse(true, "Quiz created successfully", quiz.copy(id = quizId.value))
            } catch (e: Exception) {
                ApiResponse(false, "Quiz creation failed: ${e.message}")
            }
        }
    }

    suspend fun getQuiz(id: Int): ApiResponse<Quiz> {
        return transaction {
            val quiz = Quizzes.select { Quizzes.id eq id }.firstOrNull()
                ?: return@transaction ApiResponse(false, "Quiz not found")

            val questions = getQuestionsForQuiz(id)

            val quizData = Quiz(
                id = quiz[Quizzes.id].value,
                title = quiz[Quizzes.title],
                description = quiz[Quizzes.description],
                questions = questions,
                timeLimit = quiz[Quizzes.timeLimit],
                category = quiz[Quizzes.category],
                createdBy = quiz[Quizzes.createdBy]
            )

            ApiResponse(true, "Quiz retrieved", quizData)
        }
    }

    suspend fun submitQuizResult(result: QuizResult): ApiResponse<QuizResult> {
        return transaction {
            try {
                val resultId = QuizResults.insertAndGetId {
                    it[QuizResults.userId] = result.userId
                    it[QuizResults.quizId] = result.quizId
                    it[QuizResults.score] = result.score
                    it[QuizResults.totalQuestions] = result.totalQuestions
                    it[QuizResults.percentage] = result.percentage
                    it[QuizResults.timeSpent] = result.timeSpent
                    it[QuizResults.dateTime] = result.dateTime
                }

                result.wrongAnswers.forEach { wrong ->
                    WrongAnswers.insert {
                        it[WrongAnswers.resultId] = resultId.value
                        it[WrongAnswers.questionId] = wrong.questionId
                        it[WrongAnswers.userAnswer] = wrong.userAnswer
                        it[WrongAnswers.correctAnswer] = wrong.correctAnswer
                    }
                }

                ApiResponse(true, "Result saved", result.copy(id = resultId.value))
            } catch (e: Exception) {
                ApiResponse(false, "Failed to save result: ${e.message}")
            }
        }
    }

    suspend fun getUserResults(userId: Int): ApiResponse<List<QuizResult>> {
        return transaction {
            val results = QuizResults
                .select { QuizResults.userId eq userId }
                .map { row ->
                    val wrongAnswers = WrongAnswers
                        .select { WrongAnswers.resultId eq row[QuizResults.id].value }
                        .map {
                            WrongAnswer(
                                questionId = it[WrongAnswers.questionId],
                                userAnswer = it[WrongAnswers.userAnswer],
                                correctAnswer = it[WrongAnswers.correctAnswer]
                            )
                        }

                    QuizResult(
                        id = row[QuizResults.id].value,
                        userId = row[QuizResults.userId],
                        quizId = row[QuizResults.quizId],
                        score = row[QuizResults.score],
                        totalQuestions = row[QuizResults.totalQuestions],
                        percentage = row[QuizResults.percentage],
                        timeSpent = row[QuizResults.timeSpent],
                        dateTime = row[QuizResults.dateTime],
                        wrongAnswers = wrongAnswers
                    )
                }

            ApiResponse(true, "Results retrieved", results)
        }
    }

    suspend fun getAllQuizzes(): ApiResponse<List<Quiz>> {
        return transaction {
            val quizzes = Quizzes.selectAll().map { row ->
                val questions = getQuestionsForQuiz(row[Quizzes.id].value)

                Quiz(
                    id = row[Quizzes.id].value,
                    title = row[Quizzes.title],
                    description = row[Quizzes.description],
                    questions = questions,
                    timeLimit = row[Quizzes.timeLimit],
                    category = row[Quizzes.category],
                    createdBy = row[Quizzes.createdBy]
                )
            }

            ApiResponse(true, "Quizzes retrieved", quizzes)
        }
    }

    suspend fun getStatsOverview(): ApiResponse<StatsOverview> {
        return transaction {
            val totalQuizzes = Quizzes.selectAll().count().toInt()
            val results = QuizResults.selectAll().toList()
            val averageScore = if (results.isNotEmpty()) {
                results.map { it[QuizResults.percentage] }.average()
            } else {
                0.0
            }
            val totalTimeSpent = results.sumOf { it[QuizResults.timeSpent] }
            val quizCategories = Quizzes.selectAll().associate { row ->
                row[Quizzes.id].value to row[Quizzes.category]
            }
            val categoryCounts = mutableMapOf<String, Int>()
            results.forEach { row ->
                val category = quizCategories[row[QuizResults.quizId]] ?: "Unknown"
                categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
            }
            val bestCategory = categoryCounts.maxByOrNull { it.value }?.key ?: "N/A"

            ApiResponse(
                true,
                "Stats retrieved",
                StatsOverview(
                    totalQuizzes = totalQuizzes,
                    averageScore = averageScore,
                    bestCategory = bestCategory,
                    totalTimeSpent = totalTimeSpent
                )
            )
        }
    }

    private fun getQuestionsForQuiz(quizId: Int): List<Question> {
        return Questions
            .innerJoin(QuizQuestions)
            .select { QuizQuestions.quizId eq quizId }
            .orderBy(QuizQuestions.orderIndex)
            .map { row ->
                val options = Json.decodeFromString<List<String>>(row[Questions.options])

                Question(
                    id = row[Questions.id].value,
                    text = row[Questions.text],
                    options = options,
                    correctAnswer = row[Questions.correctAnswer],
                    category = row[Questions.category],
                    difficulty = row[Questions.difficulty],
                    explanation = row[Questions.explanation] ?: ""
                )
            }
    }

    private fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    private fun verifyPassword(password: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }
}

fun main() {
    DatabaseFactory.init()

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json()
        }

        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.ContentType)
        }

        val quizService = QuizService()

        routing {
            post("/api/auth/register") {
                val user = call.receive<User>()
                val response = quizService.registerUser(user)
                call.respond(response)
            }

            post("/api/auth/login") {
                val auth = call.receive<AuthRequest>()
                val response = quizService.authenticate(auth.username, auth.password)
                call.respond(response)
            }

            post("/api/questions/upload") {
                val request = call.receive<FileUploadRequest>()
                val content = String(Base64.getDecoder().decode(request.content))
                val category = request.fileName.substringBeforeLast(".")

                val response = quizService.uploadQuestionsFromFile(content, category)
                call.respond(response)
            }

            post("/api/quizzes") {
                val quiz = call.receive<Quiz>()
                val response = quizService.createQuiz(quiz)
                call.respond(response)
            }

            get("/api/quizzes") {
                val response = quizService.getAllQuizzes()
                call.respond(response)
            }

            get("/api/quizzes/{id}") {
                val id = call.parameters["id"]?.toIntOrNull() ?: 0
                val response = quizService.getQuiz(id)
                call.respond(response)
            }

            post("/api/results") {
                val result = call.receive<QuizResult>()
                val response = quizService.submitQuizResult(result)
                call.respond(response)
            }

            get("/api/users/{id}/results") {
                val id = call.parameters["id"]?.toIntOrNull() ?: 0
                val response = quizService.getUserResults(id)
                call.respond(response)
            }

            get("/api/stats/overview") {
                val response = quizService.getStatsOverview()
                call.respond(response)
            }
        }
    }.start(wait = true)
}
