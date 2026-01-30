import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

data class Question(
    val id: Int,
    val questionText: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val category: String = "Umumiy",
    val explanation: String = ""
)

data class WrongAnswer(
    val questionId: Int,
    val questionText: String,
    val userAnswer: String,
    val correctAnswer: String,
    val explanation: String
)

data class QuizResult(
    val userName: String,
    val dateTime: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val skippedQuestions: Int,
    val percentage: Double,
    val grade: String,
    val wrongQuestions: List<WrongAnswer>
)

object QuizManager {
    private val questions = mutableListOf<Question>()
    private val quizResults = mutableListOf<QuizResult>()

    fun loadQuestionsFromFile(filename: String): Boolean {
        questions.clear()

        return try {
            val lines = File(filename).readLines(Charsets.UTF_8)
            parseQuestions(lines)
            questions.isNotEmpty()
        } catch (e: Exception) {
            println("❌ Faylni o'qishda xatolik: ${e.message}")
            false
        }
    }

    private fun parseQuestions(lines: List<String>) {
        var questionId = 1
        var currentQuestion = ""
        val options = mutableListOf<String>()
        var correctIndex = -1
        var category = "Umumiy"
        var explanation = ""

        fun flushQuestion() {
            if (currentQuestion.isNotBlank() && options.isNotEmpty() && correctIndex != -1) {
                questions.add(
                    Question(
                        id = questionId++,
                        questionText = currentQuestion,
                        options = options.toList(),
                        correctAnswerIndex = correctIndex,
                        category = category,
                        explanation = explanation
                    )
                )
            }
            currentQuestion = ""
            options.clear()
            correctIndex = -1
            category = "Umumiy"
            explanation = ""
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.startsWith("#") && line.length > 1 -> {
                    flushQuestion()
                    currentQuestion = line.removePrefix("#").trim()
                }
                line == "#" -> {
                    flushQuestion()
                }
                line.startsWith("Kategoriya:") -> {
                    category = line.substringAfter("Kategoriya:").trim()
                }
                line.startsWith("Izoh:") -> {
                    explanation = line.substringAfter("Izoh:").trim()
                }
                line.startsWith("+") -> {
                    options.add(line.removePrefix("+").trim())
                    if (correctIndex == -1) {
                        correctIndex = options.size - 1
                    }
                }
                line.startsWith("-") -> {
                    options.add(line.removePrefix("-").trim())
                }
            }
        }

