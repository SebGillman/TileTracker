package sebgillman.strava_activity_processing;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class TileSet {

    private static final Integer TILE_SIZE = 3;
    private static final double EARTH_RADIUS = 6371000;

    private final HashSet<List<Number>> set = new HashSet<>();

    // constructor takes geographic coords and adds to set
    public TileSet(List<List<Double>> coords) {
        for (List<Double> coord : coords) {
            this.set.add(CoordToTileIndexes(coord));
        }
    }

    private List<Number> CoordToTileIndexes(List<Double> coord) {
        double latRad = Math.toRadians(coord.get(0));
        double lonRad = Math.toRadians(coord.get(1));

        double x = EARTH_RADIUS * lonRad * Math.cos(latRad);  // longitude -> x coordinate
        double y = EARTH_RADIUS * latRad;                     // latitude -> y coordinate

        // Convert to tile indices
        int tileX = (int) (x / TILE_SIZE);
        int tileY = (int) (y / TILE_SIZE);

        return Arrays.asList(tileX, tileY);
    }

    public HashSet<List<Number>> getSet() {
        return set;
    }

    public void addTile(List<Number> tile) {
        this.set.add(tile);
    }

}
