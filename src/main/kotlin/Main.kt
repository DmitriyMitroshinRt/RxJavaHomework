import io.reactivex.rxjava3.core.*
import java.rmi.server.ServerNotActiveException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val LATENCY = 700L
private const val RESPONSE_LENGTH = 2048

fun main() {
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

    println("1.0")
    requestDataFromServerAsync().blockingSubscribe({println(it)},{println(it.message)})

    println("2.0")
    requestServerAsync().blockingSubscribe({println("complete")},{println(it.message)})

    println("3.0")
    requestDataFromDbAsync<String>().blockingSubscribe({ println(it) }, { println("error: ${it.message}") }, { println("complete") })

    println("4.0")
    emitEachSecond()

    println("5.1")
    xMap { flatMapCompletable(it) }
    println("5.2")
    xMap { concatMapCompletable (it) }
    println("5.3")
    xMap { switchMapCompletable(it) }

}

// 1) Какой источник лучше всего подойдёт для запроса на сервер, который возвращает результат?
// Single
// Почему?
// В Single возможны две ситуации:
//  одно значение -> onSuccess()
//  exception -> onError()
// Дописать функцию
fun requestDataFromServerAsync(): Single<ByteArray> {

    // Функция имитирует синхронный запрос на сервер, возвращающий результат
    fun getDataFromServerSync(): ByteArray? {
        Thread.sleep(LATENCY)
        val success = Random.nextBoolean()
        return if (success) Random.nextBytes(RESPONSE_LENGTH) else null
    }

    return Single.fromCallable {
        getDataFromServerSync()
    }
}


// 2) Какой источник лучше всего подойдёт для запроса на сервер, который НЕ возвращает результат?
// Completable
// Почему?
// Либо успешно завершает свою работу без каких-либо данных, либо бросает исключение.
// Дописать функцию
fun requestServerAsync(): Completable {

    // Функция имитирует синхронный запрос на сервер, не возвращающий результат
    fun getDataFromServerSync() {
        Thread.sleep(LATENCY)
        if (Random.nextBoolean()) throw ServerNotActiveException()
    }

    return Completable.fromAction {
        getDataFromServerSync()
    }
}

// 3) Какой источник лучше всего подойдёт для однократного асинхронного возвращения значения из базы данных?
// Maybe
// Почему?
// может либо содержать элемент, либо выдать ошибку, либо не содержать данных - что вполне рационально для бд
// Дописать функцию
fun <T> requestDataFromDbAsync(): Maybe<T> {

    // Функция имитирует синхронный запрос к БД не возвращающий результата
    fun getDataFromDbSync(): T? {
        Thread.sleep(LATENCY); return null
    }

    return Maybe.fromAction {
        getDataFromDbSync()
    }
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

    source()
        .filter {
            it % 2 == 0L
        }
        .map {
            it / 2
        }
        .blockingSubscribe(::printer)
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