import kotlin.test.*
import org.junit.Test

class SimpleTest {

    @Test fun testFoo() {
        assertEquals(20, foo())
    }

    @Test fun testBar() {
        assertEquals(10, foo())
    }

    @Ignore @Test fun testFooWrong() {
        assertEquals(20, foo())
    }

}

@Ignore
class TestTest {

    @Test fun emptyTest() {
    }

    class InnerTest {
        @Test fun innerTest() {
            assertTrue { true }
        }
    }

}