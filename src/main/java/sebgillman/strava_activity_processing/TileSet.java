package sebgillman.strava_activity_processing;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class TileSet {

    // private static final Double TILE_SIZE_DEGREES = 0.0005;
    private static final Double TILE_SIZE_DEGREES = 0.001;

    private final HashSet<List<Integer>> set;

    // constructor takes geographic coords and adds to set
    public TileSet(List<List<Double>> coords) {

        if (coords.isEmpty()) {
            set = new HashSet<>();
            return;
        }

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

            // get just the outlineFrequencies of the route
        }

        HashMap<List<Integer>, Integer> outline = getOutline(routeTiles);

        set = new HashSet<>(outline.keySet());
        fillOutline(outline);
    }

    private void fillOutline(HashMap<List<Integer>, Integer> outline) {

        // get bounding box of the outlineFrequencies
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (List<Integer> tile : set) {
            int tX = tile.get(1);
            int tY = tile.get(0);
            minX = Math.min(minX, tX);
            maxX = Math.max(maxX, tX);
            minY = Math.min(minY, tY);
            maxY = Math.max(maxY, tY);
        }

        boolean tileAdded;
        do {
            tileAdded = false;
            // keep a set of tiles to add to this.set as adding them in the iteration messes up isInside()
            HashSet<List<Integer>> toAddList = new HashSet<>();
            HashSet<List<Integer>> visitedSet = new HashSet<>();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    List<Integer> currentTile = Arrays.asList(y, x);
                    // if already visited or not inside outlineFrequencies, skip
                    if (set.contains(currentTile) || toAddList.contains(currentTile) || visitedSet.contains(currentTile) || !isInside(currentTile, outline)) {
                        continue;
                    }
                    FloodFillOutput floodFillOutput = floodFill(currentTile);
                    if (floodFillOutput.isInside()) {
                        tileAdded = true;
                        toAddList.addAll(floodFillOutput.getTiles());
                    }
                    visitedSet.addAll(floodFillOutput.getTiles());
                }
            }
            set.addAll(toAddList);
        } while (tileAdded);
    }

    private FloodFillOutput floodFill(List<Integer> startTile) {

        boolean inside = true;

        // get bounding box of the outlineFrequencies
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (List<Integer> tile : set) {
            int tX = tile.get(1);
            int tY = tile.get(0);
            minX = Math.min(minX, tX);
            maxX = Math.max(maxX, tX);
            minY = Math.min(minY, tY);
            maxY = Math.max(maxY, tY);
        }

        List<List<Integer>> filledTiles = new ArrayList<>();

        int[][] directions = {
            {1, 0}, // Right
            {0, 1}, // Up
            {-1, 0}, // Left
            {0, -1}, // Down
        };

        List<List<Integer>> queue = new ArrayList<>();
        queue.add(startTile);

        // BFS
        while (!queue.isEmpty()) {

            List<Integer> currentTile = queue.remove(0);
            int cX = currentTile.get(1), cY = currentTile.get(0);

            filledTiles.add(currentTile);

            for (int[] direction : directions) {
                int nX = cX + direction[1];
                int nY = cY + direction[0];

                List<Integer> candidateTile = Arrays.asList(nY, nX);
                if (queue.contains(candidateTile) || filledTiles.contains(candidateTile) || set.contains(candidateTile)) {
                    continue;
                }
                if (nX == minX || nX == maxX || nY == minY || nY == maxY) {
                    inside = false;
                    continue;
                }
                queue.add(candidateTile);
            }
        }
        return new FloodFillOutput(inside, filledTiles);
    }

