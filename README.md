# QuizApp

Ushbu repoda ikki xil yechim mavjud:

1. **Kotlin Console Quiz** — `questions.txt` asosida konsolda test o'tkazish.
2. **Full-stack Quiz Application** — Ktor backend + Compose Multiplatform desktop frontend + SQLite.

## 📁 Loyihalar tuzilishi

```
QuizApp/
├── backend/   (Kotlin + Ktor)
├── frontend/  (Compose Multiplatform)
├── shared/    (Umumiy kod uchun katalog)
├── database/  (SQLite fayllari uchun katalog)
├── src/       (Kotlin console ilovasi)
└── questions.txt
```

## ✅ Console Quiz

### Fayl formati

```
#Savol matni
Kategoriya: Fan nomi
+To'g'ri javob
-Noto'g'ri javob
-Noto'g'ri javob
-Noto'g'ri javob
Izoh: Qisqa tushuntirish
#
```

- `Kategoriya:` va `Izoh:` qatorlari ixtiyoriy.
- Har bir savol `#` bilan boshlanadi va `#` qatori bilan tugaydi.

### Ishga tushirish

```bash
kotlinc src/QuizApp.kt -include-runtime -d QuizApp.jar
java -jar QuizApp.jar
```

`questions.txt` fayli loyiha ildizida bo'lishi kerak.

### Asosiy imkoniyatlar

- Interaktiv test jarayoni (skip/help/exit komandasi).
- Kategoriyalar bo'yicha savollar.
- Batafsil natija va statistikalar.
- Natijani alohida faylga saqlash.

## 🌐 Full-stack Quiz Application

### Root build (ko'p modul)

`settings.gradle.kts` orqali `backend`, `frontend`, va `shared` modullari birlashtirilgan. Xohlasangiz, quyidagicha ishlatishingiz mumkin:

```bash
./gradlew :backend:run
./gradlew :frontend:run
```

### Backend (Ktor + SQLite)

```bash
cd backend
./gradlew run
```

Backend odatda `http://localhost:8080` da ishlaydi.

### Frontend (Compose Multiplatform)

```bash
cd frontend
./gradlew run
```

### Jar fayl yaratish

```bash
# Backend
cd backend
./gradlew shadowJar

# Frontend
cd frontend
./gradlew package
```

## ➕ Qo'shimcha funksiyalar (keyingi versiyalar)

- Real-time multiplayer quizlar
- Leaderboard (reyting jadvali)
- Push bildirishnomalar
- Offline rejim
- PDF/Excel eksport
- Audio/video savollar
- Multi-language qo'llab-quvvatlash
