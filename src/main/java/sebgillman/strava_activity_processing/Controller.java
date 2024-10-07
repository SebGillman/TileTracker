package sebgillman.strava_activity_processing;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
            HttpResponse response = executeDbQuery(Arrays.asList(queryString));
            System.out.println(response);
        } catch (IOException | InterruptedException | URISyntaxException e) {
        }
    }

    @GetMapping("/leaderboard")
    public JSONObject Leaderboard(@RequestParam Map<String, String> queryParams) throws IOException, URISyntaxException, InterruptedException, ParseException {

        Integer limit = queryParams.containsKey("limit") ? Integer.valueOf(queryParams.get("limit")) : 100;
        Integer offset = queryParams.containsKey("offset") ? Integer.valueOf(queryParams.get("offset")) : 0;
        Long userId = queryParams.containsKey("user_id") ? Long.valueOf(queryParams.get("user_id")) : null;

        List<String> queryList = new ArrayList<>();

        String leaderboardQuery = String.format("""
        SELECT DENSE_RANK() OVER (ORDER BY score DESC) AS rank, user_id, score
        FROM (
            SELECT user_id, count(tile_id) AS score 
            FROM (
                SELECT * FROM tiles t1 WHERE created_at = (
                    SELECT MAX(created_at) 
                    FROM tiles t2 
                    WHERE t1.tile_id = t2.tile_id
                )
            )
            GROUP BY user_id
        )
        ORDER BY score DESC
        LIMIT %d
        OFFSET %d;
        """, limit, offset
        );
        queryList.add(leaderboardQuery);

        if (userId != null) {
            String userRankQuery = String.format("""
            SELECT *
            FROM (
                SELECT DENSE_RANK() OVER (ORDER BY count(tile_id) DESC) AS rank, user_id, count(tile_id) AS score 
                FROM tiles
                GROUP BY user_id
            )
            WHERE user_id = %d;
            """, userId
            );
            queryList.add(userRankQuery);
        }

        // get json of results
        JSONParser jSONParser = new JSONParser();
        JSONObject queryResponseBody = (JSONObject) jSONParser.parse(executeDbQuery(queryList).body().toString());
        JSONArray queryResultsArray = (JSONArray) queryResponseBody.get("results");

        // get rows of leaderboard select query
        JSONObject leaderboardResult = (JSONObject) queryResultsArray.get(0);
        JSONObject leaderboardResponse = (JSONObject) leaderboardResult.get("response");
        JSONObject leaderboardResultObject = (JSONObject) leaderboardResponse.get("result");
        JSONArray leaderboardRows = (JSONArray) leaderboardResultObject.get("rows");

        List<Object> leaderboard = new ArrayList<>();

        for (int i = 0; i < leaderboardRows.size(); i++) {
            JSONArray row = (JSONArray) leaderboardRows.get(i);

            JSONObject rankObject = (JSONObject) row.get(0);
            JSONObject userIdObject = (JSONObject) row.get(1);
            JSONObject scoreObject = (JSONObject) row.get(2);

            JSONObject leaderboardEntry = new JSONObject();
            leaderboardEntry.putAll(Map.of("rank", Integer.valueOf((String) rankObject.get("value")),
                    "user_id", Long.valueOf((String) userIdObject.get("value")),
                    "score", Integer.valueOf((String) scoreObject.get("value"))));

            leaderboard.add(leaderboardEntry.clone());
        }

        JSONObject res = new JSONObject();
        res.put("leaderboard", leaderboard);

        if (queryResultsArray.size() > 2) {
            // get rows of userId select query if included
            JSONObject userResult = (JSONObject) queryResultsArray.get(1);
            JSONObject userResponse = (JSONObject) userResult.get("response");
            JSONObject userResultObject = (JSONObject) userResponse.get("result");
            JSONArray userRows = (JSONArray) userResultObject.get("rows");

            JSONArray userRow = (JSONArray) userRows.get(0);

            JSONObject rankObject = (JSONObject) userRow.get(0);
            JSONObject userIdObject = (JSONObject) userRow.get(1);
            JSONObject scoreObject = (JSONObject) userRow.get(2);

            JSONObject userEntry = new JSONObject();
            userEntry.putAll(Map.of("rank", Integer.valueOf((String) rankObject.get("value")),
                    "user_id", Long.valueOf((String) userIdObject.get("value")),
                    "score", Integer.valueOf((String) scoreObject.get("value"))));

            res.put("user", userEntry);

        }

        return res;

    }

    private HttpResponse executeDbQuery(List<String> queryStrings) throws URISyntaxException, IOException, InterruptedException {
        // Create the main JSON object
        JSONObject mainObject = new JSONObject();
        // Create the JSON array for "requests"
        JSONArray requestsArray = new JSONArray();
        // Create the specified request objects
        queryStrings.forEach(query -> {
            JSONObject request = new JSONObject();
            request.put("type", "execute");
            // Create the "stmt" object for the first request
            JSONObject stmtObject = new JSONObject();
            stmtObject.put("sql", query);
            // Add the "stmt" object to the first request
            request.put("stmt", stmtObject);
            // Add the first request to the array
            requestsArray.add(request.clone());
        });
        // Create the second request object
        JSONObject closeRequest = new JSONObject();
        closeRequest.put("type", "close");
        // Add the second request to the array
        requestsArray.add(closeRequest);
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
