import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import java.rmi.server.ServerNotActiveException
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val LATENCY = 700L
private const val RESPONSE_LENGTH = 2048

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

fun main() {
    println("1. requestDataFromServerAsync")
    requestDataFromServerAsync(true).blockingSubscribe({ println("size: ${it.size}") }, { println("error") })
    requestDataFromServerAsync(false).blockingSubscribe({ println("size: ${it.size}") }, { println("error") })

    println("2. requestServerAsync")
    requestServerAsync(true).blockingSubscribe({ println("complete") }, { println("error") })
    requestServerAsync(false).blockingSubscribe({ println("complete") }, { println("error") })

    println("3. requestDataFromDbAsync")
    requestDataFromDbAsync(true).blockingSubscribe({ println("$it") }, { println("error") })
    requestDataFromDbAsync(false).blockingSubscribe({ println("$it") }, { println("error") }, { println("complete") })

    println("4. emitEachSecond")
    emitEachSecond()

    println("5. xMap")
    xMap { flatMapCompletable(it) }
    xMap { concatMapCompletable(it) }
    xMap { switchMapCompletable(it) }
}

// 1) Какой источник лучше всего подойдёт для запроса на сервер, который возвращает результат?
// Почему?
// Дописать функцию
fun requestDataFromServerAsync(success: Boolean): Single<ByteArray> /* -> ???<ByteArray> */ {

    // Функция имитирует синхронный запрос на сервер, возвращающий результат
    fun getDataFromServerSync(): ByteArray? {
        Thread.sleep(LATENCY)
        // val success = Random.nextBoolean()
        return if (success) Random.nextBytes(RESPONSE_LENGTH) else null
    }

    /* return ??? */
    return Single.fromCallable { getDataFromServerSync() }
}


// 2) Какой источник лучше всего подойдёт для запроса на сервер, который НЕ возвращает результат?
// Почему?
// Дописать функцию
fun requestServerAsync(success: Boolean): Completable /* -> ??? */ {

    // Функция имитирует синхронный запрос на сервер, не возвращающий результат
    fun getDataFromServerSync() {
        Thread.sleep(LATENCY)
        if (!success) throw ServerNotActiveException()
    }

    /* return ??? */
    return Completable.fromCallable { getDataFromServerSync() }
}

// 3) Какой источник лучше всего подойдёт для однократного асинхронного возвращения значения из базы данных?
// Почему?
// Дописать функцию
fun requestDataFromDbAsync(success: Boolean): Maybe<String> /* -> ??? */ {

    // Функция имитирует синхронный запрос к БД не возвращающий результата
    fun getDataFromDbSync(): String? {
        Thread.sleep(LATENCY)
        return if (success) "DATA" else null
    }

    /* return */
    return Maybe.fromCallable { getDataFromDbSync() }
}

fun now(): LocalTime {
    return LocalTime.now()
}

// 4) Примените к источнику оператор (несколько операторов), которые приведут к тому, чтобы элемент из источника
// отправлялся раз в секунду (оригинальный источник делает это в 2 раза чаще).
// Значения должны идти последовательно (0, 1, 2 ...)
// Для проверки результата можно использовать .blockingSubscribe(::printer)
fun emitEachSecond() {

    // Источник
    fun source(): Flowable<Long> = Flowable.interval(500, TimeUnit.MILLISECONDS)

    // Принтер
    fun printer(value: Long) = println("${now()}: value = $value")

    println("${now()}: start")
    // code here
    source()
        .onBackpressureBuffer() // Чтоб не упало
        .takeWhile { it <= 10 } // Чтоб завершилось

        .concatMapSingle { v -> Single.timer(1, TimeUnit.SECONDS).map { v } }
        // или по тупому
        // .doAfterNext { Thread.sleep(1 * 1000) }

        .doOnError { println(it.localizedMessage) }
        .doOnComplete { println("complete") }
        .blockingSubscribe { printer(it) }
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

    println("${now()}: start")
    Flowable.fromIterable(0..10)
        .doOnNext { println("${now()}: index: $it") }
        .mapper { iterableIndex ->

            waitOneSecond()
                .doOnComplete { println("${now()}: finished operation for iterable index $iterableIndex") }

        }
        .blockingSubscribe()
}