import javafx.util.Pair;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This class provides all code necessary to take a query box and produce
 * a query result. The getMapRaster method must return a Map containing all
 * seven of the required fields, otherwise the front end code will probably
 * not draw the output correctly.
 */
public class Rasterer {

    private static DecimalFormat df = new DecimalFormat(".##############");
    private int maxDepth = 7;
    private int imageWidth = 256;
    private double[] lonDpp;
    private Tile root;

    public Rasterer() {
        calculateLonDpp();
        root = new Tile(0, 0, 0, MapServer.ROOT_ULLON, MapServer.ROOT_ULLAT,
                MapServer.ROOT_LRLON, MapServer.ROOT_LRLAT,
                (MapServer.ROOT_LRLON - MapServer.ROOT_ULLON) / 256);
    }

    private void calculateLonDpp() {
        lonDpp = new double[maxDepth + 1];
        lonDpp[0] = (MapServer.ROOT_LRLON - MapServer.ROOT_ULLON) / imageWidth;
        for (int i = 1; i < maxDepth + 1; i++) {
            lonDpp[i] = lonDpp[i - 1] / 2;
        }
    }

    private Tile zoom(double dppQuery, double ullonQuery, double ullatQuery, double lrlonQuery, double lrlatQuery) {
        Tile zoomed = root;
        int count = 0;
        double lonMid = half(lrlonQuery, ullonQuery);
        double latMid = half(lrlatQuery, ullatQuery);
        while (!zoomed.inResolution(dppQuery) || !zoomed.inTile(lonMid, latMid)) {
            count++;
            zoomed = zoomed.nextQuad(lonMid, latMid);
            System.out.println(zoomed.depth);
            if (count > 100) {
                return null;
            }
        }
        return zoomed;
    }

    private double half(double a, double b) {
        return (a + b) / 2;
    }

    private int gridSize(int depth) {
        return (int) Math.sqrt(Math.pow(4, depth));
    }

    private double gridLonConstant(int gridSize) {
        return (MapServer.ROOT_LRLON - MapServer.ROOT_ULLON) / gridSize;
    }

    private double gridLatConstant(int gridSize) {
        return (MapServer.ROOT_ULLAT - MapServer.ROOT_LRLAT) / gridSize;
    }

    private Pair<Integer, Double> rasterLeftToRight(int gridSize, double gridConstant, Map<String, Double> queryBox) {
        int x1 = 0;
        double queryLeftLon = queryBox.get("ullon");
        double leftLon = MapServer.ROOT_ULLON;
        boolean flag = false;
        for (int i = 0; i < gridSize; i++) {
            if (leftLon < queryLeftLon) {
                flag = true;
                leftLon += gridConstant;
                x1 = i;
            } else {
                break;
            }
        }
        if (flag) {
            leftLon -= gridConstant;
        }
        return new Pair<>(x1, leftLon);
    }

    private Pair<Integer, Double> rasterRightToLeft(int gridSize, double gridConstant, Map<String, Double> queryBox) {
        int x2 = gridSize - 1;
        double queryRightLon = queryBox.get("lrlon");
        double rightLon = MapServer.ROOT_LRLON;
        boolean flag = false;
        for (int i = gridSize - 1; i >= 0; i--) {
            if (rightLon > queryRightLon) {
                flag = true;
                rightLon -= gridConstant;
                x2 = i;
            } else {
                break;
            }
        }
        if (flag) {
            rightLon += gridConstant;
        }
        return new Pair<>(x2, rightLon);
    }

    private Pair<Integer, Double> rasterTopToBottom(int gridSize, double gridConstant, Map<String, Double> queryBox) {
        int y1 = 0;
        double queryUpperLat = queryBox.get("ullat");
        double upperLat = MapServer.ROOT_ULLAT;
        boolean flag = false;
        for (int i = 0; i < gridSize; i++) {
            if (upperLat > queryUpperLat) {
                flag = true;
                upperLat -= gridConstant;
                y1 = i;
            } else {
                break;
            }
        }
        if (flag) {
            upperLat += gridConstant;
        }
        return new Pair<>(y1, upperLat);
    }

