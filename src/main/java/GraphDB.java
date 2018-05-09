import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Graph for storing all of the intersection (vertex) and road (edge) information.
 * Uses your GraphBuildingHandler to convert the XML files into a graph. Your
 * code must include the vertices, adjacent, distance, closest, lat, and lon
 * methods. You'll also need to include instance variables and methods for
 * modifying the graph (e.g. addNode and addEdge).
 *
 * @author Alan Yao, Josh Hug
 */
public class GraphDB {
    HashMap<Long, Vertex> vertexRefs = new HashMap<>();
    HashSet<Long> cleanThese = new HashSet<>();
    Way nextWay = null;
    private Tries locTrie = new Tries();
    /** Your instance variables for storing the graph. You should consider
     * creating helper classes, e.g. Node, Edge, etc. */

    /**
     * Example constructor shows how to create and start an XML parser.
     * You do not need to modify this constructor, but you're welcome to do so.
     * @param dbPath Path to the XML file to be parsed.
     */
    public GraphDB(String dbPath) {
        try {
            File inputFile = new File(dbPath);
            FileInputStream inputStream = new FileInputStream(inputFile);
            // GZIPInputStream stream = new GZIPInputStream(inputStream);

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            GraphBuildingHandler gbh = new GraphBuildingHandler(this);
            saxParser.parse(inputStream, gbh);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
//        clean();
    }

    /**
     * Helper to process strings into their "cleaned" form, ignoring punctuation and capitalization.
     * @param s Input string.
     * @return Cleaned string.
     */
    static String cleanString(String s) {
        return s.replaceAll("[^a-zA-Z ]", "").toLowerCase();
    }

    /**
     *  Remove nodes with no connections from the graph.
     *  While this does not guarantee that any two nodes in the remaining graph are connected,
     *  we can reasonably assume this since typically roads are connected.
     */
    private void clean() {
        for (long v : cleanThese) {
            vertexRefs.remove(v);
        }
        cleanThese.clear();
    }

    /** Returns an iterable of all vertex IDs in the graph. */
    Iterable<Long> vertices() {
        return vertexRefs.keySet();
    }

    /** Returns ids of all vertices adjacent to v. */
    Iterable<Long> adjacent(long v) {
        Vertex vertex = vertexRefs.get(v);
        return vertex.adj.keySet();
    }

    /**
     * Returns the Great-circle distance between vertices v and w in miles.
     * Assumes the lon/lat methods are implemented properly.
     * @source https://www.movable-type.co.uk/scripts/latlong.html
     **/
    double distance(long v, long w) {
        double lat1 = lat(v);
        double lat2 = lat(w);
        double lon1 = lon(v);
        double lon2 = lon(w);

        return distanceHelper(lat1, lat2, lon1, lon2);
    }

    private double distanceHelper(double lat1, double lat2, double lon1, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dphi = Math.toRadians(lat2 - lat1);
        double dlambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dphi / 2.0) * Math.sin(dphi / 2.0);
        a += Math.cos(phi1) * Math.cos(phi2) * Math.sin(dlambda / 2.0) * Math.sin(dlambda / 2.0);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 3963 * c;
    }

    /**
     * Returns the initial bearing (angle) between vertices v and w in degrees
     * Assumes the lon/lat methods are implemented properly.
     * @source https://www.movable-type.co.uk/scripts/latlong.html
     */
    double bearing(long v, long w) {
        double phi1 = Math.toRadians(lat(v));
        double phi2 = Math.toRadians(lat(w));
        double lambda1 = Math.toRadians(lon(v));
        double lambda2 = Math.toRadians(lon(w));

        double y = Math.sin(lambda2 - lambda1) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2);
        x -= Math.sin(phi1) * Math.cos(phi2) * Math.cos(lambda2 - lambda1);
        return Math.toDegrees(Math.atan2(y, x));
    }

    /** Returns the vertex id closest to the given longitude and latitude. */
    long closest(double lon, double lat) {
        double min = Double.POSITIVE_INFINITY;
        long closest = -1;
        for (long v : vertices()) {
            if (distanceHelper(lat, lat(v), lon, lon(v)) < min) {
                closest = v;
                min = distanceHelper(lat, lat(v), lon, lon(v));
            }
        }
        return closest;
    }

    /** Longitude of vertex v. */
    double lon(long v) {
        return vertexRefs.get(v).lon;
    }

    /** Latitude of vertex v. */
    double lat(long v) {
        return vertexRefs.get(v).lat;
    }

    void addVertex(long id, double lon, double lat) {
        Vertex v = new Vertex(id, lon, lat);
        vertexRefs.put(id, v);
        cleanThese.add(id);
    }

    Vertex getVertex(long id) {
        return vertexRefs.get(id);
    }

    void addEdge(long v, long w, String way) {
        Vertex a = vertexRefs.get(v);
        Vertex b = vertexRefs.get(w);
        a.connect(b, way);
        b.connect(a, way);
        cleanThese.remove(a.id);
        cleanThese.remove(b.id);
    }

    void processNextWay() {
        if (nextWay != null) {
            while (nextWay.toConnect.size() > 1) {
                long first = nextWay.toConnect.removeFirst();
                long second = nextWay.toConnect.peekFirst();
                addEdge(first, second, nextWay.name);
            }
        }
    }

    void queueWay() {
        nextWay = new Way();
    }

    List<Map<String, Object>> getNodesByLocName(String locName) {
        List<Map<String, Object>> r = new ArrayList<>();
        List<Long> nodesID = locTrie.getNodeByLoc(locName);
        System.out.println(nodesID);
        System.out.println(vertexRefs.containsKey(nodesID.get(0)));
        for (long id : nodesID) {
            System.out.println(id);
            Map<String, Object> n = new HashMap<>();
            n.put("lat", lat(id));
            n.put("lon", lon(id));
            n.put("name", locName);
            n.put("id", id);
            r.add(n);
        }
        return r;
    }

    List<String> getLocationsByPrefix(String prefix) {
        return locTrie.keysWithPrefix(cleanString(prefix));
    }

    public class Vertex {
        long id;
        double lon, lat;
        HashMap<Long, Vertex> adj;
        Map<Long, String> adjWay;

        public Vertex(long id, double lon, double lat) {
            this.id = id;
            this.lon = lon;
            this.lat = lat;
            adj = new HashMap<>();
            adjWay = new HashMap<>();
        }

        void connect(Vertex o, String way) {
            adj.put(o.id, o);
            adjWay.put(o.id, way);
        }

        public void setLoc(String locName) {
            locTrie.put(locName, this.id);
        }
    }

    public class Way {
        ArrayDeque<Long> toConnect;
        boolean valid;
        String name;

        public Way() {
            toConnect = new ArrayDeque<>();
            valid = false;
        }

        public void validate() {
            valid = true;
        }

        public void addNode(Long id) {
            toConnect.addLast(id);
        }

        public boolean isValid() {
            return valid;
        }
    }
}
