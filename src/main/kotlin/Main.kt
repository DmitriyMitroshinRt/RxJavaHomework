import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.*
import java.rmi.server.ServerNotActiveException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val LATENCY = 700L
private const val RESPONSE_LENGTH = 2048

fun main() {
    println("first:")
    requestDataFromServerAsync().blockingSubscribe(
        { println("array size - ${it.size}") },
        { println("error") })

    println("second:")
    requestServerAsync().blockingSubscribe({ println("Запрос выполнен") }, { println("Ошибка при запросе в БД") })

    println("third:")
    requestDataFromDbAsync<String>().blockingSubscribe(
        { println(it) },
        { println(it.printStackTrace()) },
        { println("Элемент отсутствует") })

    println("fourth:")
    emitEachSecond()

    //xMap { flatMapCompletable(it) }
    //xMap { concatMapCompletable(it) }
    //xMap { switchMapCompletable(it) }
}

// 1) Какой источник лучше всего подойдёт для запроса на сервер, который возвращает результат?
// Single. Т.к мы ожидаемый результат, который или есть или нет. Таким образом мы или его получаем или даем експшн, что его нет.
fun requestDataFromServerAsync(): @NonNull Single<ByteArray> /* -> ???<ByteArray> */ {

    // Функция имитирует синхронный запрос на сервер, возвращающий результат
    fun getDataFromServerSync(): ByteArray? {
        Thread.sleep(LATENCY)
        val success = Random.nextBoolean()
        return if (success) Random.nextBytes(RESPONSE_LENGTH) else null
    }

    /* return ??? */
    return Single.fromCallable { getDataFromServerSync() }
}


// 2) Какой источник лучше всего подойдёт для запроса на сервер, который НЕ возвращает результат?
// Completable не выполняет какого-то действия, однако можно узнать успешность выполнения запроса.
fun requestServerAsync(): @NonNull Completable /* -> ??? */ {

    // Функция имитирует синхронный запрос на сервер, не возвращающий результат
    fun getDataFromServerSync() {
        Thread.sleep(LATENCY)
        if (Random.nextBoolean()) throw ServerNotActiveException()
    }

    fun getDataFromServerSyncWithoutError() {
        Thread.sleep(LATENCY)
        println("Что-то")
    }

    /* return ??? */
    return Completable.fromCallable { getDataFromServerSync() }
}

// 3) Какой источник лучше всего подойдёт для однократного асинхронного возвращения значения из базы данных?
// Maybe. Т.к запрос однократный и тут скорее всего мы не ожидаем результат. Поэтому мы вернем либо 0 либо 1 или среагируем на ошибку
fun <T> requestDataFromDbAsync(): @NonNull Maybe<T> {

    // Функция имитирует синхронный запрос к БД не возвращающий результата
    fun getDataFromDbSync(): T? {
        Thread.sleep(LATENCY); return null
    }

    fun getDataFromDbSyncWithResult(): T? {
        val x = "test"
        Thread.sleep(LATENCY); return x as T
    }

    fun getDataFromDbSyncWithError(): T? {
        val x = "test"
        Thread.sleep(LATENCY); return x.toInt() as T
    }

    /* return */
    return Maybe.fromCallable { getDataFromDbSync() }
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
        .filter { it % 2 == 0L }
        .map { it / 2 }
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