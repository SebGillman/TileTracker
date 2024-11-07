package sebgillman.strava_activity_processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoordTests {

    private Coord coord;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        coord = new Coord();
    }

    @Test
    void testLatitudeSetterAndGetter() {
        Double latitude = 51.5074;
        coord.setLat(latitude);

        assertEquals(latitude, coord.getLat(), "The getter should return the latitude that was set.");
    }

    @Test
    void testLongitudeSetterAndGetter() {
        Double longitude = -0.1278;
        coord.setLong(longitude);

        assertEquals(longitude, coord.getLong(), "The getter should return the longitude that was set.");
    }

    @Test
    void testEmptyInitialization() {
        assertEquals(0.0, coord.getLat(), "Latitude should be 0.0 initially.");
        assertEquals(0.0, coord.getLong(), "Longitude should be 0.0 initially.");
    }
}
