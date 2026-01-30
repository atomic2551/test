# Kotlin Console Quiz

Ushbu loyiha `.txt` fayldagi savollar asosida konsolda test o'tkazadi va natijalarni faylga saqlaydi.

## Fayl formati

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

## Ishga tushirish

```bash
kotlinc src/QuizApp.kt -include-runtime -d QuizApp.jar
java -jar QuizApp.jar
```

`questions.txt` fayli loyiha ildizida bo'lishi kerak.

## Asosiy imkoniyatlar

- Interaktiv test jarayoni (skip/help/exit komandasi).
- Kategoriyalar bo'yicha savollar.
- Batafsil natija va statistikalar.
- Natijani alohida faylga saqlash.
