import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import java.rmi.server.ServerNotActiveException
import java.util.*
import java.util.concurrent.Callable
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
   // - Сделал два варианта написания фунций - 1ый вариант - оканчивается на суффикс 2 - здесь функции заточены
   // для применения в соответствующих излучателях
   // - 2ой вариант - соответствующие излучатели вставлены в сами функции
   //  - 4 вопрос вызов ф-ции хотел закомментарить - потом передумал -  вероятно так удобнее будет проверять?
   //


    println("Start Q1");
    Maybe.fromCallable {
        var result: ByteArray? = requestDataFromServerAsync2()
        result
        }?.blockingSubscribe({ s : ByteArray? ->  println("2Item received: from Maybe"+s.contentToString()); },
        { obj: Throwable -> obj.printStackTrace() } )
       { println("2Done from MaybeSource") }

    requestDataFromServerAsync().blockingSubscribe(
        {s : ByteArray? ->  println("Item received: from Maybe"+s.contentToString()); },
        { obj: Throwable -> obj.printStackTrace() } )
        { println("Done from MaybeSource") }

    println("Finished Q1");

    println("Start Q2");
    Completable.fromCallable ( object: Callable<Unit> {
         override fun call(): Unit { requestServerAsync2()}} ).
         subscribe({println("2Successful");},
        { obj: Throwable -> obj.printStackTrace() } );

    requestServerAsync().subscribe({println("Successful");},
        { obj: Throwable -> obj.printStackTrace() } );
    println("Finished Q2");

    println("Start Q3");
    Single.fromCallable ({
        var result: Int? =  requestDataFromDbAsync2<Int?>()
         result
                       }).onErrorComplete{throwable : Throwable -> throwable is NullPointerException}
        .blockingSubscribe({println("2S_Item received: from Single:$it")}, { obj: Throwable -> obj.printStackTrace() },{println("2S_Item received: from Single:null")});

    requestDataFromDbAsyncSingle<Int?>().onErrorComplete{throwable : Throwable -> throwable is NullPointerException}
        ?.blockingSubscribe({println("Item received: from Single:$it")}, { obj: Throwable -> obj.printStackTrace() },{println("Item received: from Single:null")});

    Maybe.fromCallable {
        var result: Int? = requestDataFromDbAsync2<Int?>()
        result
    }?.blockingSubscribe({println("2M_Item received: from Maybe:$it")}, { obj: Throwable -> obj.printStackTrace() },{println("2M_Item received: from Maybe: null")});

    requestDataFromDbAsync<Any?>().blockingSubscribe({println("Any? -Item received: from Maybe:$it")}, { obj: Throwable -> obj.printStackTrace() },{println("Any? Item received: from Maybe: null")});
    // Конкретный тип
    requestDataFromDbAsync<Int?>().blockingSubscribe({println("Int?Item received: from Maybe:$it")}, { obj: Throwable -> obj.printStackTrace() },{println("Int? Item received: from Maybe: null")});

    println("Finish Q3");

    println("Start Q4");
    emitEachSecond();
    println("Finish Q4");

    println("Start Q5");
    xMap { flatMapCompletable(it) }
    xMap { concatMapCompletable (it) }
    xMap { switchMapCompletable(it) }
    println("Finish Q5");


}



// 1) Какой источник лучше всего подойдёт для запроса на сервер, который возвращает результат?
//  Maybe - ну результат он возвращает(испускает более правильно в этом паттерне) ..
//  ну и null  в отличии от Single допускает...
//  Можно использовать Observable - но он как бы depricated ? ибо -  MissingBackpressureException или OutOfMemoryError
// но согласно спекам (*) - он подходит на все случаи
// В RxJava  есть три специализированных источника, представляющих собой подмножество Observable.(Single Completable Maybe)
// Третий тип — Maybe.
// Почему?
// Он может либо содержать элемент, либо выдать ошибку, либо не содержать данных — этакий реактивный Optional.
// Согласно лекции номер 2
// Дописать функцию
fun requestDataFromServerAsync2() : ByteArray? /* -> ???<ByteArray> */ {
    // Функция имитирует синхронный запрос на сервер, возвращающий результат
    fun getDataFromServerSync(): ByteArray? {
        Thread.sleep(LATENCY);
        val success = Random.nextBoolean()
        return if (success) Random.nextBytes(RESPONSE_LENGTH) else null
    }
    return getDataFromServerSync()
    /* return ??? */
}

fun requestDataFromServerAsync() : Maybe<ByteArray?> /* -> ???<ByteArray> */ {
    // Функция имитирует синхронный запрос на сервер, возвращающий результат
    fun getDataFromServerSync(): ByteArray? {
        Thread.sleep(LATENCY);
        val success = Random.nextBoolean()
        return if (success) Random.nextBytes(RESPONSE_LENGTH) else null

    }
    return Maybe.fromCallable { getDataFromServerSync() }
    /* return ??? */
}


