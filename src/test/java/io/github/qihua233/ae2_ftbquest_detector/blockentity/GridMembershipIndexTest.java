package io.github.qihua233.ae2_ftbquest_detector.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridMembershipIndexTest {
    @Test
    void membersInTheSameGridAreReturnedTogether() {
        GridMembershipIndex<Object, Object> index = new GridMembershipIndex<>();
        Object grid = new Object();

        index.update(new Object(), grid);
        index.update(new Object(), grid);

        assertEquals(2, index.copyForGrid(grid).size());
    }

    @Test
    void gridsAreMatchedByIdentity() {
        GridMembershipIndex<Object, String> index = new GridMembershipIndex<>();
        Object detector = new Object();
        String registeredGrid = new String("grid");
        String equalButDistinctGrid = new String("grid");

        index.update(detector, registeredGrid);

        assertEquals(1, index.copyForGrid(registeredGrid).size());
        assertTrue(index.copyForGrid(equalButDistinctGrid).isEmpty());
    }

    @Test
    void movedMemberIsOnlyReturnedFromItsCurrentGrid() {
        GridMembershipIndex<Object, String> index = new GridMembershipIndex<>();
        Object detector = new Object();

        index.update(detector, "first");
        index.update(detector, "second");

        assertTrue(index.copyForGrid("first").isEmpty());
        assertEquals(1, index.copyForGrid("second").size());
    }

    @Test
    void removedMemberIsNotReturnedFromItsPreviousGrid() {
        GridMembershipIndex<Object, String> index = new GridMembershipIndex<>();
        Object detector = new Object();
        index.update(detector, "grid");

        index.remove(detector);

        assertTrue(index.copyForGrid("grid").isEmpty());
    }

    @Test
    void updateReturnsPreviousGridForPeerInvalidation() {
        GridMembershipIndex<Object, String> index = new GridMembershipIndex<>();
        Object detector = new Object();
        index.update(detector, "first");

        String previousGrid = index.update(detector, "second");

        assertSame("first", previousGrid);
    }
}