    private Pair<Integer, Double> rasterBottomToTop(int gridSize, double gridConstant, Map<String, Double> queryBox) {
        int y2 = gridSize - 1;
        double queryLowerLat = queryBox.get("lrlat");
        double lowerLat = MapServer.ROOT_LRLAT;
        boolean flag = false;
        for (int i = gridSize - 1; i >= 0; i--) {
            if (lowerLat < queryLowerLat) {
                flag = true;
                lowerLat += gridConstant;
                y2 = i;
            } else {
                break;
            }
        }
        if (flag) {
            lowerLat -= gridConstant;
        }
        return new Pair<>(y2, lowerLat);
    }

    private String[][] getRenderGrid(int x1, int y1, int x2, int y2, int depth) {
        String[][] renderGrid = new String[y2 - y1 + 1][x2 - x1 + 1];
        for (int i = y1, i1 = 0; i <= y2 || i1 < y2 - y1; i++, i1++) {
            for (int j = x1, j1 = 0; j <= x2 || j1 < x2 - x1; j++, j1++) {
                renderGrid[i1][j1] = String.format("d%d_x%d_y%d.png", depth, j, i);
            }
        }
        return renderGrid;
    }

    /**
     * Takes a user query and finds the grid of images that best matches the query. These
     * images will be combined into one big image (rastered) by the front end. <br>
     * <p>
     * The grid of images must obey the following properties, where image in the
     * grid is referred to as a "tile".
     * <ul>
     * <li>The tiles collected must cover the most longitudinal distance per pixel
     * (LonDPP) possible, while still covering less than or equal to the amount of
     * longitudinal distance per pixel in the query box for the user viewport size. </li>
     * <li>Contains all tiles that intersect the query bounding box that fulfill the
     * above condition.</li>
     * <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     * </ul>
     * </p>
     *
     * @param params Map of the HTTP GET request's query parameters - the query box and
     *               the user viewport width and height.
     * @return A map of results for the front end as specified:
     * "render_grid"   -> String[][], the files to display
     * "raster_ul_lon" -> Number, the bounding upper left longitude of the rastered image <br>
     * "raster_ul_lat" -> Number, the bounding upper left latitude of the rastered image <br>
     * "raster_lr_lon" -> Number, the bounding lower right longitude of the rastered image <br>
     * "raster_lr_lat" -> Number, the bounding lower right latitude of the rastered image <br>
     * "depth"         -> Number, the depth of the nodes of the rastered image.
     * Can also be interpreted as the length of the numbers in the image
     * string. <br>
     * "query_success" -> Boolean, whether the query was able to successfully complete. Don't
     * forget to set this to true! <br>
     */
    public Map<String, Object> getMapRaster(Map<String, Double> params) {
        Map<String, Object> results = new HashMap<>();
        if (params.get("ullon") > MapServer.ROOT_LRLON
                || params.get("lrlon") < MapServer.ROOT_ULLON
                || params.get("lrlat") > MapServer.ROOT_ULLAT
                || params.get("ullat") < MapServer.ROOT_LRLAT) {
            results.put("query_success", false);
            return queryFailedResults(results, params);
        }

        double qDPP = (params.get("lrlon") - params.get("ullon")) / params.get("w");
        Tile starter = zoom(qDPP, params.get("ullon"), params.get("ullat"), params.get("lrlon"), params.get("lrlat"));
        if (starter == null) {
            return queryFailedResults(results, params);
        }

        System.out.println("Zoomed depth" + String.valueOf(starter.depth));

        System.out.println("Params " + params);

        int gridSize = gridSize(starter.depth);
        System.out.println("Grid size " + String.valueOf(gridSize));

        double gridLonConstant = gridLonConstant(gridSize);
        System.out.println("Grid Lon constant " + String.valueOf(gridLonConstant));

        double gridLatConstant = gridLatConstant(gridSize);
        System.out.println("Grid Lat constant " + String.valueOf(gridLatConstant));

        Pair<Integer, Double> leftToRight = rasterLeftToRight(gridSize, gridLonConstant, params);
        int x1 = leftToRight.getKey();
        double leftLon = leftToRight.getValue();
        System.out.println("x1 " + String.valueOf(x1));
        System.out.println("ullon " + String.valueOf(leftLon));

        Pair<Integer, Double> rightToLeft = rasterRightToLeft(gridSize, gridLonConstant, params);
        int x2 = rightToLeft.getKey();
        double rightLon = rightToLeft.getValue();
        System.out.println("x2 " + String.valueOf(x2));
        System.out.println("lrlon " + String.valueOf(rightLon));

        Pair<Integer, Double> topToBottom = rasterTopToBottom(gridSize, gridLatConstant, params);
        int y1 = topToBottom.getKey();
        double upperLat = topToBottom.getValue();
        System.out.println("y1 " + String.valueOf(y1));
        System.out.println("ullat " + String.valueOf(upperLat));

        Pair<Integer, Double> bottomToTop = rasterBottomToTop(gridSize, gridLatConstant, params);
        int y2 = bottomToTop.getKey();
        double lowerLat = bottomToTop.getValue();
        System.out.println("y2 " + String.valueOf(y2));
        System.out.println("lrlat " + String.valueOf(lowerLat));

        String[][] renderGrid = getRenderGrid(x1, y1, x2, y2, starter.depth);
        System.out.println(Arrays.deepToString(renderGrid));

        results.put("raster_ul_lon", leftLon);
        results.put("raster_lr_lon", rightLon);
        results.put("raster_lr_lat", lowerLat);
        results.put("raster_ul_lat", upperLat);
        results.put("depth", starter.depth);
        results.put("render_grid", renderGrid);
        results.put("query_success", true);

        return results;
    }

