package sebgillman.strava_activity_processing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class TileSet {

    private static final Integer TILE_SIZE = 3;
    private static final double EARTH_RADIUS = 6371000;

    private final HashSet<List<Integer>> set = new HashSet<>();

    // constructor takes geographic coords and adds to set
    public TileSet(List<List<Double>> coords) {

        for (int i = 0; i < coords.size() - 1; i++) {

            // convert to tile indexes
            List<Integer> startTile = coordToTileIndexes(coords.get(i));
            List<Integer> endTile = coordToTileIndexes(coords.get(i + 1));

            // interpolate to get all tiles in line segment
            List<List<Integer>> segmentTiles = interpolateTiles(startTile, endTile);

            segmentTiles.forEach(tile -> set.add(tile));
        }
    }

    private List<List<Integer>> interpolateTiles(List<Integer> startTile, List<Integer> endTile) {

        List<List<Integer>> tiles = new ArrayList<>();

        int x0 = startTile.get(0);
        int y0 = startTile.get(1);
        int x1 = endTile.get(0);
        int y1 = endTile.get(1);

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            tiles.add(Arrays.asList(x0, y0));

            if (x0 == x1 && y0 == y1) {
                break;
            }

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
        return tiles;
    }

    private List<Integer> coordToTileIndexes(List<Double> coord) {
        double latRad = Math.toRadians(coord.get(0));
        double lonRad = Math.toRadians(coord.get(1));

        double x = EARTH_RADIUS * lonRad * Math.cos(latRad);  // longitude -> x coordinate
        double y = EARTH_RADIUS * latRad;                     // latitude -> y coordinate

        // Convert to tile indices
        int tileX = (int) (x / TILE_SIZE);
        int tileY = (int) (y / TILE_SIZE);

        return Arrays.asList(tileX, tileY);
    }

    public HashSet<List<Integer>> getSet() {
        return set;
    }

    public void addTile(List<Integer> tile) {
        this.set.add(tile);
    }

}
