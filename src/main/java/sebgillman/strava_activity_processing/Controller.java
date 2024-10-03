package sebgillman.strava_activity_processing;

import java.io.IOException;
import java.util.HashSet;
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

        TileSet tileSet = new TileSet(coords);

        tileSet.writeRepresentation();

        HashSet<List<Integer>> tiles = tileSet.getSet();

    }

}
