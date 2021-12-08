import com.lauriethefish.betterportals.bukkit.util.ArrayUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArrayUtilTests {
    private final Integer[] testArray = new Integer[]{1, 2, 3, 4, 5};

    @Test
    public void testRemoveFirstElement() {
        Integer[] result = ArrayUtil.removeFirstElement(testArray);

        assertEquals((Integer) 2, result[0]);
        assertEquals(4, result.length);
    }

    @Test()
    public void testRemoveFirstElementEmpty() {
        assertThrows(IllegalArgumentException.class, () -> ArrayUtil.removeFirstElement(new Integer[0]));
    }

    @Test
    public void testRemoveLastElement() {
        Integer[] result = ArrayUtil.removeLastElement(testArray);

        assertEquals((Integer) 4, result[3]);
        assertEquals(4, result.length);
    }

    @Test()
    public void testRemoveLastElementEmpty() {
        assertThrows(IllegalArgumentException.class, () -> ArrayUtil.removeLastElement(new Integer[0]));
    }
}
