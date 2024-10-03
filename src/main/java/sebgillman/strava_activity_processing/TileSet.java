package sebgillman.strava_activity_processing;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public final class TileSet {

    private static final Integer TILE_SIZE = 100;
    private static final double EARTH_RADIUS = 6371000;

    private final HashSet<List<Integer>> set;

    // constructor takes geographic coords and adds to set
    public TileSet(List<List<Double>> coords) {

        HashSet<List<Integer>> routeTiles = new HashSet<>();
        for (int i = 0; i < coords.size() - 1; i++) {

            // convert to tile indexes
            List<Integer> startTile = coordToTileIndexes(coords.get(i));
            List<Integer> endTile = coordToTileIndexes(coords.get(i + 1));

            // interpolate to get all tiles in line segment
            List<List<Integer>> segmentTiles = interpolateTiles(startTile, endTile);

            for (List<Integer> tile : segmentTiles) {
                routeTiles.add(tile);
            }

            // get just the outline of the route
        }
        set = getOutline(routeTiles);

    }
            }
        }
    private HashSet<List<Integer>> getOutline(HashSet<List<Integer>> filledTiles) {
        HashSet<List<Integer>> outline = new HashSet<>();
        HashSet<Edge> visitedEdges = new HashSet<>();

        // Directions for Moore's Neighbor (clockwise starting from left) and corresponding edges
        int[][] directions = {
            {-1, 0}, // Left
            {-1, 1}, // Top-left
            {0, 1}, // Top
            {1, 1}, // Top-right
            {1, 0}, // Right
            {1, -1}, // Bottom-right
            {0, -1}, // Bottom
            {-1, -1} // Bottom-left
        };

        // Starting tile for edge tracking
        List<Integer> startTile = filledTiles.iterator().next(); // Pick any filled tile
        List<Integer> currentTile = startTile;

        // Store the current direction
        int lastDirection = 0;

        // Track edges instead of just tiles
        do {
            outline.add(currentTile);

            // Traverse edges to find the next boundary tile
            boolean foundNext = false;
            for (int i = 0; i < directions.length; i++) {
                int dirIndex = (lastDirection + i) % directions.length;
                int[] direction = directions[dirIndex];

                List<Integer> neighbor = Arrays.asList(
                        currentTile.get(0) + direction[0],
                        currentTile.get(1) + direction[1]
                );

                // Define the edge being traversed (from current tile to the neighbor)
                Edge currentEdge = new Edge(currentTile, neighbor);

                // If the neighbor is a boundary tile and the edge hasn't been visited
                if (filledTiles.contains(neighbor) && !visitedEdges.contains(currentEdge)) {
                    // Mark the edge as visited
                    visitedEdges.add(currentEdge);
                    currentTile = neighbor;
                    lastDirection = (dirIndex + 5) % 8;  // Reverse direction
                    foundNext = true;
                    break;
                }
            }

            // If no neighbor is found, break (edge complete)
            if (!foundNext) {
                break;
            }

        } while (!currentTile.equals(startTile));  // Stop when we return to the start

        return outline;
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

// Helper class to represent a directed edge between two tiles
class Edge {

    List<Integer> from;
    List<Integer> to;

    Edge(List<Integer> from, List<Integer> to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Edge)) {
            return false;
        }
        Edge edge = (Edge) o;
        return (from.equals(edge.from) && to.equals(edge.to)); // Treat undirected edges as equal

    }

    @Override
    public int hashCode() {
        return from.hashCode() + to.hashCode();
    }
}
