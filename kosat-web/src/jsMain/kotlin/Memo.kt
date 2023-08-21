/**
 * A small utility class that memoizes a function with a single argument.
 */
class Memo<in T, out Q>(val fn: (T) -> Q) {
    private val map = mutableMapOf<T, Q>()

    operator fun invoke(arg: T): Q {
        return map.getOrPut(arg) { fn(arg) }
    }
}

/**
 * Given a function, returns a memoized version of it.
 */
fun <T, Q> ((T) -> Q).memoized(): Memo<T, Q> {
    return Memo(this)
}