// BUG: if a rayline brushes an edge without crossing it then fails
    private boolean isInside(List<Integer> candidateTile, HashMap<List<Integer>, Integer> outline) {

        // 4-axis ray casting to check if odd number of edge crossings in each axis
        int posX = 0, posY = 0, negX = 0, negY = 0;

        int candidateX = candidateTile.get(1);
        int candidateY = candidateTile.get(0);

        HashSet<List<Integer>> visitedTiles = new HashSet<>();

        for (List<Integer> tile : set) {
            int cx = tile.get(1);
            int cy = tile.get(0);

            if (tile == candidateTile) {
                return false;
            }

            if (cx != candidateX && cy != candidateY) {
                continue;
            }

            if (visitedTiles.contains(tile)) {
                continue;
            }

            List<List<Integer>> currentEdgeTiles = new ArrayList<>();

            int dx = (cx == candidateX) ? 0 : 1;
            int dy = (cy == candidateY) ? 0 : 1;

            int incrX = cx;
            int incrY = cy;
            int currEdgeMaxFreq = outline.getOrDefault(tile, 1);

            while (set.contains(Arrays.asList(incrY, incrX))) {
                List<Integer> currTile = Arrays.asList(incrY, incrX);
                currEdgeMaxFreq = Math.max(currEdgeMaxFreq, outline.getOrDefault(currTile, 1));
                currentEdgeTiles.add(currTile);
                incrX += dx;
                incrY += dy;
            }

            int decrX = cx;
            int decrY = cy;

            while (set.contains(Arrays.asList(decrY, decrX))) {
                List<Integer> currTile = Arrays.asList(decrY, decrX);
                currEdgeMaxFreq = Math.max(currEdgeMaxFreq, outline.getOrDefault(currTile, 1));
                currentEdgeTiles.add(currTile);
                decrX -= dx;
                decrY -= dy;
            }

            visitedTiles.addAll(currentEdgeTiles);

            if (cx == candidateX) {

                if (cy > candidateY) {
                    posY += currEdgeMaxFreq;
                } else {
                    negY += currEdgeMaxFreq;
                }
            } else if (cy == candidateY) {

                if (cx > candidateX) {
                    posX += currEdgeMaxFreq;
                } else {
                    negX += currEdgeMaxFreq;
                }
            }
        }
        return (posX % 2 == 1) && (negX % 2 == 1) && (posY % 2 == 1) && (negY % 2 == 1);

    }

    private HashMap<List<Integer>, Integer> getOutline(HashSet<List<Integer>> filledTiles) {
        HashMap<List<Integer>, Integer> outlineFrequencies = new HashMap<>();
        HashSet<Edge> visitedEdges = new HashSet<>();

        // Directions for Moore's Neighbor (clockwise starting from left) and corresponding edges
        int[][] directions = {
            {0, -1}, // Left
            {1, -1}, // Top-left
            {1, 0}, // Top
            {1, 1}, // Top-right
            {0, 1}, // Right
            {-1, 1}, // Bottom-right
            {-1, 0}, // Bottom
            {-1, -1} // Bottom-left
        };

        // Starting tile for edge tracking with left-most tile of top-most row
        List<List<Integer>> filledTileList = new ArrayList<>(filledTiles); // Pick any filled tile

        int startIndex = 0;
        int maxY = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;

        for (int i = 0; i < filledTileList.size(); i++) {
            int cY = filledTileList.get(i).get(0);
            int cX = filledTileList.get(i).get(1);
            if (cY > maxY || (cY == maxY && cX < minX)) {
                maxY = cY;
                minX = cX;
                startIndex = i;
            }
        }

        List<Integer> startTile = filledTileList.get(startIndex);
        List<Integer> currentTile = startTile;

        // Store the current direction
        int lastDirection = 4;

        // Track edges instead of just tiles
        boolean foundNext = true;
        boolean edgeRevisited = false;
        do {
            outlineFrequencies.put(currentTile, 1 + outlineFrequencies.getOrDefault(currentTile, 0));
            // Traverse edges to find the next boundary tile
            foundNext = false;
            edgeRevisited = false;
            for (int i = 0; i < directions.length; i++) {
                int dirIndex = (lastDirection + i) % directions.length;
                int[] direction = directions[dirIndex];

                List<Integer> neighbor = Arrays.asList(
                        currentTile.get(0) + direction[0],
                        currentTile.get(1) + direction[1]
                );

                // Define the edge being traversed (from current tile to the neighbor)
                Edge currentEdge = new Edge(currentTile, neighbor);

                // if a revisited edge found
                if (visitedEdges.contains(currentEdge)) {
                    edgeRevisited = true;
                    break;
                }

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

        } while (foundNext && !edgeRevisited);  // Stop when we return to the start

        return outlineFrequencies;
    }

    private List<List<Integer>> interpolateTiles(List<Integer> startTile, List<Integer> endTile) {

        List<List<Integer>> tiles = new ArrayList<>();

        int x0 = startTile.get(1);
        int y0 = startTile.get(0);
        int x1 = endTile.get(1);
        int y1 = endTile.get(0);

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            tiles.add(Arrays.asList(y0, x0));

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

    public static List<Integer> coordToTileIndexes(List<Double> coord) {
        double longitude = coord.get(1);
        double latitude = coord.get(0);

        // Normalize latitude to be in the range [0, 360)
        if (longitude < 0) {
            longitude += 360;
        }

        // Calculate tile indices based on the tile size
        int tileY = (int) Math.floor(latitude / TILE_SIZE_DEGREES);
        int tileX = (int) Math.floor(longitude / TILE_SIZE_DEGREES); // 90 to flip the y-axis

        return Arrays.asList(tileY, tileX);
    }

    public void writeRepresentation() throws IOException {
        List<List<Integer>> tileList = new ArrayList<>(set);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        // Calculate bounds for the grid
        for (List<Integer> tile : tileList) {
            minX = Math.min(minX, tile.get(1));
            maxX = Math.max(maxX, tile.get(1));
            minY = Math.min(minY, tile.get(0));
            maxY = Math.max(maxY, tile.get(0));
        }

        // Use StringBuilder for efficient appending
        StringBuilder out = new StringBuilder();

        // Iterate through the range and build the representation
        for (int y = maxY + 1; y >= minY - 1; y--) {
            for (int x = minX - 1; x <= maxX + 1; x++) {
                // Instead of creating a list for lookup, use a custom key or data structure
                if (set.contains(Arrays.asList(y, x))) {
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

class FloodFillOutput {

    boolean inside;
    List<List<Integer>> tiles;

    public FloodFillOutput(boolean inside, List<List<Integer>> tiles) {
        this.inside = inside;
        this.tiles = tiles;
    }

    public boolean isInside() {
        return inside;
    }

    public void setInside(boolean inside) {
        this.inside = inside;
    }

    public List<List<Integer>> getTiles() {
        return tiles;
    }

    public void setTiles(List<List<Integer>> tiles) {
        this.tiles = tiles;
    }
}
