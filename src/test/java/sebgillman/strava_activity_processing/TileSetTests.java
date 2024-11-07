package sebgillman.strava_activity_processing;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

class TileSetTests {

    private TileSet tileSet;

    // Helper method for converting coordinates to tile indexes (public, static method)
    @Test
    void testCoordToTileIndexes() {
        List<Double> coord = Arrays.asList(51.5074, -0.1278); // Example: London
        List<Integer> tileIndexes = TileSet.coordToTileIndexes(coord);

        assertNotNull(tileIndexes);
        assertEquals(2, tileIndexes.size());
    }

    @Test
    void testTileSetConstructionWithValidCoordinates() {
        List<List<Double>> coords = Arrays.asList(
                Arrays.asList(51.5074, -0.1278), // Example coordinates forming a route
                Arrays.asList(51.5075, -0.1279),
                Arrays.asList(51.5076, -0.1280)
        );

        tileSet = new TileSet(coords);

        // Verifying that a non-empty set of tiles was generated
        assertFalse(tileSet.getSet().isEmpty(), "TileSet should contain tiles for the route.");
    }

    @Test
    void testTileSetWithEmptyCoordinates() {
        List<List<Double>> coords = Arrays.asList();  // Empty coordinates list

        tileSet = new TileSet(coords);

        // Verifying that an empty tile set was created
        assertTrue(tileSet.getSet().isEmpty(), "TileSet should be empty when initialized with no coordinates.");
    }

    @Test
    void testTileRepresentationOutput() {
        List<List<Double>> coords = Arrays.asList(
                Arrays.asList(51.5074, -0.1278),
                Arrays.asList(51.5075, -0.1279)
        );

        tileSet = new TileSet(coords);

        try {
            tileSet.writeRepresentation();  // Test writing to file output
            // Here, we could validate file output manually, or use a mock file system in a more advanced setup.
        } catch (IOException e) {
            fail("writeRepresentation() should not throw an IOException.");
        }
    }
}
