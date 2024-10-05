package sebgillman.strava_activity_processing;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashSet;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @Value("${TILES_DB_URL}")
    private String tilesDbUrl;
    @Value("${TILES_DB_TOKEN}")
    private String tilesDbToken;

    @PostMapping("/process-activity")
    public void ProcessActivity(@RequestBody ProcessActivityReqBody reqBody) throws IOException {

        Long userId = reqBody.getUserId();
        Long activityId = reqBody.getActivityId();
        List<List<Double>> coords = reqBody.getCoords();
        Long createdAt = reqBody.getCreatedAt();

        TileSet tileSet = new TileSet(coords);

        tileSet.writeRepresentation();

        HashSet<List<Integer>> tiles = tileSet.getSet();

        StringBuilder records = new StringBuilder();
        tiles.forEach(tile -> {
            Integer x = tile.get(0);
            Integer y = tile.get(1);
            if (records.length() != 0) {
                records.append(",");
            }
            String recordString = String.format("('%s',%d,%d,%d,%d,%d)",
                    Integer.toString(x) + "," + Integer.toString(y), x, y,
                    userId, activityId, createdAt);

            records.append(recordString);
        });

        String queryString = String.format("INSERT OR REPLACE INTO tiles (tile_id, x_index, y_index, user_id, activity_id, created_at) VALUES %s;", records.toString());

        try {
            HttpResponse response = executeDbQuery(queryString);
            System.out.println(response);
        } catch (IOException | InterruptedException | URISyntaxException e) {
        }
    }

    private HttpResponse executeDbQuery(String queryString) throws URISyntaxException, IOException, InterruptedException {
        // Create the main JSON object
        JSONObject mainObject = new JSONObject();
        // Create the JSON array for "requests"
        JSONArray requestsArray = new JSONArray();
        // Create the first request object
        JSONObject request1 = new JSONObject();
        request1.put("type", "execute");
        // Create the "stmt" object for the first request
        JSONObject stmtObject = new JSONObject();
        stmtObject.put("sql", queryString);
        // Add the "stmt" object to the first request
        request1.put("stmt", stmtObject);
        // Add the first request to the array
        requestsArray.add(request1);
        // Create the second request object
        JSONObject request2 = new JSONObject();
        request2.put("type", "close");
        // Add the second request to the array
        requestsArray.add(request2);
        // Add the "requests" array to the main object
        mainObject.put("requests", requestsArray);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(new URI(this.tilesDbUrl))
                .headers("Content-Type", "application/json")
                .headers("Authorization", "Bearer " + this.tilesDbToken)
                .POST(HttpRequest.BodyPublishers.ofString(mainObject.toString()))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .build()
                .send(httpRequest, BodyHandlers.ofString());

        return response;
    }

}
