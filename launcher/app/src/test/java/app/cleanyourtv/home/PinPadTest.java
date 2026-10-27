package app.cleanyourtv.home;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PinPadTest {
    @Test
    public void rowsMatchTheGooglePad() {
        assertEquals('1', PinPad.digit(0, PinPad.LEFT));
        assertEquals('2', PinPad.digit(0, PinPad.CENTER));
        assertEquals('3', PinPad.digit(0, PinPad.RIGHT));
        assertEquals('4', PinPad.digit(1, PinPad.LEFT));
        assertEquals('5', PinPad.digit(1, PinPad.CENTER));
        assertEquals('6', PinPad.digit(1, PinPad.RIGHT));
        assertEquals('7', PinPad.digit(2, PinPad.LEFT));
        assertEquals('8', PinPad.digit(2, PinPad.CENTER));
        assertEquals('9', PinPad.digit(2, PinPad.RIGHT));
        assertFalse(PinPad.downTypesZero(0));
        assertFalse(PinPad.downTypesZero(1));
        assertTrue(PinPad.downTypesZero(2));
    }
}
