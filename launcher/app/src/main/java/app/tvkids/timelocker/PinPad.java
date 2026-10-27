package app.tvkids.timelocker;

/**
 * Google TV direction pad. Up and down change the row. Left, center and right type that row.
 * Down on the last row types 0. The typed PIN is never shown.
 */
public final class PinPad {
    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;

    public static final char[][] ROWS = {
            {'1', '2', '3'},
            {'4', '5', '6'},
            {'7', '8', '9'}
    };

    private PinPad() {
    }

    public static char digit(int group, int slot) {
        if (group < 0 || group >= ROWS.length || slot < 0 || slot > 2) return 0;
        return ROWS[group][slot];
    }

    public static boolean downTypesZero(int group) {
        return group >= ROWS.length - 1;
    }
}
