import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class provides a shortestPath method for finding routes between two points
 * on the map. Start by using Dijkstra's, and if your code isn't fast enough for your
 * satisfaction (or the autograder), upgrade your implementation by switching it to A*.
 * Your code will probably not be fast enough to pass the autograder unless you use A*.
 * The difference between A* and Dijkstra's is only a couple of lines of code, and boils
 * down to the priority you use to order your vertices.
 */
public class Router {
    static Map<Long, Double> priorityMap;

    /**
     * Return a List of longs representing the shortest path from st to dest,
     * where the longs are node IDs.
     */
    public static List<Long> shortestPath(GraphDB g, double stlon, double stlat,
                                          double destlon, double destlat) {
        LinkedList<Long> path = new LinkedList<>();
        long start = g.closest(stlon, stlat);
        long end = g.closest(destlon, destlat);
        double totalDistance = g.distance(start, end);
        PathNode initial = new PathNode(null, start, end, 0, totalDistance);
        PriorityQueue<PathNode> minPQ = new PriorityQueue<>();
        HashSet<Long> marked = new HashSet<>();
        minPQ.add(initial);
        boolean arrived = false;

        while (!arrived) {
            PathNode next = minPQ.poll();
            if (next != null) {
                System.out.println(next);
                marked.add(next.thisPoint);
                if (next.arrived()) {
                    arrived = true;
                    for (PathNode n = next; n != null; n = n.prev) {
                        path.addFirst(n.thisPoint);
                    }
                } else {
                    for (long neighbor : g.adjacent(next.thisPoint)) {
                        if (!marked.contains(neighbor)) {
                            double neighborDistFromStart = next.distFromStart
                                    + g.distance(next.thisPoint, neighbor);
                            double neighborDistFromFinish = g.distance(neighbor, next.endPoint);
                            PathNode nextNode = new PathNode(next, neighbor,
                                    next.endPoint, neighborDistFromStart, neighborDistFromFinish);
                            if (next.prev == null || neighbor != next.prev.thisPoint) {
                                minPQ.add(nextNode);
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Path " + path);
        return path;
    }

    /**
     * Given a ROUTE as a list of longs representing graph node IDs on graph G,
     * return a List of NavigationDirection objects representing the correct travel directions
     * in the right order.
     */
    public static List<NavigationDirection> routeDirections(GraphDB g, List<Long> route) {
        List<NavigationDirection> navList = new LinkedList<>();
        int dir = 0;
        ListIterator<Long> ltr = route.listIterator();
        Long cur = ltr.next();
        NavigationDirection prev = null;
        while (ltr.hasNext()) {
            Long next = ltr.next();
            NavigationDirection nav = new NavigationDirection();
            nav.direction = dir;
            nav.distance = g.distance(cur, next);
            String name = g.vertexRefs.get(cur).adjWay.get(next);
            if (name == null)
                nav.way = NavigationDirection.UNKNOWN_ROAD;
            else
                nav.way = name;
            double relBearing = g.bearing(next, cur);
            if (relBearing >= -15.0 && relBearing <= 15.0) {
                dir = 1;
            } else if (relBearing >= -30.0 && relBearing <= 30.0) {
                if (relBearing < 0.0) {
                    dir = 2;
                } else {
                    dir = 3;
                }
            } else if (relBearing >= -100.0 && relBearing <= 100.0) {
                if (relBearing < 0.0) {
                    dir = 4;
                } else {
                    dir = 5;
                }
            } else {
                if (relBearing < 0.0) {
                    dir = 6;
                } else {
                    dir = 7;
                }
            }
            cur = next;
            if (prev != null && prev.way.equals(nav.way)) {
                prev.distance = prev.distance + nav.distance;
            } else {
                System.out.println(prev + " " + Double.toString(relBearing));
//                System.out.println(nav + " " + Double.toString(relBearing));
                prev = nav;
                navList.add(nav);
            }

        }
        return navList;
    }

    public static List<NavigationDirection> routeDirections2(GraphDB graph, List<Long> route) {
        List<NavigationDirection> navList = new ArrayList<>();
        int direction = NavigationDirection.START;
        long previousNode = route.get(0);
        long previousFixedNode = previousNode;
        NavigationDirection previousNavigation = null;
        for(int i=1; i<route.size(); i++) {
            long currentNode = route.get(i);
            NavigationDirection currentNavigation = new NavigationDirection();
            currentNavigation.direction = direction;
            currentNavigation.distance = graph.distance(previousNode, currentNode);
            String name = graph.vertexRefs.get(previousNode).adjWay.get(currentNode);
            if (name == null)
                currentNavigation.way = NavigationDirection.UNKNOWN_ROAD;
            else
                currentNavigation.way = name;

            double relativeBearing = graph.bearing(currentNode, previousNode);
            direction = getDirection(relativeBearing);
            previousNode = currentNode;
            if (previousNavigation != null && previousNavigation.way.equals(currentNavigation.way)) {
                previousNavigation.distance = previousNavigation.distance + currentNavigation.distance;
            } else {
                System.out.println(previousNavigation + " ==== " + Double.toString(relativeBearing));
                System.out.println(currentNavigation + " ==== " + Double.toString(relativeBearing));
                System.out.println("Expected bearing" + Double.toString(graph.bearing(currentNode, previousFixedNode)));
                previousFixedNode = currentNode;
                currentNavigation.direction = direction;
                previousNavigation = currentNavigation;
                navList.add(currentNavigation);
            }
        }
        return navList;
    }

    private static int getDirection(double relativeBearing) {
        int direction;
        if (relativeBearing >= -15.0 && relativeBearing <= 15.0) {
//                System.out.println("go straight");
            direction = NavigationDirection.STRAIGHT;
        } else if (relativeBearing >= -30.0 && relativeBearing <= 30.0) {
            if (relativeBearing < 0.0) {
//                    System.out.println("slight left");
                direction = NavigationDirection.SLIGHT_LEFT;
            } else {
//                    System.out.println("slight right");
                direction = NavigationDirection.SLIGHT_RIGHT;
            }
        } else if (relativeBearing >= -100.0 && relativeBearing <= 100.0) {
            if (relativeBearing < 0.0) {
//                    System.out.println("turn left");
                direction = NavigationDirection.RIGHT;
            } else {
//                    System.out.println("turn right");
                direction = NavigationDirection.LEFT;
            }
        } else {
            if (relativeBearing < 0.0) {
//                    System.out.println("sharp left");
                direction = NavigationDirection.SHARP_LEFT;
            } else {
//                    System.out.println("sharp right");
                direction = NavigationDirection.SHARP_RIGHT;
            }
        }
        return direction;
    }

    public static class PathNode implements Comparable<PathNode> {
        PathNode prev;
        double distFromStart;
        double distFromFinish;
        double priority;
        long thisPoint;
        long endPoint;

        public PathNode(PathNode p, long t, long f, double fromS, double fromF) {
            prev = p;
            thisPoint = t;
            endPoint = f;
            distFromStart = fromS;
            distFromFinish = fromF;
            priority = distFromStart + distFromFinish;
        }

        public boolean arrived() {
            return thisPoint == endPoint;
        }

        @Override
        public int compareTo(PathNode other) {
            if (priority == other.priority) {
                return 0;
            } else if (priority < other.priority) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * Class to represent a navigation direction, which consists of 3 attributes:
     * a direction to go, a way, and the distance to travel for.
     */
    public static class NavigationDirection {

        /**
         * Integer constants representing directions.
         */
        public static final int START = 0;
        public static final int STRAIGHT = 1;
        public static final int SLIGHT_LEFT = 2;
        public static final int SLIGHT_RIGHT = 3;
        public static final int RIGHT = 4;
        public static final int LEFT = 5;
        public static final int SHARP_LEFT = 6;
        public static final int SHARP_RIGHT = 7;

        /**
         * Number of directions supported.
         */
        public static final int NUM_DIRECTIONS = 8;

        /**
         * A mapping of integer values to directions.
         */
        public static final String[] DIRECTIONS = new String[NUM_DIRECTIONS];

        /**
         * Default name for an unknown way.
         */
        public static final String UNKNOWN_ROAD = "unknown road";

        /** Static initializer. */
        static {
            DIRECTIONS[START] = "Start";
            DIRECTIONS[STRAIGHT] = "Go straight";
            DIRECTIONS[SLIGHT_LEFT] = "Slight left";
            DIRECTIONS[SLIGHT_RIGHT] = "Slight right";
            DIRECTIONS[LEFT] = "Turn left";
            DIRECTIONS[RIGHT] = "Turn right";
            DIRECTIONS[SHARP_LEFT] = "Sharp left";
            DIRECTIONS[SHARP_RIGHT] = "Sharp right";
        }

        /**
         * The direction a given NavigationDirection represents.
         */
        int direction;
        /**
         * The name of the way I represent.
         */
        String way;
        /**
         * The distance along this way I represent.
         */
        double distance;

        public NavigationDirection() {
            this.direction = STRAIGHT;
            this.way = UNKNOWN_ROAD;
            this.distance = 0.0;
        }

        public static NavigationDirection fromString(String dirAsString) {
            String regex = "([a-zA-Z\\s]+) on ([\\w\\s]*) and continue for ([0-9\\.]+) miles\\.";
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(dirAsString);
            NavigationDirection nd = new NavigationDirection();
            if (m.matches()) {
                String direction = m.group(1);
                if (direction.equals("Start")) {
                    nd.direction = NavigationDirection.START;
                } else if (direction.equals("Go straight")) {
                    nd.direction = NavigationDirection.STRAIGHT;
                } else if (direction.equals("Slight left")) {
                    nd.direction = NavigationDirection.SLIGHT_LEFT;
                } else if (direction.equals("Slight right")) {
                    nd.direction = NavigationDirection.SLIGHT_RIGHT;
                } else if (direction.equals("Turn right")) {
                    nd.direction = NavigationDirection.RIGHT;
                } else if (direction.equals("Turn left")) {
                    nd.direction = NavigationDirection.LEFT;
                } else if (direction.equals("Sharp left")) {
                    nd.direction = NavigationDirection.SHARP_LEFT;
                } else if (direction.equals("Sharp right")) {
                    nd.direction = NavigationDirection.SHARP_RIGHT;
                } else {
                    return null;
                }

                nd.way = m.group(2);
                try {
                    nd.distance = Double.parseDouble(m.group(3));
                } catch (NumberFormatException e) {
                    return null;
                }
                return nd;
            } else {
                // not a valid nd
                return null;
            }
        }

        public String toString() {
            return String.format("%s on %s and continue for %.3f miles.", DIRECTIONS[direction], way, distance);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof NavigationDirection) {
                return direction == ((NavigationDirection) o).direction
                        && way.equals(((NavigationDirection) o).way)
                        && distance == ((NavigationDirection) o).distance;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(direction, way, distance);
        }
    }
}
