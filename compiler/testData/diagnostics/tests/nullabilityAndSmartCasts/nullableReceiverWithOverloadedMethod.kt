// !DIAGNOSTICS: -UNUSED_PARAMETER

class A {
    fun f(x: Boolean) {
    }

    fun f(y: String) {
    }
}

class B {
    private var a: A? = null

    fun f() {
        a = A()
        <!SMARTCAST_IMPOSSIBLE!>a<!>.f(true)
    }
}