package sebgillman.strava_activity_processing;

import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @PostMapping("/process-activity")
    public void ProcessActivity(@RequestBody ProcessActivityReqBody reqBody) throws IOException {
        int userId = reqBody.getUserId();
        int activityId = reqBody.getActivityId();
        List<List<Double>> coords = reqBody.getCoords();

        System.out.println(String.format("UserId: %d", userId));
        System.out.println(String.format("ActivityId: %d", activityId));
        System.out.println(String.format("Coords: %s", coords));

        TileSet tiles = new TileSet(coords);
        System.out.println(String.format("tiles: %s", tiles.getSet()));

        tiles.writeRepresentation();

    }

}
