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
import java.util.Objects;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @DeleteMapping("/game")
    public JSONArray DeleteGame(@RequestParam Map<String, String> queryParams) throws URISyntaxException, IOException, InterruptedException, ParseException {

        if (!queryParams.containsKey("game_id")) {
            throw new Error("Bad request, missing game_id");
        }
        if (!queryParams.containsKey("user_id")) {
            throw new Error("Bad request, missing user_id");
        }

        Integer gameId = Integer.valueOf(queryParams.get("game_id"));
        Integer userId = Integer.valueOf(queryParams.get("user_id"));
        String password = (queryParams.containsKey("password") && queryParams.get("password") != null) ? (String) queryParams.get("password") : null;

        String queryString = String.format("SELECT owner_id FROM games WHERE id = %d AND owner_id = %d AND game_password %s;",
                gameId, userId, password == null ? "IS NULL" : String.format("= '%s'", password));
        JSONArray ownerCheckRes = executeDbQuery(Arrays.asList(queryString));
        ArrayList<JSONObject> ownerCheckRows = (ArrayList) ownerCheckRes.get(0);

        if (ownerCheckRows.isEmpty()) {
            throw new Error("User is not the game owner or bad password provided");
        }

        ArrayList<String> queryStrings = new ArrayList<>();
        queryStrings.add(String.format("""
                                DELETE FROM games
                                WHERE id = %d;""", gameId)
        );
        queryStrings.add(String.format("""
                                DELETE FROM game_users
                                WHERE game_id = %d;""", gameId)
        );
        queryStrings.add(String.format("""
                                DELETE FROM game_teams
                                WHERE game_id = %d;""", gameId)
        );
        queryStrings.add(String.format("""
                                DROP TABLE tile_ownership_%d;""", gameId)
        );

        JSONArray queryResults = executeDbQuery(queryStrings);

        return queryResults;

    }

    @GetMapping("/user-games")
    public JSONArray ListUsersGames(@RequestParam Map<String, String> queryParams) throws URISyntaxException, IOException, InterruptedException, ParseException {

        if (!queryParams.containsKey("user_id")) {
            throw new Error("Bad request, missing user_id");
        }

        Integer userId = Integer.valueOf(queryParams.get("user_id"));

        // Check game_id & isTeamGame
        String queryString = String.format("""
                                SELECT u.game_id, g.name, g.owner_id, u.team, g.game_password
                                FROM game_users u 
                                LEFT JOIN games g ON u.game_id = g.id 
                                WHERE user_id = %d;""", userId);

        JSONArray queryResults = executeDbQuery(Arrays.asList(queryString));
        ArrayList<List<JSONObject>> userGameRows = (ArrayList) queryResults.get(0);

        JSONArray userGames = new JSONArray();

        for (List<JSONObject> gameRow : userGameRows) {

            Integer game_id = Integer.valueOf((String) gameRow.get(0).get("value"));
            String game_name = (String) gameRow.get(1).get("value");
            Integer owner_id = Integer.valueOf((String) gameRow.get(2).get("value"));

            JSONObject rowObject = new JSONObject();
            rowObject.put("game_id", game_id);
            rowObject.put("game_name", game_name);
            rowObject.put("owner_id", owner_id);
            if (gameRow.get(3) != null) {
                rowObject.put("team", gameRow.get(3).get("value"));
            }
            if (Objects.equals(owner_id, userId)) {
                rowObject.put("password", gameRow.get(4).get("value"));
            }
            userGames.add(rowObject.clone());
        }

        return userGames;
    }

    @GetMapping("/teams")
    public JSONArray ListGameTeams(@RequestParam Map<String, String> queryParams) throws URISyntaxException, IOException, InterruptedException, ParseException {

        Integer gameId = queryParams.containsKey("game_id") ? Integer.valueOf(queryParams.get("game_id")) : 1;

        // Check game_id & isTeamGame
        String queryString = String.format("SELECT team FROM game_teams WHERE game_id = %d;", gameId);
        JSONArray queryResults = executeDbQuery(Arrays.asList(queryString));
        ArrayList<List<JSONObject>> gameTeamRows = (ArrayList) queryResults.get(0);

        JSONArray gameTeams = new JSONArray();

        for (List<JSONObject> teamRow : gameTeamRows) {
            gameTeams.add(teamRow.get(0).get("value"));
        }

        return gameTeams;
    }

    @PostMapping("/add-teams")
    public String AddTeams(@RequestBody ArrayList<JSONObject> reqBody) throws URISyntaxException, IOException, InterruptedException, ParseException {
        for (JSONObject teamToAdd : reqBody) {
            if (!teamToAdd.containsKey("team")) {
                throw new Error("Bad request: missing team");
            }
            if (!teamToAdd.containsKey("game_id")) {
                throw new Error("Bad request: missing game_id");
            }

            Integer gameId = (int) teamToAdd.get("game_id");
            String team = (String) teamToAdd.get("team");

            // Check game_id & isTeamGame
            String queryString = String.format("SELECT teams FROM games WHERE id = %d;", gameId);
            JSONArray queryResults = executeDbQuery(Arrays.asList(queryString));
            JSONArray gameCheckResult = (JSONArray) queryResults.get(0);

            if (gameCheckResult.isEmpty()) {
                throw new Error("This game does not exist.");
            }

            // Check isTeamGame
            JSONArray gameCheckRow = (JSONArray) gameCheckResult.get(0);
            JSONObject teamObject = (JSONObject) gameCheckRow.get(0);
            Boolean isTeamGame = Integer.parseInt((String) teamObject.get("value")) == 1;

            if (!isTeamGame) {
                throw new Error("Team attempted to be added to non-team game");
            }

            // add team
            queryString = String.format("INSERT INTO game_teams (game_id,team) VALUES (%d,'%s');", gameId, team);
            executeDbQuery(Arrays.asList(queryString));

        }
        return "ok";
    }

    @PostMapping("/add-player")
    public ResponseEntity<String> AddPlayer(@RequestBody JSONObject reqBody) throws URISyntaxException, IOException, InterruptedException, ParseException {

        if (!reqBody.containsKey("user_id")) {
            throw new Error("Bad request: missing user_id");
        }

        Integer userId = (Integer) reqBody.get("user_id");
        Integer gameId = (reqBody.containsKey("game_id")) ? (int) reqBody.get("game_id") : 1;
        String team = (reqBody.containsKey("team") && reqBody.get("team") != null) ? (String) reqBody.get("team") : null;
        String password = (reqBody.containsKey("password") && reqBody.get("password") != null) ? (String) reqBody.get("password") : null;

        // Check game_id & isTeamGame
        String queryString = String.format("SELECT teams FROM games WHERE id = %d AND game_password %s;",
                gameId, password == null ? "IS NULL" : String.format("= '%s'", password));

        JSONArray queryResults = executeDbQuery(Arrays.asList(queryString));
        JSONArray gameCheckResult = (JSONArray) queryResults.get(0);

        if (gameCheckResult.isEmpty()) {
            return new ResponseEntity<>("This game does not exist or an incorrect password was given.", HttpStatus.NOT_FOUND);
        }

        // Check isTeamGame matches whether team specified
        JSONArray gameCheckRow = (JSONArray) gameCheckResult.get(0);
        JSONObject teamObject = (JSONObject) gameCheckRow.get(0);
        Boolean isTeamGame = Integer.parseInt((String) teamObject.get("value")) == 1;

        if (isTeamGame == (team == null)) {
            return new ResponseEntity<>(String.format("Gave team as %s when game teams setting is %b", (team == null) ? "null" : team, isTeamGame), HttpStatus.NOT_FOUND);
        }

        // if isTeamGame:
        if (isTeamGame) {
            //check if team exists
            queryString = String.format("SELECT * FROM game_teams WHERE game_id = %d AND team = '%s';", gameId, team);
            queryResults = executeDbQuery(Arrays.asList(queryString));
            JSONArray teamCheckRows = (JSONArray) queryResults.get(0);
            if (teamCheckRows.isEmpty()) {
                return new ResponseEntity<>("Specified team does not exist in this game", HttpStatus.NOT_FOUND);

            }
        }

        // add user
        queryString = (team == null)
                ? String.format("INSERT INTO game_users (user_id,game_id) VALUES (%d,%d);", userId, gameId)
                : String.format(
                        """
                        INSERT INTO game_users (user_id,game_id,team) 
                        VALUES (%d,%d,'%s') 
                        ON CONFLICT (user_id, game_id)
                        DO UPDATE SET team = EXCLUDED.team;
                        """, userId, gameId, team);

        executeDbQuery(Arrays.asList(queryString));
        return new ResponseEntity<>("ok", HttpStatus.OK);
    }

    @PostMapping("/create-game")
    public JSONObject CreateGame(@RequestBody JSONObject reqBody) throws URISyntaxException, IOException, InterruptedException, ParseException {
        // Create game if doesn't exist

        if (!reqBody.containsKey("name")) {
            throw new Error("Bad request: missing name");
        }
        if (!reqBody.containsKey("owner_id")) {
            throw new Error("Bad request: missing owner_id");
        }

        String gameName = (String) reqBody.get("name");
        Boolean isTeamGame = (reqBody.containsKey("teams") && reqBody.get("teams") != null) ? (boolean) reqBody.get("teams") : false;
        Integer ownerId = (Integer) reqBody.get("owner_id");

        // generate password
        Integer passwordLength = 10;
        String password = PasswordGenerator.generateRandomString(passwordLength);

        // Get games to work out lowest free game_id
        String queryString = """
                            SELECT id+1 as first_free_id FROM games WHERE id+1 NOT IN (SELECT id FROM games) LIMIT 1;
                            """;
        JSONArray querySetResults = executeDbQuery(Arrays.asList(queryString));
        JSONArray queryResults = (JSONArray) querySetResults.get(0);
        JSONArray row = (JSONArray) queryResults.get(0);
        JSONObject gameIdObject = (JSONObject) row.get(0);
        Integer gameId = Integer.valueOf((String) gameIdObject.get("value"));

        queryString = String.format("""
                                    INSERT INTO games (id, name, teams, owner_id, game_password)
                                    VALUES (%d,"%s",%b,%d,'%s');
                                    """, gameId, gameName, isTeamGame, ownerId, password);
        executeDbQuery(Arrays.asList(queryString));

        // CREATE TABLE
        List<String> queries = Arrays.asList(String.format("""
        CREATE TABLE IF NOT EXISTS tile_ownership_%d(
            tile_id text not null,
            x_index integer not null,
            y_index integer not null,
            user_id text not null,
            activity_id integer not null,
            created_at integer not null,
            PRIMARY KEY (tile_id)
        );
        """, gameId),
                String.format("""
        CREATE TRIGGER IF NOT EXISTS check_if_newer_capture_%d
        BEFORE UPDATE ON tile_ownership_%d
        FOR EACH ROW
        BEGIN
            SELECT RAISE(ABORT, 'Tile capture not newer than current owner')
            WHERE NOT(NEW.created_at >OLD.created_at);
        END;
        """, gameId, gameId));

        executeDbQuery(queries);

        JSONObject result = new JSONObject();
        result.put("game_id", gameId);
        result.put("password", password);

        return result;
    }

    @PostMapping("/rename-game")
    public String RenameGame(@RequestBody JSONObject reqBody) throws URISyntaxException, IOException, InterruptedException, ParseException {
        // Create game if doesn't exist

        if (!reqBody.containsKey("game_id")) {
            throw new Error("Bad request: missing game_id");
        }
        if (!reqBody.containsKey("name")) {
            throw new Error("Bad request: missing name");
        }

        String gameName = (String) reqBody.get("name");
        Integer gameId = (Integer) reqBody.get("game_id");
        String password = (String) reqBody.get("password");
        String queryString = String.format("""
                            UPDATE games SET name = "%s" WHERE id = %d AND game_password %s;
                            """, gameName, gameId, password == null ? "IS NULL" : String.format("= '%s'", password));
        executeDbQuery(Arrays.asList(queryString));

        return "ok";
    }

    @PostMapping("/process-activity")
    public String ProcessActivity(@RequestBody ProcessActivityReqBody reqBody) throws IOException, ParseException, URISyntaxException, InterruptedException {
        System.out.println("[START] /process-activity");
        Long userId = reqBody.getUserId();
        Long activityId = reqBody.getActivityId();
        List<List<Double>> coords = reqBody.getCoords();
        Long createdAt = reqBody.getCreatedAt();

        TileSet tileSet = new TileSet(coords);

        tileSet.writeRepresentation();

        HashSet<List<Integer>> tiles = tileSet.getSet();

        // GET USERS GAMES
        String queryString = String.format("SELECT game_id,team FROM game_users WHERE user_id=%d", userId);
        JSONArray queryResults = executeDbQuery(Arrays.asList(queryString));

        JSONArray gamesRows = (JSONArray) queryResults.get(0);

        System.out.println(userId.toString() + " IS IN GAMES " + gamesRows.toString());

        // INPUT VALUES
        StringBuilder records = new StringBuilder();
        tiles.forEach(tile -> {
            Integer x = tile.get(1);
            Integer y = tile.get(0);
            if (records.length() != 0) {
                records.append(",");
            }
            String recordString = String.format("('%s',%d,%d,%d,%d,%d)",
                    Integer.toString(x) + "," + Integer.toString(y), x, y, activityId, createdAt, userId);

            records.append(recordString);
        });

        for (int i = 0; i < gamesRows.size(); i++) {
            JSONArray row = (JSONArray) gamesRows.get(i);

            JSONObject gameIdObject = (JSONObject) row.get(0);
            JSONObject teamObject = (JSONObject) row.get(1);

            Integer gameId = Integer.valueOf((String) gameIdObject.get("value"));

            String insertRecords = records.toString();
            if (teamObject.containsKey("value")) {
                String team = (String) teamObject.get("value");

                StringBuilder teamRecords = new StringBuilder();

                String[] splitRecords = insertRecords.split("\\),?");
                // System.out.println("splitRecords" + Arrays.toString(splitRecords));
                for (String record : splitRecords) {
                    String[] splitRecord = record.split(",");
                    teamRecords.append(teamRecords.isEmpty() ? "" : ",")
                            .append(String.join(",", Arrays.copyOfRange(splitRecord, 0, splitRecord.length - 1)))
                            .append(",")
                            .append(String.format("'%s'", team))
                            .append(")");
                }
                insertRecords = teamRecords.toString();
            }

            queryString = String.format("INSERT INTO tile_ownership_%d"
                    + " (tile_id, x_index, y_index, activity_id, created_at,user_id)"
                    + " VALUES %s"
                    + " ON CONFLICT(tile_id) DO"
                    + " UPDATE SET"
                    + "   x_index = EXCLUDED.x_index,"
                    + "   y_index = EXCLUDED.y_index,"
                    + "   activity_id = EXCLUDED.activity_id,"
                    + "   created_at = EXCLUDED.created_at,"
                    + "   user_id = EXCLUDED.user_id"
                    + " WHERE EXCLUDED.created_at > tile_ownership_%d.created_at;;", gameId, insertRecords, gameId);
            try {
                executeDbQuery(Arrays.asList(queryString));
            } catch (IOException | InterruptedException | URISyntaxException e) {
                throw new Error(e);
            }
        }
        return "ok";

    }

    @GetMapping("/ping")
    public String Ping() {
        return "Service healthy!";
    }

    @GetMapping("/leaderboard")
    public JSONObject Leaderboard(@RequestParam Map<String, String> queryParams) throws IOException, URISyntaxException, InterruptedException, ParseException {

        Integer gameId = queryParams.containsKey("game_id") ? Integer.valueOf(queryParams.get("game_id")) : 1;
        Integer limit = queryParams.containsKey("limit") ? Integer.valueOf(queryParams.get("limit")) : 100;
        Integer offset = queryParams.containsKey("offset") ? Integer.valueOf(queryParams.get("offset")) : 0;
        String userId = queryParams.containsKey("user_id") ? (String) queryParams.get("user_id") : null;

        JSONObject res = new JSONObject();

        String gameTeamsQuery = String.format("""
            SELECT teams from games WHERE id = %d;
            """, gameId);
        JSONArray gameTeamsResults = executeDbQuery(Arrays.asList(gameTeamsQuery));
        JSONArray gameTeamsRows = (JSONArray) gameTeamsResults.get(0);
        JSONArray gameTeamsRow = (JSONArray) gameTeamsRows.get(0);
        JSONObject gameTeamsObject = (JSONObject) gameTeamsRow.get(0);
        Integer isTeamGame = Integer.valueOf((String) gameTeamsObject.get("value"));

        res.put("teams", isTeamGame);

        List<String> queryList = new ArrayList<>();

        String leaderboardQuery = String.format("""
        SELECT 
            DENSE_RANK () OVER(ORDER BY count(tile_id) DESC) as rank,  
            user_id, 
            COUNT(tile_id) as score 
        FROM tile_ownership_%d 
        GROUP BY user_id
        LIMIT %d
        OFFSET %d;
        """, gameId, limit, offset
        );
        queryList.add(leaderboardQuery);

        if (userId != null) {
            // Check if team game, if so, will need to query by team
            String userTeamQuery = String.format("""
            SELECT team from game_users WHERE user_id = "%s" AND game_id = %d;
            """, userId, gameId);
            JSONArray userTeamResult = executeDbQuery(Arrays.asList(userTeamQuery));
            JSONArray userTeamRows = (JSONArray) userTeamResult.get(0);

            String team = null;
            if (!userTeamRows.isEmpty()) {
                JSONArray userTeamRow = (JSONArray) userTeamRows.get(0);
                JSONObject userTeamObject = (JSONObject) userTeamRow.get(0);
                team = (String) userTeamObject.get("value");
            }
            String player = (team == null) ? userId : team;

            String userRankQuery = String.format("""
            SELECT * FROM (SELECT 
                DENSE_RANK () OVER(ORDER BY count(tile_id) DESC) as rank,  
                user_id, 
                COUNT(tile_id) as score 
            FROM tile_ownership_%d 
            GROUP BY user_id
            ) t1
            WHERE t1.user_id = "%s";
            """, gameId, player
            );
            queryList.add(userRankQuery);
        }

        // get json of results
        JSONArray queryResponses = executeDbQuery(queryList);

        JSONArray leaderboardRows = (JSONArray) queryResponses.get(0);

        List<Object> leaderboard = new ArrayList<>();

        for (int i = 0; i < leaderboardRows.size(); i++) {
            JSONArray row = (JSONArray) leaderboardRows.get(i);

            JSONObject rankObject = (JSONObject) row.get(0);
            JSONObject userIdObject = (JSONObject) row.get(1);
            JSONObject scoreObject = (JSONObject) row.get(2);

            JSONObject leaderboardEntry = new JSONObject();
            leaderboardEntry.putAll(Map.of("rank", Integer.valueOf((String) rankObject.get("value")),
                    "user_id", (String) userIdObject.get("value"),
                    "score", Integer.valueOf((String) scoreObject.get("value"))));

            leaderboard.add(leaderboardEntry.clone());
        }

        res.put("leaderboard", leaderboard);

        if (queryResponses.size() > 1) {
            // get rows of userId select query if included
            JSONArray userRows = (JSONArray) queryResponses.get(1);

            if (!userRows.isEmpty()) {

                JSONArray userRow = (JSONArray) userRows.get(0);

                JSONObject rankObject = (JSONObject) userRow.get(0);
                JSONObject userIdObject = (JSONObject) userRow.get(1);
                JSONObject scoreObject = (JSONObject) userRow.get(2);

                JSONObject userEntry = new JSONObject();
                userEntry.putAll(Map.of("rank", Integer.valueOf((String) rankObject.get("value")),
                        "user_id", (String) userIdObject.get("value"),
                        "score", Integer.valueOf((String) scoreObject.get("value"))));

                res.put("user", userEntry);
            }

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

        Integer gameId = queryParams.containsKey("game_id") ? Integer.valueOf(queryParams.get("game_id")) : 1;
        Double x1Param = Double.valueOf(queryParams.get("x1"));
        Double x2Param = Double.valueOf(queryParams.get("x2"));
        Double y1Param = Double.valueOf(queryParams.get("y1"));
        Double y2Param = Double.valueOf(queryParams.get("y2"));

        List<Integer> coord1 = TileSet.coordToTileIndexes(Arrays.asList(y1Param, x1Param));
        List<Integer> coord2 = TileSet.coordToTileIndexes(Arrays.asList(y2Param, x2Param));

        List<String> queryList = new ArrayList<>();

        String tileQueryString = String.format("""
                SELECT x_index, y_index, user_id, activity_id, created_at
                FROM tile_ownership_%d
                WHERE 
                (
                    (%d <= %d AND x_index BETWEEN %d AND %d) 
                    OR (%d > %d AND (x_index BETWEEN %d AND 360000 OR (x_index) BETWEEN 0 AND %d))
                ) 
                AND 
                (   
                    (%d <= %d AND y_index BETWEEN %d AND %d) 
                    OR (%d > %d AND (y_index BETWEEN %d AND 360000 OR (y_index) BETWEEN 0 AND %d))
                )
                ;
                """,
                gameId,
                coord1.get(1), coord2.get(1), coord1.get(1), coord2.get(1), // x_index logic part 1 (normal range)
                coord1.get(1), coord2.get(1), coord1.get(1), coord2.get(1), // x_index logic part 2 (wrap-around range)
                coord1.get(0), coord2.get(0), coord1.get(0), coord2.get(0), // y_index logic part 1 (normal range)
                coord1.get(0), coord2.get(0), coord1.get(0), coord2.get(0) // y_index logic part 2 (wrap-around range)
        );

        queryList.add(tileQueryString);

        // get json of results
        JSONArray queryResponses = executeDbQuery(queryList);

        JSONArray tileQueryRows = (JSONArray) queryResponses.get(0);

        List<Object> tiles = new ArrayList<>();

        for (int i = 0; i < tileQueryRows.size(); i++) {
            JSONArray row = (JSONArray) tileQueryRows.get(i);

            JSONObject xObject = (JSONObject) row.get(0);
            JSONObject yObject = (JSONObject) row.get(1);
            JSONObject userIdObject = (JSONObject) row.get(2);
            JSONObject activityIdObject = (JSONObject) row.get(3);
            JSONObject createdAtObject = (JSONObject) row.get(4);

            JSONObject tilesEntry = new JSONObject();
            tilesEntry.putAll(Map.of(
                    "x_index", Long.valueOf((String) xObject.get("value")),
                    "y_index", Long.valueOf((String) yObject.get("value")),
                    "user_id", (String) userIdObject.get("value"),
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

    private JSONArray executeDbQuery(List<String> queryStrings) throws URISyntaxException, IOException, InterruptedException, ParseException {
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

        JSONParser jsonParser = new JSONParser();
        JSONObject querySetResponseBody = (JSONObject) jsonParser.parse(response.body());
        ArrayList querySetResultsArray = (ArrayList) querySetResponseBody.get("results");

        // remove db close result
        List querySetResultsArrayWithOutClose = querySetResultsArray.subList(0, querySetResultsArray.size() - 1);

        JSONArray formattedQueryResponses = new JSONArray();

        for (Object result : querySetResultsArrayWithOutClose) {
            JSONObject resultJSON = (JSONObject) result;
            JSONObject responseJSON = (JSONObject) resultJSON.get("response");
            if (responseJSON != null) {
                JSONObject resultObjectJSON = (JSONObject) responseJSON.get("result");
                JSONArray rowsArray = (JSONArray) resultObjectJSON.get("rows");
                formattedQueryResponses.add(rowsArray);
            }
        }

        return formattedQueryResponses;
    }

}
