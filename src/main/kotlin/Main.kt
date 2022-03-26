import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import java.rmi.server.ServerNotActiveException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val LATENCY = 700L
private const val RESPONSE_LENGTH = 2048

fun main() {
    //requestDataFromServerAsync()
    //requestServerAsync()
    //requestDataFromDbAsync<String>()
    //emitEachSecond()
    //xMap { flatMapCompletable(it) }
    //xMap { concatMapCompletable(it) }
    //xMap { switchMapCompletable(it) }
    // Функции можно вызывать отсюда для проверки
    //  для ДЗ лучше использовать blockingSubscribe вместо subscribe потому что subscribe подпишется на изменения,
    //  но изменения в большинстве случаев будут получены позже, чем выполнится функция main, поэтому в консоли ничего
    //  не будет выведено. blockingSubscribe работает синхронно, поэтому результат будет выведен в консоль
    //
    //  В реальных программах нужно использовать subscribe или передавать данные от источника к источнику для
    //  асинхронного выполнения кода.
    //
    //  Несмотря на то, что в некоторых заданиях фигурируют слова "синхронный" и "асинхронный" в рамках текущего ДЗ
    //  это всего лишь имитация, реальное переключение между потоками будет рассмотрено на следующем семинаре
}

// 1) Какой источник лучше всего подойдёт для запроса на сервер, который возвращает результат?
// Почему? Maybe позволяет обработать null в источнике данных
// Дописать функцию
fun requestDataFromServerAsync(): ByteArray?/* -> ???<ByteArray> */ {

    // Функция имитирует синхронный запрос на сервер, возвращающий результат
    fun getDataFromServerSync(): ByteArray? {
        Thread.sleep(LATENCY)
        val success = Random.nextBoolean()
        return if (success) Random.nextBytes(RESPONSE_LENGTH) else null
    }

    var result: ByteArray? = null
    Maybe.fromCallable<Any> { getDataFromServerSync() }
        .blockingSubscribe({
            println(it)
            result = it as ByteArray?
        }, { it.printStackTrace() }, {
            println("complete")
        })
    /* return ??? */
    return result
}


// 2) Какой источник лучше всего подойдёт для запроса на сервер, который НЕ возвращает результат?
// Почему? Completable потому что нас интересует лишь факт выполнения операции
// Дописать функцию
fun requestServerAsync(): Boolean/* -> ??? */ {
    // Функция имитирует синхронный запрос на сервер, не возвращающий результат
    fun getDataFromServerSync() {
        Thread.sleep(LATENCY)
        if (Random.nextBoolean()) throw ServerNotActiveException()
    }

    var result = true
    Completable.fromAction { getDataFromServerSync() }
        .blockingSubscribe({
            println("requestServerAsync success")
        }, {
            result = false
            it.printStackTrace()
        })
    return result
}

// 3) Какой источник лучше всего подойдёт для однократного асинхронного возвращения значения из базы данных?
// Почему? Maybe мы ожидаем возможное null значение
// Дописать функцию
fun <T> requestDataFromDbAsync(): T? /* -> ??? */ {
    // Функция имитирует синхронный запрос к БД не возвращающий результата
    fun getDataFromDbSync(): T? {
        Thread.sleep(LATENCY); return null
    }

    var result: T? = null
    Maybe.fromCallable<Any> { getDataFromDbSync() }
        .blockingSubscribe({
            println("requestDataFromDbAsync $it success")
                result = it as T
        },
            { println("requestDataFromDbAsync error") },
            {
                println("requestDataFromDbAsync complete")
                result = null
            })
    /* return */
    return result
}

// 4) Примените к источнику оператор (несколько операторов), которые приведут к тому, чтобы элемент из источника
// отправлялся раз в секунду (оригинальный источник делает это в 2 раза чаще).
// Значения должны идти последовательно (0, 1, 2 ...)
// Для проверки результата можно использовать .blockingSubscribe(::printer)
fun emitEachSecond() {

    // Источник
    fun source(): Flowable<Long> = Flowable.interval(500, TimeUnit.MILLISECONDS)

    // Принтер
    fun printer(value: Long) = println("${Date()}: value = $value")

    // code here
    source()
        .onBackpressureBuffer()
        .concatMap { Flowable.interval(1000, TimeUnit.MILLISECONDS) }
        .blockingSubscribe({ printer(it) }, { it.printStackTrace() })
}

// 5) Функция для изучения разницы между операторами concatMap, flatMap, switchMap
// Нужно вызвать их последовательно и разобраться чем они отличаются
// Документацию в IDEA можно вызвать встав на функцию (например switchMap) курсором и нажав hotkey для вашей раскладки
// Mac: Intellij Idea -> Preferences -> Keymap -> Быстрый поиск "Quick documentation"
// Win, Linux: File -> Settings -> Keymap -> Быстрый поиск "Quick documentation"
//
// конструкция в аргументах функции xMap не имеет значения для ДЗ и создана для удобства вызова функции, чтобы была
//  возможность удобно заменять тип маппинга
//
// Вызов осуществлять поочерёдно из функции main
//
//  xMap { flatMapCompletable(it) }
//  xMap { concatMapCompletable (it) }
//  xMap { switchMapCompletable(it) }
//
fun xMap(mapper: Flowable<Int>.(internalMapper: (Int) -> Completable) -> Completable) {

    fun waitOneSecond() = Completable.timer(1, TimeUnit.SECONDS)

    println("${Date()}: start")
    Flowable.fromIterable(0..20)
        .mapper { iterableIndex ->
            waitOneSecond()
                .doOnComplete { println("${Date()}: finished operation for iterable index $iterableIndex") }

        }
        .blockingSubscribe()
}