package sebgillman.strava_activity_processing;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    // private static final String template = "Hello, %s!";
    // private final AtomicLong counter = new AtomicLong();

    @PostMapping("/process-activity")
    public void ProcessActivity(@RequestBody ProcessActivityReqBody reqBody) {
        int userId = reqBody.getUserId();
        int activityId = reqBody.getActivityId();
        List<List<Double>> coords = reqBody.getCoords();
        // do request to strava to get 
        System.out.println(String.format("ActivityId: %d", activityId));
        System.out.println(String.format("Coords: %s", coords));
    }
}