        flushQuestion()
    }

    fun getQuestionsCount(): Int = questions.size

    fun startQuiz(userName: String): QuizResult {
        println("\n🎯 Test Boshlandi! Omad!")
        println("Savollar soni: ${questions.size}")
        println("-".repeat(50))

        val wrongAnswers = mutableListOf<WrongAnswer>()
        var correctCount = 0
        var skippedCount = 0

        for (question in questions) {
            println("\n📌 Savol ${question.id}/${questions.size}")
            println("Kategoriya: ${question.category}")
            println("Savol: ${question.questionText}")
            println("\nVariantlar:")

            question.options.forEachIndexed { index, option ->
                println("  ${index + 1}. $option")
            }

            val userAnswer = getUserAnswer(question.options.size)

            if (userAnswer == null) {
                println("⏭️ Savol o'tkazib yuborildi")
                skippedCount++
                wrongAnswers.add(
                    WrongAnswer(
                        questionId = question.id,
                        questionText = question.questionText,
                        userAnswer = "O'tkazib yuborildi",
                        correctAnswer = question.options[question.correctAnswerIndex],
                        explanation = question.explanation
                    )
                )
            } else if (userAnswer == question.correctAnswerIndex) {
                println("✅ To'g'ri javob!")
                correctCount++
            } else {
                println("❌ Noto'g'ri!")
                println("💡 To'g'ri javob: ${question.options[question.correctAnswerIndex]}")
                wrongAnswers.add(
                    WrongAnswer(
                        questionId = question.id,
                        questionText = question.questionText,
                        userAnswer = question.options[userAnswer],
                        correctAnswer = question.options[question.correctAnswerIndex],
                        explanation = question.explanation
                    )
                )
            }

            if (question.id < questions.size) {
                print("Davom etish uchun ENTER ni bosing...")
                readLine()
            }
        }

        val percentage = if (questions.isNotEmpty()) {
            (correctCount.toDouble() / questions.size) * 100
        } else {
            0.0
        }
        val grade = calculateGrade(percentage)

        val result = QuizResult(
            userName = userName,
            dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            totalQuestions = questions.size,
            correctAnswers = correctCount,
            skippedQuestions = skippedCount,
            percentage = percentage,
            grade = grade,
            wrongQuestions = wrongAnswers
        )

        quizResults.add(result)
        saveResultToFile(result)

        return result
    }

    private fun getUserAnswer(optionsCount: Int): Int? {
        while (true) {
            print("Javobingizni tanlang (1-$optionsCount): ")
            val input = readLine()?.trim()

            when {
                input == "quit" || input == "exit" -> {
                    println("Test to'xtatildi.")
                    exitProcess(0)
                }
                input == "help" -> {
                    println("📌 Yordam:")
                    println("  - Javob raqamini kiriting (1-$optionsCount)")
                    println("  - 'quit' yoki 'exit' - testni tugatish")
                    println("  - 'skip' - savolni o'tkazib yuborish")
                }
                input == "skip" -> {
                    return null
                }
                else -> {
                    val answer = input?.toIntOrNull()
                    if (answer != null && answer in 1..optionsCount) {
                        return answer - 1
                    }
                    println("⚠️ Iltimos, 1 dan $optionsCount gacha raqam kiriting!")
                }
            }
        }
    }

    private fun calculateGrade(percentage: Double): String {
        return when {
            percentage >= 95 -> "A⁺ (A'lo)"
            percentage >= 90 -> "A (Juda yaxshi)"
            percentage >= 85 -> "B⁺ (Yaxshi)"
            percentage >= 80 -> "B (Yaxshi)"
            percentage >= 75 -> "C⁺ (Qoniqarli)"
            percentage >= 70 -> "C (Qoniqarli)"
            percentage >= 65 -> "D⁺ (Qoniqarsiz)"
            percentage >= 60 -> "D (Qoniqarsiz)"
            else -> "F (Yomon)"
        }
    }

    private fun saveResultToFile(result: QuizResult) {
        try {
            val filename = "quiz_results_${System.currentTimeMillis()}.txt"
            val header = """
                |=== QUIZ NATIJALARI ===
                |Foydalanuvchi: ${result.userName}
                |Sana va vaqt: ${result.dateTime}
                |Umumiy savollar: ${result.totalQuestions}
                |To'g'ri javoblar: ${result.correctAnswers}
                |O'tkazib yuborilgan: ${result.skippedQuestions}
                |Foiz: ${String.format("%.2f", result.percentage)}%
                |Baholash: ${result.grade}
                |
                |=== NOTO'G'RI JAVOBLAR ===
            """.trimMargin()

            val wrongAnswersText = if (result.wrongQuestions.isEmpty()) {
                "Hamma javoblar to'g'ri! 🎉"
            } else {
                result.wrongQuestions.mapIndexed { index, wrong ->
                    """
                        |
                        |${index + 1}. Savol: ${wrong.questionText}
                        |   Sizning javobingiz: ${wrong.userAnswer}
                        |   To'g'ri javob: ${wrong.correctAnswer}
                        |   Izoh: ${wrong.explanation.ifBlank { "Izoh mavjud emas" }}
                    """.trimMargin()
                }.joinToString("")
            }

            File(filename).writeText(header + wrongAnswersText)
            println("\n📁 Natijalar '$filename' fayliga saqlandi!")
        } catch (e: Exception) {
            println("❌ Natijalarni saqlashda xatolik: ${e.message}")
        }
    }

    fun showDetailedResults(result: QuizResult) {
        println("\n" + "=".repeat(60))
        println("📊 BATAFSIL NATIJALAR")
        println("=".repeat(60))

        println("👤 Foydalanuvchi: ${result.userName}")
        println("📅 Sana: ${result.dateTime}")
        println("📝 Umumiy savollar: ${result.totalQuestions}")
        println("✅ To'g'ri javoblar: ${result.correctAnswers}")
        println("⏭️ O'tkazib yuborilgan: ${result.skippedQuestions}")
        println("❌ Xato javoblar: ${result.totalQuestions - result.correctAnswers - result.skippedQuestions}")
        println("📈 Foiz: ${String.format("%.2f", result.percentage)}%")
        println("🏆 Baholash: ${result.grade}")

        if (result.wrongQuestions.isNotEmpty()) {
            println("\n" + "=".repeat(60))
            println("❌ NOTO'G'RI JAVOBLAR TAHLILI")
            println("=".repeat(60))

            result.wrongQuestions.forEachIndexed { index, wrong ->
                println("\n${index + 1}. Savol: ${wrong.questionText}")
                println("   Sizning javobingiz: ❌ ${wrong.userAnswer}")
                println("   To'g'ri javob: ✅ ${wrong.correctAnswer}")
                if (wrong.explanation.isNotBlank()) {
                    println("   Izoh: ${wrong.explanation}")
                }
                println("   " + "-".repeat(50))
            }
        } else {
            println("\n🎉 TABRIKLAYMIZ! HAMMA JAVOBLAR TO'G'RI! 🎉")
        }

        println("\n📊 NATIJA GRAFIKASI:")
        val barLength = 50
        val correctBarLength = (result.percentage * barLength / 100).toInt()
        val correctBar = "█".repeat(correctBarLength)
        val wrongBar = "░".repeat(barLength - correctBarLength)
        println("[$correctBar$wrongBar] ${String.format("%.1f", result.percentage)}%")
    }

    fun showPreviousResults() {
        if (quizResults.isEmpty()) {
            println("📭 Hali hech qanday natija mavjud emas!")
            return
        }

        println("\n" + "=".repeat(60))
        println("📋 OLDINGI NATIJALAR")
        println("=".repeat(60))

        quizResults.forEachIndexed { index, result ->
            println(
                """
                |
                |Test #${index + 1}:
                |  Foydalanuvchi: ${result.userName}
                |  Sana: ${result.dateTime}
                |  Natija: ${result.correctAnswers}/${result.totalQuestions} (${String.format("%.1f", result.percentage)}%)
                |  Baho: ${result.grade}
                """.trimMargin()
            )
        }
    }
}

