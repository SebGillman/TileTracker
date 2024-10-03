package sebgillman.strava_activity_processing;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class TileSet {

    private static final Integer TILE_SIZE = 100;
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

            for (List<Integer> tile : segmentTiles) {
                set.add(tile);
            }
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

    public void writeRepresentation() throws IOException {
        List<List<Integer>> tileList = new ArrayList<>(set);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        // Calculate bounds for the grid
        for (List<Integer> tile : tileList) {
            minX = Math.min(minX, tile.get(0));
            maxX = Math.max(maxX, tile.get(0));
            minY = Math.min(minY, tile.get(1));
            maxY = Math.max(maxY, tile.get(1));
        }

        // Use StringBuilder for efficient appending
        StringBuilder out = new StringBuilder();

        // Iterate through the range and build the representation
        for (int y = maxY + 1; y >= minY - 1; y--) {
            for (int x = minX - 1; x <= maxX + 1; x++) {
                // Instead of creating a list for lookup, use a custom key or data structure
                if (set.contains(Arrays.asList(x, y))) {
                    out.append("@");
                } else {
                    out.append(" ");
                }
            }
            out.append("\n");  // New line after each row
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("outputVisualisation.txt"))) {
            writer.write(out.toString());
            writer.close();
        }
    }

    public HashSet<List<Integer>> getSet() {
        return set;
    }

    public void addTile(List<Integer> tile) {
        this.set.add(tile);
    }

}
