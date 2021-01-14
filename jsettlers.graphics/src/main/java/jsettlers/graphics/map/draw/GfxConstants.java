package jsettlers.graphics.map.draw;

/**
 * gfx file constants
 * arrays contain civilisation specific data in following order {roman, egyptian, asian, amazon}
 * file = gfx file index
 * seq = gfx sequence index
 *
 * @author MarviMarv
 *
 */
public final class GfxConstants {
    public static final int FILE_OBJECT = 1;
    public static final int FILE_OBJECT_PROFESSION = 6;
    public static final int[] FILE_WORKER_BEARER = {10, 20, 30, 40};
    public static final int[] FILE_WORKER_PROFESSION = {11, 21, 31, 41};
    public static final int[] FILE_MILITARY = {12, 22, 32, 42};
    public static final int[] FILE_BUILDING = {13, 23, 33, 43};
    public static final int[] FILE_BUILDING_GUI = {14, 24, 34, 44};

    public static final int[] SEQ_MILL_ROTATION = {15, 22, 21, 20};

    //Mana Plants - File 1
    public static final int SEQ_WINE = 25;
    public static final int COUNT_WINE_GROW_STEPS = 3;
    public static final int INDEX_WINE_DEAD_STEP = 0;

    public static final int SEQ_RICE = 24;
    public static final int COUNT_RICE_GROW_STEPS = 5;
    public static final int INDEX_RICE_DEAD_STEP = 0;



    public static final int[] SEQ_TEMPLE_MANA_BOWL = {46, 47, 49, 49};
    public static final int COUNT_TEMPLE_MANA_BOWL_IMAGES = 9;

    private GfxConstants() {
    }
}
