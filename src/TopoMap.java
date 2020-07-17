import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class TopoMap {

	private IntervalTree<Triangle> mesh;
	private Map<Double, Set<Contour>> contours = new TreeMap<Double, Set<Contour>>();
	private Map<Point2D, Set<Point2D>> searchMap = new HashMap<Point2D, Set<Point2D>>();
	private double[][] bounds;

	public TopoMap(String file) throws IOException {

		Path stlPath = Paths.get(file);

		List<Triangle> preMesh = STLParser.parseSTLFile(stlPath);
		bounds = getBounds(preMesh);
		mesh = convertTriangles(preMesh);
	}

	private IntervalTree<Triangle> convertTriangles(List<Triangle> mesh) {
		List<IntervalTree.IntervalData<Triangle>> list = new ArrayList<>();
		for (Triangle t : mesh) {
			Vec3d[] vertices = t.getVertices();
			double zMin = Double.min(vertices[0].z, Double.min(vertices[1].z, vertices[2].z));
			double zMax = Double.max(vertices[0].z, Double.max(vertices[1].z, vertices[2].z));

			list.add(new IntervalTree.IntervalData<Triangle>(zMin, zMax, t));
		}

		return new IntervalTree<Triangle>(list);
	}

	private double[][] getBounds(List<Triangle> mesh) {
		double[] min = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
		double[] max = { Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE };
		for (Triangle t : mesh) {
			for (Vec3d vertex : t.getVertices()) {
				double[] array = { vertex.x, vertex.y, vertex.z };
				for (int i = 0; i < 3; i++) {
					if (array[i] < min[i]) {
						min[i] = array[i];
					}
					if (array[i] > max[i]) {
						max[i] = array[i];
					}
				}
			}
		}
		return new double[][] { min, max };
	}

	public double[][] getBounds() {
		return new double[][] { Arrays.copyOf(bounds[0], 3), Arrays.copyOf(bounds[1], 3) };
	}

	public void createContours(double[] thresholds) {
		contours.clear();
		for (double threshold : thresholds) {
			contours.put(threshold, process(threshold));
		}
		doSeattleThings(thresholds);
		removeTinys(15);
	}

	private void doSeattleThings(double[] thresholds) {
		// contours.get(thresholds[1]).remove(findContour(new Point2D.Double(21.52057,
		// 192.81208763168445), 4.06));
		// contours.get(thresholds[1]).remove(findContour(new
		// Point2D.Double(125.23646370527592, 276.220285021225), 4.06));
		// doBallardLocksDifferentWay();
		// doMontlakeCutThingsViaSplicing(thresholds[1]);
		// doTinyPeninsulaThings(thresholds[1]);
	}

	/**
	 * 
	 * @param min  inclusive
	 * @param max  inclusive
	 * @param step
	 */
	public void createContours(double min, double max, double step) {
		contours.clear();
		for (double threshold = min; threshold <= max; threshold += step) {
			contours.put(threshold, process(threshold));
		}
	}

	private Set<Contour> process(double zThresh) {
		Map<Point2D, List<Point2D>> consolidator = new HashMap<Point2D, List<Point2D>>();
		Set<Contour> contour = new HashSet<Contour>();
		// in case you need to change double equality
		double almostZero = 0.0000;

		IntervalTree.IntervalData<Triangle> interval = mesh.query(zThresh);

		if (interval == null) {
			return contour;
		}

		Collection<Triangle> intersectingTriangles = mesh.query(zThresh).getData();

		for (Triangle t : intersectingTriangles) {
			int countBelow = 0, countAbove = 0;
			Vec3d prev = null;

			// figure out whether this triangle intersects the z = zThresh plane
			for (Vec3d vertex : t.getVertices()) {
				// if not zero
				if (!((vertex.z - zThresh) * (vertex.z - zThresh) <= almostZero)) {
					// is below
					if (vertex.z < zThresh) {
						countBelow++;
					} else // is above
					{
						countAbove++;
					}
				}
				prev = vertex;
			}

			List<Point2D> list = new ArrayList<Point2D>();
			// if the triangle intersects the plane...
			if (countBelow != 3 && countAbove != 3) {
				// check for consecutive vertices on opposite sides of the plane
				for (Vec3d vertex : t.getVertices()) {
					// if they're on opposite sides
					if ((zThresh - vertex.z) * (zThresh - prev.z) <= 0) {
						list.add(getIntersect(vertex, prev, zThresh));
					}
					if ((vertex.z - zThresh) * (vertex.z - zThresh) <= almostZero) {
						// not sure this is the right thing to do for the
						// "equals" case
						list.add(new Point2D.Double(vertex.x, vertex.y));
					}
					prev = vertex;
				}
			}
			addToContour(list, consolidator);
		}
		for (List<Point2D> list : consolidator.values()) {
			contour.add(new Contour(list));
		}

		return contour;
	}

	public void createContours(String svgPath) {
		svgPath = "M 4980.6,1656.53512476 1511.6,3562 4980.6,129.5"//
				+ " 2985.4,957 105.4,129.5 3453.56487524,129.5 "//
				+ "1506.5,-3365.4 4980.6,129.5 " + "4150.4,-1873.4 4980.6,-4787.2 4980.6,-1397.53512476 "
				+ "8452.2,-3365.3 4980.6,129.5 " + "6970.7,-729.2 9900,106.1 6507.63512476,129.5 "
				+ "8470.4,3601 4980.6,129.5 " + "5831.6,2093.5 4983.2,5007.2 Z M";

		// center: 4980.6,129.5
		// left: 3453.56487524

		// radius: 1527.03512476,129.5
		// right: 6507.63512476,129.5
		// down: 4980.6,1656.53512476
		// up: 4980.6,-1397.53512476

		String[] split = svgPath.split(" ");
		List<Point2D> list = new ArrayList<Point2D>();
		Set<Contour> set = new HashSet<Contour>();
		for (String str : split) {
			if (str.startsWith("M")) {
				if (!list.isEmpty()) {
					set.add(new Contour(list));
					list = new ArrayList<Point2D>();
				}
			} else if (str.startsWith("Z")) {
				list.add(list.get(0));
			} else {
				String[] coords = str.split(",");
				list.add(new Point2D.Double(25 - Double.parseDouble(coords[0]) / 300, //
						25 + Double.parseDouble(coords[1]) / 300));
			}

		}
		contours.clear();
		contours.put(0.0, set);
	}

	private void doBallardLocksDifferentWay() {
		Point2D otherStart = new Point2D.Double(32.85014858461594, 85.8465006016415);
		Point2D otherEnd = new Point2D.Double(51.226505006008985, 0.0);
		Point2D thisStart = new Point2D.Double(125.10580380722897, 0.0);
		Point2D thisEnd = new Point2D.Double(33.63252302035765, 86.0098480645299);

		Contour ocean = findContour(otherStart, 3);
		Contour lake = findContour(thisStart, 4.06);

		lake.splice(ocean, thisStart, thisEnd, otherStart, otherEnd, false);

		thisEnd = new Point2D.Double(170.74498313279213, 279.0);
		thisStart = new Point2D.Double(33.45732049452805, 89.74933161025403);
		otherStart = new Point2D.Double(40.46498609551326, 279.0);
		otherEnd = new Point2D.Double(33.14531709838673, 89.0);

		lake.splice(ocean, thisStart, thisEnd, otherStart, otherEnd, false);

		// finalize
		contours.remove(3.0);
	}

	private void doTinyPeninsulaThings(double lakeWash) {

		Point2D[] pts = { new Point2D.Double(116.19706870342779, 113.22086438152002),
				new Point2D.Double(116.16782091503889, 112.56805417367146),
				new Point2D.Double(116.36186057227516, 112.45529386029666),
				new Point2D.Double(116.77000276338089, 112.39488527018964),
				new Point2D.Double(117.0465520656568, 112.48715702477184),
				new Point2D.Double(117.06282979214781, 113.0) };

		Contour oldContour = findContour(pts[0], lakeWash);
		List<Point2D> l = Arrays.asList(pts);
		l = new ArrayList<Point2D>(l);
		l.add(l.get(0));
		Contour peninsula = new Contour(l);

		Point2D otherStart = pts[1];
		Point2D otherEnd = pts[4];
		Point2D thisStart = pts[5];
		Point2D thisEnd = pts[0];

		oldContour.splice(peninsula, thisStart, thisEnd, otherStart, otherEnd, false);

	}

	private void doMontlakeCutThingsViaSplicing(double lakeWash) {

		// 0: north, 1: south
		Point2D[][] pts = {
				{ new Point2D.Double(113.6555, 108.51394901394907),
						new Point2D.Double(106.20364304758702, 108.45610781027716),
						new Point2D.Double(105.94882748942715, 108.0) }, //
				{ new Point2D.Double(113.12315993744676, 110.0),
						new Point2D.Double(106.8694314557018, 110.01196272547914),
						new Point2D.Double(106.25185611892023, 110.59032692976665),
						new Point2D.Double(106.2578, 111.32093087685276) } };

		List<Point2D> l = Arrays.asList(pts[0]);
		l = new ArrayList<Point2D>(l);
		l.add(l.get(0));
		Contour north = new Contour(l);
		l = Arrays.asList(pts[1]);
		l = new ArrayList<Point2D>(l);
		l.add(l.get(0));
		Contour south = new Contour(l);

		Contour oldContour = findContour(pts[0][0], lakeWash);
		contours.get(lakeWash).remove(oldContour);

		Point2D otherStart = pts[0][2];
		Point2D otherEnd = pts[0][0];
		Point2D thisStart = pts[0][1];
		Point2D thisEnd = pts[0][1];

		north.splice(oldContour, thisStart, thisEnd, otherStart, otherEnd, false);

		otherStart = pts[1][0];
		otherEnd = pts[1][3];
		thisStart = pts[1][2];
		thisEnd = pts[1][1];

		south.splice(oldContour, thisStart, thisEnd, otherStart, otherEnd, false);

		contours.get(lakeWash).add(north);
		contours.get(lakeWash).add(south);
	}

	private void removeTinys(double areaThreshold) {
		Set<Contour> toRemove = new HashSet<Contour>();
		for (Set<Contour> set : contours.values()) {
			toRemove.clear();
			for (Contour list : set) {
				double area = list.getAreaEstimate();
				if (area < areaThreshold) {
					toRemove.add(list);
				}
			}
			set.removeAll(toRemove);
		}
	}

	private Contour findContour(Point2D pt, double thresh) {
		for (Contour contour : contours.get(thresh)) {
			if (contour.contains(pt)) {
				return contour;
			}
		}
		return null;
	}

	public void preComputeSearchMap() {
		searchMap.clear();
		for (Set<Contour> set : contours.values()) {
			for (Contour path : set) {
				for (Point2D pt : path.points()) {
					Point2D searchPoint = new Point2D.Double((int) pt.getX(), (int) pt.getY());
					Set<Point2D> matches = searchMap.get(searchPoint);
					if (matches == null) {
						matches = new HashSet<Point2D>();
						searchMap.put(searchPoint, matches);
					}
					matches.add(pt);
				}
			}
		}
	}

	/**
	 * Returns null if no nearby pt found
	 * 
	 * @param searchPt
	 * @return
	 */
	public Point2D query(Point2D searchPt) {

		if (searchPt == null) {
			return null;
		}

		// Compute the four points with integer x/y values closest to the searchPt.
		int truncX = (int) searchPt.getX();
		int truncY = (int) searchPt.getY();
		int otherX = truncX, otherY = truncY;
		if (searchPt.getX() - .5 < truncX) {
			otherX--;
		} else {
			otherX++;
		}
		if (searchPt.getY() - .5 < truncY) {
			otherY--;
		} else {
			otherY++;
		}
		Point2D[] pts = { new Point2D.Double(truncX, truncY), new Point2D.Double(truncX, otherY),
				new Point2D.Double(otherX, truncY), new Point2D.Double(otherX, otherY) };

		// Index into the search map with these integer-valued points.
		Set<Point2D> closePts = new HashSet<Point2D>();
		for (Point2D pt : pts) {
			if (searchMap.containsKey(pt)) {
				closePts.addAll(searchMap.get(pt));
			}
		}

		// Of the points discovered in the search map, return the closest one.
		Point2D closestPt = null;
		double minDist = Double.MAX_VALUE;
		for (Point2D pt : closePts) {
			double dist = pt.distance(searchPt);
			if (dist < minDist) {
				minDist = dist;
				closestPt = pt;
			}
		}

		return closestPt;
	}

	/**
	 * List of paths corresponding to threshold values, one path per threshold value
	 * 
	 * @return
	 */
	public List<Path2D> asPaths() {
		List<Path2D> paths = new ArrayList<Path2D>();
		for (double thresh : contours.keySet()) {
			Set<Contour> curContours = contours.get(thresh);
			Path2D path2d = new Path2D.Double();
			for (Contour path : curContours) {
				path2d.append(path.asPath(0), false);
			}
			paths.add(path2d);
		}
		return paths;
	}

	/**
	 * List of paths corresponding to underlying contours values
	 * 
	 * @return
	 */
	public List<Path2D> asSimplePaths(int xOffset) {

		List<Path2D> paths = new ArrayList<Path2D>();
		for (double thresh : contours.keySet()) {
			Set<Contour> curContours = contours.get(thresh);
			for (Contour path : curContours) {
				paths.add(path.asPath(0));
			}
		}
		return paths;
	}

	public void writeSVGFile(String outputFile) {
		double strokeWidth = .1;
		String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\"  viewBox=\"0 0 187.6325 279.0\">\n";
		String footer = "</svg>";
		String pre = "\t<polyline id=\"polyline";
		// id="polyline3846"
		String mid = "\" points=\"";
		String post = "\" stroke=\"black\" stroke-width=\"" + strokeWidth + "\" fill=\"none\" />";
		try {
			PrintWriter writer = new PrintWriter(outputFile, "UTF-8");
			writer.print(header);
			int i = 0;
			for (Set<Contour> sets : contours.values()) {
				for (Contour list : sets) {
					writer.println(pre + i + mid + list.toStringSimple() + post);
					i++;
				}
			}
			writer.println(footer);
			writer.close();
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	public void writePaths(String filename) {
		List<Contour> simplifiedContours = new ArrayList<Contour>();
		for (Set<Contour> set : contours.values()) {
			simplifiedContours.addAll(set);
		}
		System.out.println("Writing out map: (" + simplifiedContours.size() + " contours)");
		// writeObjectToFile(filename, simplifiedContours);
		System.out.println("Didn't actually write it to file");
	}

	private void writeObjectToFile(String file, Object obj) {
		try {
			FileOutputStream fs = new FileOutputStream(file);
			ObjectOutputStream oos = new ObjectOutputStream(fs);
			oos.writeObject(obj);
			oos.close();
			fs.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void addToContour(List<Point2D> l0, Map<Point2D, List<Point2D>> consolidator) {
		if (l0.isEmpty()) {
			return;
		}

		// beginning state:
		// l0: list(0) .... list(end)
		// l1: list(0) .... l1(end) (or l1: null) (or:
		// l1: l1(end) .... list(0)
		for (int i = 0; i < 2; i++) {
			Point2D cur = l0.get(0);
			List<Point2D> l1 = consolidator.get(rounded(cur));
			if (l0.equals(l1))
				continue;
			if (l1 != null) {
				// remove l1 from the map for now
				consolidator.remove(rounded(l1.get(0)));
				consolidator.remove(rounded(l1.get(l1.size() - 1)));

				if (rounded(l1.get(0)).equals(rounded(cur)))
					Collections.reverse(l1);

				// at this point, we have:
				// l0: list(0) .... list(end)
				// l1: l1(end) .... list(0)
				l0.remove(0);
				l1.addAll(l0);
				l0 = l1;

				// at this point, we have:
				// l0: l1(end) .... list(0) .... list(end)

				// after the next reverse, we get:
				// l0: list(end) .... list(0) .... l1(end)
				// this means that on the second pass through the loop we're
				// looking for a match of list(end)
				// and it should still be in the map unless that closed a loop
				// but in the case of a closed loop at this point both ends of
				// the list will be that endpoint
			}
			Collections.reverse(l0);
		}

		consolidator.put(rounded(l0.get(0)), l0);
		consolidator.put(rounded(l0.get(l0.size() - 1)), l0);
	}

	private Point2D getIntersect(Vec3d pt1, Vec3d pt2, double zThresh) {
		double scale = (zThresh - pt1.z) / (pt2.z - pt1.z);
		double newX = pt1.x + scale * (pt2.x - pt1.x);
		double newY = pt1.y + scale * (pt2.y - pt1.y);

		return new Point2D.Double(newX, newY);
	}

	private Point2D rounded(Point2D pt) {
		return new Point2D.Double(round(pt.getX()), round(pt.getY()));
	}

	private double round(double db) {
		int val = 100000;
		return (int) (db * val);
	}
}
