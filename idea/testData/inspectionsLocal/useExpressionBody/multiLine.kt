// PROBLEM: none

fun Int?.orZero(): Int {
    return <caret>this
           ?: 0
}