// 2) Какой источник лучше всего подойдёт для запроса на сервер, который НЕ возвращает результат?
//Completable
// Почему?
// По определению ..- заточен на какое то действие без возврата результата - а ля типа procedure(void-метод) vs function
// Он либо успешно завершает свою работу без каких-либо данных, либо бросает исключение.
// То есть это некий кусок кода, который можно запустить, и он либо успешно выполнится, либо завершится сбоем.
// по реализации - можно  завернуть  иксепшен что бы не делать сверху генерации ошибки - ну если это надо.
// Дописать функцию
fun requestServerAsync2() :Unit  /* -> ??? */ {

    // Функция имитирует синхронный запрос на сервер, не возвращающий результат
    fun getDataFromServerSync() {
        Thread.sleep(LATENCY)
        if (Random.nextBoolean()) throw ServerNotActiveException()
    }
    return  getDataFromServerSync()
    /* return ??? */
}
fun requestServerAsync() : Completable /*Unit*//* -> ??? */ {

    // Функция имитирует синхронный запрос на сервер, не возвращающий результат
    fun getDataFromServerSync() {
        Thread.sleep(LATENCY)
        if (Random.nextBoolean()) throw ServerNotActiveException()
    }
    return Completable.fromCallable { getDataFromServerSync() }

    /* return ??? */
}
// 3) Какой источник лучше всего подойдёт для однократного асинхронного возвращения значения из базы данных?
// Можно конечно использовать Maybe-  возвращает null в отличии  Single
// А Single либо содержит один элемент, либо выдаёт ошибку,
// можно представлять его себе как обычный метод.
// Вы вызываете метод и получаете возвращаемое значение; либо метод бросает исключение.
// Только вот как при проброске исключения сделать что бы Single возвращал значение ?
// через обработчик ошибки  на null ? - обойти это
// Сделал и так и этак - см. выше
// Почему?
//  Больше склоняюсь к maybe - ибо -   может либо содержать элемент, либо выдать ошибку, либо не содержать данных
//  Хотя...(непонятки) - а как такое может быть ? - Функция имитирует синхронный запрос к БД не возвращающий результата
//  а в условиях задачи сказано что  - подойдёт для однократного асинхронного возвращения значения из базы данных
//  хотя в принципе он(Single) и  подходит - а вот если засунут этот "шлямбур" с null -
//  он выдаст ошибку - ибо просто ждет возвращаемого значения из бд


// Дописать функцию
fun <T> requestDataFromDbAsync2() : T? /* -> ??? */ {

    // Функция имитирует синхронный запрос к БД не возвращающий результата
    fun getDataFromDbSync(): T? {
         //val s : Int? = 1
        Thread.sleep(LATENCY); return null
    }
    return getDataFromDbSync()

    /* return */
}

fun <T> requestDataFromDbAsync() : Maybe<T?> /* -> ??? */ {

    // Функция имитирует синхронный запрос к БД не возвращающий результата
    fun getDataFromDbSync(): T? {
        //val s : Int? = 1
        Thread.sleep(LATENCY); return null
    }
    return Maybe.fromCallable {getDataFromDbSync()}

    /* return */
}
fun <T> requestDataFromDbAsyncSingle() : Single<T?> /* -> ??? */ {

    // Функция имитирует синхронный запрос к БД не возвращающий результата
    fun getDataFromDbSync(): T? {
        Thread.sleep(LATENCY); return null
    }
    return Single.fromCallable {getDataFromDbSync()}

    /* return */
}


// Дописать функцию
fun <T> requestDataFromDbAsync7() : Int? /* -> ??? */ {

    // Функция имитирует синхронный запрос к БД не возвращающий результата
    fun getDataFromDbSync(): Int? {
        //val s : Int? = 1
        Thread.sleep(LATENCY); return null
    }
    return getDataFromDbSync()

    /* return */
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
    source().doOnEach { Thread.sleep(2000L)}.blockingSubscribe({printer(it)})
    // code here
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
// возвращает (emit?) результат чисто внешне в рандомном порядке
// в терминологии этого паттерна -  генерит один стрим(несколько) и маппиться напрямую в конечный стрим без гарантии сохранения порядка
//  xMap { concatMapCompletable (it) }
// возвращает (emit?) результат чисто внешне в отсортированном по возрастании порядке
// в терминологии этого паттерна -  генерит один стрим(несколько) и маппиться напрямую в конечный стрим сохраняя порядок
//  xMap { switchMapCompletable(it) }
// возвращает (emit?) результат чисто внешне обрезает и отдает крайний результат
// в терминологии этого паттерна(не уверен)  -  генерит один стрим(несколько) элементв видно не пропускаються и маппиться крайний элемент по порядку.
// Говорят нужно например при вводе символов ....
//
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