package app.tvkids.timelocker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PinBookTest {
    @Test
    public void lockoutDoublesFromThirtySeconds() {
        PinBook book = PinBook.create("1234", 1000);
        assertNotNull(book);
        assertNull(PinBook.create("12", 1000));
        assertNull(PinBook.create("abcd", 1000));
        long t = 1_000_000L;
        assertEquals("bad", book.verify("0000", t));
        assertEquals("bad", book.verify("0000", t));
        assertEquals("bad", book.verify("0000", t));
        assertEquals("bad", book.verify("0000", t));
        assertEquals("bad", book.verify("0000", t));
        assertTrue(book.lockUntil >= t + 30_000L);
        assertEquals("locked", book.verify("1234", t));
        assertEquals("locked", book.verify("1234", t + 1000));
        assertEquals("ok", book.verify("1234", t + 31_000));
        assertEquals(0, book.fails);
    }
}
