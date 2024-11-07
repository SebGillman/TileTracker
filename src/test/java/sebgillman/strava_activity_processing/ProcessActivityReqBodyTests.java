package sebgillman.strava_activity_processing;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessActivityReqBodyTests {

    private ProcessActivityReqBody reqBody;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        reqBody = new ProcessActivityReqBody();
    }

    @Test
    void testUserIdSetterAndGetter() {
        Long userId = 12345L;
        reqBody.setUserId(userId);

        assertEquals(userId, reqBody.getUserId(), "The getter should return the userId that was set.");
    }

    @Test
    void testActivityIdSetterAndGetter() {
        Long activityId = 67890L;
        reqBody.setActivityId(activityId);

        assertEquals(activityId, reqBody.getActivityId(), "The getter should return the activityId that was set.");
    }

    @Test
    void testCoordsSetterAndGetter() {
        List<List<Double>> coords = Arrays.asList(
                Arrays.asList(51.5074, -0.1278),
                Arrays.asList(51.5075, -0.1279)
        );
        reqBody.setCoords(coords);

        assertEquals(coords, reqBody.getCoords(), "The getter should return the coords that were set.");
    }

    @Test
    void testCreatedAtSetterAndGetter() {
        Long createdAt = System.currentTimeMillis();
        reqBody.setCreatedAt(createdAt);

        assertEquals(createdAt, reqBody.getCreatedAt(), "The getter should return the createdAt timestamp that was set.");
    }

    @Test
    void testEmptyInitialization() {
        assertNull(reqBody.getUserId(), "UserId should be null initially.");
        assertNull(reqBody.getActivityId(), "ActivityId should be null initially.");
        assertNull(reqBody.getCoords(), "Coords should be null initially.");
        assertNull(reqBody.getCreatedAt(), "CreatedAt should be null initially.");
    }
}