    private Map<String, Object> queryFailedResults(Map<String, Object> results, Map<String, Double> params) {
        results.put("raster_ul_lon", params.get("ullon"));
        results.put("raster_lr_lon", params.get("lrlon"));
        results.put("raster_lr_lat", params.get("lrlat"));
        results.put("raster_ul_lat", params.get("ullat"));
        results.put("depth", 0);
        results.put("render_grid", new String[0][0]);
        results.put("query_success", false);
        return results;
    }

    class Tile {
        int x, y;
        int depth;
        double ulLon, ulLat, lrLon, lrLat, lonDpp;
        String imgFile;

        Tile(int x, int y, int d, double ullon, double ullat, double lrlon, double lrlat, double ldpp) {
            depth = d;
            ulLon = ullon;
            ulLat = ullat;
            lrLon = lrlon;
            lrLat = lrlat;
            lonDpp = ldpp;
            imgFile = String.format("d%d_x%d_y%d.png", depth, x, y);
        }

        boolean inResolution(double maxDPP) {
            System.out.println(String.format("lonDpp for depth %d is %s", depth, Double.toString(lonDpp)));
            return (lonDpp <= maxDPP) || (depth == 7);
        }

        boolean inTile(double lon, double lat) {
            return (ulLon < lon) && (lrLon > lon) && (ulLat > lat) && (lrLat < lat);
        }

        Tile nextQuad(double lon, double lat) {
            Tile childNW = northWestChild();
            Tile childNE = northEastChild();
            Tile childSE = southEastChild();
            Tile childSW = southWestChild();
            if (childNW.inTile(lon, lat)) {
                System.out.println("north west in tile");
                return childNW;
            } else if (childNE.inTile(lon, lat)) {
                System.out.println("north east in tile");
                return childNE;
            } else if (childSE.inTile(lon, lat)) {
                System.out.println("south east in tile");
                return childSE;
            } else if (childSW.inTile(lon, lat)) {
                System.out.println("south west in tile");
                return childSW;
            }
            return this;
        }

        Tile northWestChild() {
            return new Tile(x, y, depth + 1, ulLon, ulLat, half(ulLon, lrLon),
                    half(ulLat, lrLat), lonDpp / 2);
        }

        Tile northEastChild() {
            return new Tile(x+1, y,depth + 1, half(ulLon, lrLon),
                    ulLat, lrLon, half(ulLat, lrLat), lonDpp / 2);
        }

        Tile southWestChild() {
            return new Tile(x, y+1, depth + 1, ulLon, half(ulLat, lrLat),
                    half(ulLon, lrLon), lrLat, lonDpp / 2);
        }

        Tile southEastChild() {
            return new Tile(x+1, y+1, depth + 1, half(ulLon, lrLon),
                    half(ulLat, lrLat), lrLon, lrLat, lonDpp / 2);
        }
    }
}
