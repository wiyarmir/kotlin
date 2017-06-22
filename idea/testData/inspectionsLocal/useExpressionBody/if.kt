fun abs(x: Int): Int {
    // Controversial, may be we should not report anything here
    <caret>return if (x > 0) {
        x
    }
    else {
        -x
    }
}