fun main() {
    println("🎓 QUIZ DASTURI - Test Tizimi")
    println("=".repeat(50))

    print("Ismingizni kiriting: ")
    val userName = readLine()?.trim().orEmpty().ifBlank { "Mehmon" }

    var questionsLoaded = false
    while (!questionsLoaded) {
        print("Savollar faylini nomini kiriting (questions.txt): ")
        val filename = readLine()?.trim().orEmpty().ifBlank { "questions.txt" }

        questionsLoaded = QuizManager.loadQuestionsFromFile(filename)

        if (!questionsLoaded) {
            println("Faylni yuklash muvaffaqiyatsiz tugadi. Qayta urinib ko'ring.")
            print("Yangi fayl nomi yoki 'exit' deb yozing: ")
            val newInput = readLine()?.trim()
            if (newInput == "exit") return
        }
    }

    println("✅ ${QuizManager.getQuestionsCount()} ta savol muvaffaqiyatli yuklandi!")

    var exit = false
    while (!exit) {
        println("\n" + "=".repeat(50))
        println("🏠 ASOSIY MENYU")
        println("=".repeat(50))
        println("1. 🎯 Testni boshlash")
        println("2. 📊 Oldingi natijalarni ko'rish")
        println("3. 📁 Yangi fayl yuklash")
        println("4. ℹ️  Dastur haqida ma'lumot")
        println("5. 🚪 Chiqish")
        print("\nTanlang (1-5): ")

        when (readLine()) {
            "1" -> {
                val result = QuizManager.startQuiz(userName)
                println("\n" + "=".repeat(50))
                print("Natijani ko'rishni xohlaysizmi? (ha/yoq): ")
                if (readLine()?.equals("ha", ignoreCase = true) == true) {
                    QuizManager.showDetailedResults(result)
                }

                print("Bosh menyuga qaytish uchun ENTER ni bosing...")
                readLine()
            }
            "2" -> {
                QuizManager.showPreviousResults()
                print("Bosh menyuga qaytish uchun ENTER ni bosing...")
                readLine()
            }
            "3" -> {
                print("Yangi fayl nomini kiriting: ")
                val newFile = readLine()?.trim().orEmpty()
                if (newFile.isNotBlank()) {
                    if (QuizManager.loadQuestionsFromFile(newFile)) {
                        println("✅ Yangi savollar yuklandi!")
                    }
                }
            }
            "4" -> {
                println(
                    """
                    |
                    |📚 QUIZ DASTURI
                    |Version: 2.0
                    |Muallif: AI Assistant
                    |
                    |Xususiyatlar:
                    |• 📄 TXT fayldan savollarni o'qish
                    |• 🎯 Interaktiv test jarayoni
                    |• 📊 Batafsil natijalar va statistikalar
                    |• ❌ Xato javoblarni tahlil qilish
                    |• 💾 Natijalarni faylga saqlash
                    |• 📋 Oldingi natijalarni ko'rish
                    |
                    |Fayl formati:
                    |#Savol matni
                    |Kategoriya: Fan nomi
                    |+to'g'ri javob
                    |-noto'g'ri javob 1
                    |-noto'g'ri javob 2
                    |Izoh: Qisqa tushuntirish
                    |#
                    """.trimMargin()
                )
                print("Bosh menyuga qaytish uchun ENTER ni bosing...")
                readLine()
            }
            "5" -> {
                println("👋 Dasturdan chiqildi. Rahmat!")
                exit = true
            }
            else -> {
                println("⚠️ Noto'g'ri tanlov! Iltimos, 1-5 oralig'ida raqam tanlang.")
            }
        }
    }
}
