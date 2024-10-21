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
    public String ProcessActivity(@RequestBody ProcessActivityReqBody reqBody) throws IOException {
        System.out.println("[START] /process-activity");
        Long userId = reqBody.getUserId();
        Long activityId = reqBody.getActivityId();
        List<List<Double>> coords = reqBody.getCoords();
        Long createdAt = reqBody.getCreatedAt();

        TileSet tileSet = new TileSet(coords);

        tileSet.writeRepresentation();

        HashSet<List<Integer>> tiles = tileSet.getSet();

        StringBuilder records = new StringBuilder();
        tiles.forEach(tile -> {
            Integer x = tile.get(1);
            Integer y = tile.get(0);
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
            if (response.statusCode() != 200) {
                throw new Error(String.format("%d: %s", response.statusCode(), response.toString()));
            }
            return response.toString();
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new Error(e);
        }
    }

    @GetMapping("/ping")
    public String Ping() {
        return "Service healthy!";
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

    @GetMapping("/tiles")
    public JSONObject Tiles(@RequestParam Map<String, String> queryParams) throws IOException, URISyntaxException, InterruptedException, ParseException {

        if (!queryParams.containsKey("x1") || !queryParams.containsKey("y1")
                || !queryParams.containsKey("x2")
                || !queryParams.containsKey("y2")) {
            return new JSONObject();
        }
        Double x1Param = Double.valueOf(queryParams.get("x1"));
        Double x2Param = Double.valueOf(queryParams.get("x2"));
        Double y1Param = Double.valueOf(queryParams.get("y1"));
        Double y2Param = Double.valueOf(queryParams.get("y2"));

        List<Integer> coord1 = TileSet.coordToTileIndexes(Arrays.asList(y1Param, x1Param));
        List<Integer> coord2 = TileSet.coordToTileIndexes(Arrays.asList(y2Param, x2Param));

        List<String> queryList = new ArrayList<>();

        String tileQueryString = String.format(
                "SELECT x_index, y_index, user_id, activity_id, created_at "
                + "FROM tiles t1 WHERE created_at = ( "
                + "SELECT MAX(created_at) FROM tiles t2 WHERE t1.tile_id = t2.tile_id) "
                + "AND ((%d <= %d AND t1.x_index BETWEEN %d AND %d) OR (%d > %d AND (t1.x_index BETWEEN %d AND 360000 OR (t1.x_index + 360000) BETWEEN %d AND %d + 360000))) "
                + "AND ((%d <= %d AND t1.y_index BETWEEN %d AND %d) OR (%d > %d AND (t1.y_index BETWEEN %d AND 360000 OR (t1.y_index + 360000) BETWEEN %d AND %d + 360000)));",
                coord1.get(1), coord2.get(1), coord1.get(1), coord2.get(1), // x_index logic part 1 (normal range)
                coord1.get(1), coord2.get(1), coord1.get(1), coord1.get(1), coord2.get(1), // x_index logic part 2 (wrap-around range)
                coord1.get(0), coord2.get(0), coord1.get(0), coord2.get(0), // y_index logic part 1 (normal range)
                coord1.get(0), coord2.get(0), coord1.get(0), coord1.get(0), coord2.get(0) // y_index logic part 2 (wrap-around range)
        );

        queryList.add(tileQueryString);

        // get json of results
        JSONParser jSONParser = new JSONParser();
        JSONObject queryResponseBody = (JSONObject) jSONParser.parse(executeDbQuery(queryList).body().toString());
        JSONArray queryResultsArray = (JSONArray) queryResponseBody.get("results");

        // get rows of tile select query
        JSONObject tileQueryResult = (JSONObject) queryResultsArray.get(0);
        JSONObject tileQueryResponse = (JSONObject) tileQueryResult.get("response");
        JSONObject tileQueryResultObject = (JSONObject) tileQueryResponse.get("result");
        JSONArray tileQueryRows = (JSONArray) tileQueryResultObject.get("rows");

        List<Object> tiles = new ArrayList<>();

        for (int i = 0; i < tileQueryRows.size(); i++) {
            JSONArray row = (JSONArray) tileQueryRows.get(i);

            JSONObject xObject = (JSONObject) row.get(0);
            JSONObject yObject = (JSONObject) row.get(1);
            JSONObject userIdObject = (JSONObject) row.get(2);
            JSONObject activityIdObject = (JSONObject) row.get(3);
            JSONObject createdAtObject = (JSONObject) row.get(2);

            JSONObject tilesEntry = new JSONObject();
            tilesEntry.putAll(Map.of(
                    "x_index", Long.valueOf((String) xObject.get("value")),
                    "y_index", Long.valueOf((String) yObject.get("value")),
                    "user_id", Long.valueOf((String) userIdObject.get("value")),
                    "activity_id", Long.valueOf((String) activityIdObject.get("value")),
                    "created_at", Long.valueOf((String) createdAtObject.get("value"))
            ));
            tiles.add(tilesEntry.clone());
        }

        JSONObject res = new JSONObject();
        res.put("tiles", tiles);
        res.put("tile_count", tileQueryRows.size());

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
