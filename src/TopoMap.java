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
	private Map<Double, Set<List<Point2D.Double>>> contours = new TreeMap<Double, Set<List<Point2D.Double>>>();
	private Map<Point2D.Double, Set<Point2D.Double>> searchMap = new HashMap<Point2D.Double, Set<Point2D.Double>>();
	private double[][] bounds;
	List<Triangle> preMesh;

	public TopoMap(String file) throws IOException {

		Path stlPath = Paths.get(file);

		preMesh = STLParser.parseSTLFile(stlPath);
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

	public void initialize(double[] thresholds) {
		contours.clear();
		for (double threshold : thresholds) {
			contours.put(threshold, process(threshold));
		}
		doBallardLocksThings(thresholds[0], thresholds[1]);
		doMontlakeCutThings(thresholds[1]);
		doTinyPeninsulaThings(thresholds[1]);
		clean();
		removeTinys();
	}

	private void clean() {
		Set<List<Point2D.Double>> toRemove = new HashSet<List<Point2D.Double>>();
		Set<Point2D.Double> ptsToRemove = new HashSet<Point2D.Double>();
		for (Set<List<Point2D.Double>> contourLevel : contours.values()) {
			toRemove.clear();
			for (List<Point2D.Double> contour : contourLevel) {
				if (contour.size() <= 2) {
					toRemove.add(contour);
					continue;
				}
				ptsToRemove.clear();
				Point2D.Double prev = contour.get(contour.size() - 2);
				for (int i = 0; i < contour.size() - 1; i++) {
					if (prev.x == contour.get(i).x && prev.x == contour.get(i + 1).x//
							|| prev.y == contour.get(i).y && prev.y == contour.get(i + 1).y) {
						ptsToRemove.add(contour.get(i));
					}
					prev = contour.get(i);
				}
				contour.removeAll(ptsToRemove);
				if (!contour.get(0).equals(contour.get(contour.size() - 1))) {
					contour.add(contour.get(0));
				}
			}
			contourLevel.removeAll(toRemove);
		}
	}

	private void removeTinys() {
		Set<List<Point2D.Double>> toRemove = new HashSet<List<Point2D.Double>>();
		for (Set<List<Point2D.Double>> set : contours.values()) {
			toRemove.clear();
			for (List<Point2D.Double> list : set) {
				double area = getArea(list);
				if (area < 15) {
					toRemove.add(list);
				}
			}
			set.removeAll(toRemove);
		}
	}

	public void initialize(String svgPath) {
		svgPath = "M 4980.6,1656.53512476 1511.6,3562 4980.6,129.5"//
				+ " 2985.4,957 105.4,129.5 3453.56487524,129.5 "//
				+ "1506.5,-3365.4 4980.6,129.5 " + "4150.4,-1873.4 4980.6,-4787.2 4980.6,-1397.53512476 "
				+ "8452.2,-3365.3 4980.6,129.5 " + "6970.7,-729.2 9900,106.1 6507.63512476,129.5 "
				+ "8470.4,3601 4980.6,129.5 " + "5831.6,2093.5 4983.2,5007.2 Z M";
				// M 4744.5,269.6 3185.2,946.8 1921.6,3160 Z M 5063.6,264.4
				// 5790.1,1880.8 7987.8,3134 Z M 9308.4,111.3 6488,119
				// 6879.8,772.8 Z M
				// 6796.8,-752.7 7982.6,-2859.5 5144.1,20.5 Z M 4160.7,-1658.2
				// 2113.6,-2831 4843.1,-41.8 Z M 5644.8,-1806.2 4988.4,-4180.3
				// 4993.6,-1406.7 Z";
				// M 3473.2,108.7 3068.4,-550.3 650.2,111.3 Z

		// center: 4980.6,129.5
		// left: 3453.56487524

		// radius: 1527.03512476,129.5
		// right: 6507.63512476,129.5
		// down: 4980.6,1656.53512476
		// up: 4980.6,-1397.53512476

		String[] split = svgPath.split(" ");
		List<Point2D.Double> list = new ArrayList<Point2D.Double>();
		Set<List<Point2D.Double>> set = new HashSet<List<Point2D.Double>>();
		for (String str : split) {
			if (str.startsWith("M")) {
				if (!list.isEmpty()) {
					set.add(list);
					list = new ArrayList<Point2D.Double>();
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

	private double getArea(List<Point2D.Double> list) {
		double[] minXY = { Double.MAX_VALUE, Double.MAX_VALUE };
		double[] maxXY = { Double.MIN_VALUE, Double.MIN_VALUE };
		for (Point2D.Double pt : list) {

			double[] array = { pt.x, pt.y };
			for (int i = 0; i < 2; i++) {
				if (array[i] < minXY[i]) {
					minXY[i] = array[i];
				}
				if (array[i] > maxXY[i]) {
					maxXY[i] = array[i];
				}
			}
		}
		return (maxXY[0] - minXY[0]) * (maxXY[1] - minXY[1]);
	}

	private void doTinyPeninsulaThings(double lakeWash) {

		// i0: 116.19706870342779, 113.22086438152002
		// i1: 117.06282979214781, 113.0
		//
		// i2: 116.16782091503889, 112.06805417367146
		// i3: 117.0465520656568, 112.08715702477184

		Point2D.Double[] pts = { new Point2D.Double(116.19706870342779, 113.22086438152002),
				new Point2D.Double(117.06282979214781, 113.0),
				new Point2D.Double(116.16782091503889, 112.56805417367146),
				new Point2D.Double(116.36186057227516, 112.45529386029666),
				new Point2D.Double(116.77000276338089, 112.39488527018964),
				new Point2D.Double(117.0465520656568, 112.48715702477184) };

		List<Point2D.Double> oldContour = findList(pts[0], lakeWash);
		int index = oldContour.indexOf(pts[0]);

		for (int i = 2; i < pts.length; i++) {
			index++;
			oldContour.add(index, pts[i]);
		}
		index++;
		while (!oldContour.get(index).equals(pts[1])) {
			oldContour.remove(index);
		}
	}

	private void doMontlakeCutThings(double lakeWash) {
		// (north)
		// 113.02432856037157, 109.0
		// 107.20257682732203, 109.0
		// 105.94882748942715, 108.0

		// (south)
		// 113.12315993744676, 110.0 // 113.6555, 110.41447074586864
		// 106.24414631648915, 110.0 // y=111.32093087685276
		// 106.2578, 111.32093087685276

		// (south--new)
		// 113.6555, 110.41447074586864 // 113.6555, 110.41447074586864
		// 106.24414631648915, 110.0 // x=111.32093087685276
		// 106.2578, 111.32093087685276

		// 0: north, 1: south
		Point2D.Double[][] pts = {
				{ new Point2D.Double(113.6555, 108.51394901394907),
						new Point2D.Double(106.20364304758702, 108.45610781027716),
						new Point2D.Double(105.94882748942715, 108.0) }, //
				{ new Point2D.Double(113.12315993744676, 110.0),
						new Point2D.Double(106.25185611892023, 110.59032692976665),
						new Point2D.Double(106.8694314557018, 110.01196272547914),
						new Point2D.Double(106.2578, 111.32093087685276) } }; // 106.24414631648915,
																				// 110.0

		List<Point2D.Double> oldContour = findList(pts[0][0], lakeWash);

		// get indices
		int[][] index = new int[2][2];
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				index[i][j] = oldContour.indexOf(pts[i][(pts[i].length - 1) * j]);
			}
		}

		// split
		List<Point2D.Double> north = new ArrayList<Point2D.Double>();
		for (int i = 0; i <= index[0][0]; i++) {
			north.add(oldContour.get(i));
		}
		north.add(pts[0][1]);
		for (int i = index[0][1]; i < oldContour.size(); i++) {
			north.add(oldContour.get(i));
		}

		List<Point2D.Double> south = new ArrayList<Point2D.Double>();
		for (int i = index[1][0]; i <= index[1][1]; i++) {
			south.add(oldContour.get(i));
		}
		south.add(pts[1][1]);
		south.add(pts[1][2]);
		south.add(south.get(0));

		contours.get(lakeWash).remove(oldContour);
		contours.get(lakeWash).add(north);
		contours.get(lakeWash).add(south);
	}

	private List<Point2D.Double> findList(Point2D.Double pt, double thresh) {
		for (List<Point2D.Double> list : contours.get(thresh)) {
			if (list.contains(pt)) {
				return list;
			}
		}
		return null;
	}

	private void reIndex(List<Point2D.Double> list, Point2D.Double startPt) {
		List<Point2D.Double> l1 = new ArrayList<Point2D.Double>(), l2 = new ArrayList<Point2D.Double>();

		int inflectionIndex = list.indexOf(startPt);
		for (int i = 1; i < list.size(); i++) {
			if (i < inflectionIndex) {
				l1.add(list.get(i));
			} else {
				l2.add(list.get(i));
			}
		}
		list.clear();
		list.addAll(l2);
		list.addAll(l1);
	}

	/**
	 * Replaces the Lake Washington-level coast with the actual sea-level
	 * contour at the Ballard locks. Also removes the largest inland Lake
	 * Washington-level contour. (A few small ones remain)
	 * 
	 * @param oceanThresh
	 * @param lakeWashThresh
	 */
	private void doBallardLocksThings(double oceanThresh, double lakeWashThresh) {
		// post process and remove fuck tons of inconvenient things
		{
			Point2D.Double[] removeExtraLakeContours = { new Point2D.Double(113.58902590833443, 279.0),
					new Point2D.Double(125.7609, 274.89541171018067) };
			Set<List<Point2D.Double>> setToRemoveLake = new HashSet<List<Point2D.Double>>();
			for (Point2D.Double removePt : removeExtraLakeContours) {
				setToRemoveLake.add(findList(removePt, lakeWashThresh));
			}
			contours.get(lakeWashThresh).removeAll(setToRemoveLake);
		}

		// Sea/Lake
		// 32.85014858461594,85.8465006016415
		// 33.63252302035765,86.0098480645299
		// 0 ... 1
		// 3 ... 2
		// Lake/Sea
		// 33.45732049452805,89.74933161025403
		// 33.14531709838673,89.0
		Point2D.Double[] lockPts = { //
				new Point2D.Double(32.85014858461594, 85.8465006016415),
				new Point2D.Double(33.63252302035765, 86.0098480645299),
				new Point2D.Double(33.45732049452805, 89.74933161025403), //
				new Point2D.Double(33.14531709838673, 89.0) };

		// 170.74498313279213,279.0
		// 40.46498609551326,279.0
		// 51.226505006008985,0.0
		Point2D.Double[] outerPts = { //
				new Point2D.Double(170.74498313279213, 279.0), new Point2D.Double(40.46498609551326, 279.0), //
				new Point2D.Double(51.226505006008985, 0.0) };

		List<Point2D.Double> oceanList = findList(lockPts[0], oceanThresh), //
				lakeWashList = findList(lockPts[1], lakeWashThresh);

		{
			Collections.reverse(oceanList);
			reIndex(oceanList, new Point2D.Double(187.6325, 0.0));
			Set<List<Point2D.Double>> lakeContours = contours.get(lakeWashThresh);
			lakeContours.remove(lakeWashList);
			Collections.reverse(lakeWashList);
			reIndex(lakeWashList, new Point2D.Double(125.10580380722897, 0.0));
			lakeContours.add(lakeWashList);

			// if (!oceanList.isEmpty())
			// return;
		}

		System.out.println("ocean things");
		System.out
				.println(oceanList.indexOf(lockPts[0]) + " " + oceanList.indexOf(lockPts[3]) + " " + oceanList.size());
		System.out.println(
				lakeWashList.indexOf(lockPts[1]) + " " + lakeWashList.indexOf(lockPts[2]) + " " + lakeWashList.size());

		//
		int[] oceanIndex = { oceanList.indexOf(outerPts[1]), oceanList.indexOf(lockPts[3]),
				oceanList.indexOf(lockPts[0]), oceanList.indexOf(outerPts[2]) };
		int[] lakeIndex = { lakeWashList.indexOf(outerPts[0]), lakeWashList.indexOf(lockPts[2]),
				lakeWashList.indexOf(lockPts[1]) };

		// *include* the indices
		List<Point2D.Double> mergedList = new ArrayList<Point2D.Double>();
		for (int i = 0; i <= lakeIndex[0]; i++) {
			mergedList.add(lakeWashList.get(i));
		}
		for (int i = oceanIndex[0]; i <= oceanIndex[1]; i++) {
			mergedList.add(oceanList.get(i));
		}
		for (int i = lakeIndex[1]; i <= lakeIndex[2]; i++) {
			mergedList.add(lakeWashList.get(i));
		}
		for (int i = oceanIndex[2]; i <= oceanIndex[3]; i++) {
			mergedList.add(oceanList.get(i));
		}
		mergedList.add(mergedList.get(0));
		contours.remove(oceanThresh);
		Set<List<Point2D.Double>> lakeContours = contours.get(lakeWashThresh);
		lakeContours.remove(lakeWashList);
		lakeContours.add(mergedList);
	}

	public void preComputeSearchMap() {
		searchMap.clear();
		for (Set<List<Point2D.Double>> set : contours.values()) {
			for (List<Point2D.Double> path : set) {
				for (Point2D.Double pt : path) {
					Point2D.Double searchPoint = new Point2D.Double((int) pt.getX(), (int) pt.getY());
					Set<Point2D.Double> matches = searchMap.get(searchPoint);
					if (matches == null) {
						matches = new HashSet<Point2D.Double>();
						searchMap.put(searchPoint, matches);
					}
					matches.add(pt);
				}
			}
		}
	}

	/**
	 * List of paths corresponding to threshold values
	 * 
	 * @return
	 */
	public List<Path2D.Double> asPaths() {
		List<Path2D.Double> paths = new ArrayList<Path2D.Double>();
		for (double thresh : contours.keySet()) {
			Set<List<Point2D.Double>> curContours = contours.get(thresh);
			Path2D.Double path2d = new Path2D.Double();
			for (List<Point2D.Double> path : curContours) {
				path2d.moveTo(path.get(0).x, path.get(0).y);
				for (Point2D.Double pt : path) {
					path2d.lineTo(pt.x, pt.y);
				}
			}
			paths.add(path2d);
		}
		return paths;
	}

	/**
	 * 
	 * @param min
	 *            inclusive
	 * @param max
	 *            inclusive
	 * @param step
	 */
	public void initialize(double min, double max, double step) {
		contours.clear();
		for (double threshold = min; threshold <= max; threshold += step) {
			contours.put(threshold, process(threshold));
		}
	}

	/**
	 * Returns null if no nearby pt found
	 * 
	 * @param searchPt
	 * @return
	 */
	public Point2D.Double query(Point2D.Double searchPt) {

		if (searchPt == null) {
			return null;
		}

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

		Point2D.Double[] pts = { new Point2D.Double(truncX, truncY), new Point2D.Double(truncX, otherY),
				new Point2D.Double(otherX, truncY), new Point2D.Double(otherX, otherY) };

		Set<Point2D.Double> closePts = new HashSet<Point2D.Double>();
		for (Point2D.Double pt : pts) {
			if (searchMap.containsKey(pt)) {
				closePts.addAll(searchMap.get(pt));
			}
		}

		Point2D.Double closestPt = null;
		double minDist = Double.MAX_VALUE;
		for (Point2D.Double pt : closePts) {
			double dist = pt.distance(searchPt);
			if (dist < minDist) {
				minDist = dist;
				closestPt = pt;
			}
		}

		List<Point2D.Double> list = null;// findList(closestPt, 3);
		if (list != null)
			System.out.println("ocean index: " + list.indexOf(closestPt));
		list = findList(closestPt, 4.06);
		if (list != null)
			System.out.println("lake index: " + list.indexOf(closestPt));

		return closestPt;
	}

	private Set<List<Point2D.Double>> process(double zThresh) {
		Map<Point2D.Double, List<Point2D.Double>> consolidator = new HashMap<Point2D.Double, List<Point2D.Double>>();
		Set<List<Point2D.Double>> contour = new HashSet<List<Point2D.Double>>();
		// in case you need to change double equality
		double almostZero = 0.0000;

		IntervalTree.IntervalData<Triangle> interval = mesh.query(zThresh);

		if (interval == null) {
			return contour;
		}

		Collection<Triangle> intersectingTriangles = mesh.query(zThresh).getData();

		for (Triangle t : intersectingTriangles) { // preMesh){//
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

			List<Point2D.Double> list = new ArrayList<Point2D.Double>();
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
		contour.addAll(consolidator.values());

		return contour;
	}

	public void writePaths(String filename) {
		List<List<Point2D.Double>> simplifiedContours = new ArrayList<List<Point2D.Double>>();
		for (Set<List<Point2D.Double>> set : contours.values()) {
			simplifiedContours.addAll(set);
		}
		System.out.println("Writing out map: (" + simplifiedContours.size() + " contours)");
		writeObjectToFile(filename, simplifiedContours);
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

	/**
	 * List of paths corresponding to threshold values
	 * 
	 * @return
	 */
	public List<Path2D.Double> asSimplePaths(int xOffset) {

		List<Path2D.Double> paths = new ArrayList<Path2D.Double>();
		for (double thresh : contours.keySet()) {
			Set<List<Point2D.Double>> curContours = contours.get(thresh);
			for (List<Point2D.Double> path : curContours) {
				Path2D.Double path2d = new Path2D.Double();
				path2d.moveTo(path.get(0).x + xOffset, path.get(0).y);
				for (Point2D.Double pt : path) {
					path2d.lineTo(pt.x + xOffset, pt.y);
				}
				paths.add(path2d);
			}
		}
		return paths;
	}

	private Point2D.Double rounded(Point2D.Double pt) {
		return new Point2D.Double(round(pt.getX()), round(pt.getY()));
	}

	private double round(double db) {
		int val = 100000;
		return (int) (db * val);
	}

	private void addToContour(List<Point2D.Double> l0, Map<Point2D.Double, List<Point2D.Double>> consolidator) {
		if (l0.isEmpty()) {
			return;
		}

		// beginning state:
		// l0: list(0) .... list(end)
		// l1: list(0) .... l1(end) (or l1: null) (or:
		// l1: l1(end) .... list(0)
		for (int i = 0; i < 2; i++) {
			Point2D.Double cur = l0.get(0);
			List<Point2D.Double> l1 = consolidator.get(rounded(cur));
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

	private Point2D.Double getIntersect(Vec3d pt1, Vec3d pt2, double zThresh) {
		double scale = (zThresh - pt1.z) / (pt2.z - pt1.z);
		double newX = pt1.x + scale * (pt2.x - pt1.x);
		double newY = pt1.y + scale * (pt2.y - pt1.y);

		return new Point2D.Double(newX, newY);
	}

	public void writeSVGFile(String output) {
		/*
		 * 
		 * <rect x="25" y="25" width="200" height="200" fill="lime"
		 * stroke-width="4" stroke="pink" /> <circle cx="125" cy="125" r="75"
		 * fill="orange" /> <polyline points="50,150 50,200 200,200 200,100"
		 * stroke="red" stroke-width="4" fill="none" /> <line x1="50" y1="50"
		 * x2="200" y2="200" stroke="blue" stroke-width="4" />
		 * 
		 */
		double strokeWidth = .1;
		String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\"  viewBox=\"0 0 187.6325 279.0\">\n";
		String footer = "</svg>";
		String pre = "\t<polyline id=\"polyline";
		// id="polyline3846"
		String mid = "\" points=\"";
		String post = "\" stroke=\"black\" stroke-width=\"" + strokeWidth + "\" fill=\"none\" />";
		try {
			PrintWriter writer = new PrintWriter(output, "UTF-8");
			writer.print(header);
			int i = 0;
			for (Set<List<Point2D.Double>> sets : contours.values()) {
				for (List<Point2D.Double> list : sets) {
					writer.println(pre + i + mid + pointsListToString(list) + post);
					i++;
				}
			}
			writer.println(footer);
			writer.close();
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	private String pointsListToString(List<Point2D.Double> list) {
		String str = "";

		for (Point2D.Double pt : list) {
			str += pt.getX() + "," + pt.getY() + " ";
		}

		return str.trim();
	}
}
