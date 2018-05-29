import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class TopoMap {

	private List<Triangle> mesh;
	private Map<Double, Set<List<Point2D.Double>>> contours = new TreeMap<Double, Set<List<Point2D.Double>>>();
	private double lakeWashThresh = 4.06;

	public TopoMap(String file) throws IOException {

		Path stlPath = Paths.get(file);

		mesh = STLParser.parseSTLFile(stlPath);
	}

	public double[][] getBounds() {
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

	public void initialize(double[] thresholds) {
		contours.clear();
		for (double threshold : thresholds) {
			contours.put(threshold, process(threshold));
		}
	}

	public void initialize(double min, double max, double step) {
		contours.clear();
		for (double threshold = min; threshold < max; threshold += step) {
			contours.put(threshold, process(threshold));
		}
	}

	private Set<List<Point2D.Double>> process(double zThresh) {
		Map<Point2D.Double, List<Point2D.Double>> consolidator = new HashMap<Point2D.Double, List<Point2D.Double>>();
		// in case you need to change double equality
		double almostZero = 0.0000;

		for (Triangle t : mesh) {
			int countBelow = 0, countAbove = 0, countAt = 0;
			Vec3d prev = null;

			// special Lake Washington filter
			if (zThresh == lakeWashThresh) {
				if (t.getVertices()[0].x < 110 || //
						(t.getVertices()[0].x < 140 && t.getVertices()[0].y < 50)) {
					continue;
				}
			}

			// figure out whether this triangle intersects the z = zThresh plane
			for (Vec3d vertex : t.getVertices()) {
				if ((vertex.z - zThresh) * (vertex.z - zThresh) <= almostZero) {
					// not sure this is the right thing to do for the "equals"
					// case
					countAt++;
				} else if (vertex.z < zThresh) {
					countBelow++;
				} else {
					countAbove++;
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
		Set<List<Point2D.Double>> contour = new HashSet<List<Point2D.Double>>();
		contour.addAll(consolidator.values());

		return contour;
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
	
	public void writePaths(String filename){
		Set<List<Point2D.Double>> simplifiedContours = new HashSet<List<Point2D.Double>>();
		for (Set<List<Point2D.Double>> set : contours.values()){
			simplifiedContours.addAll(set);
		}
		writeObjectToFile(filename,simplifiedContours);
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
			List<Point2D.Double> l1 = consolidator.get(cur);
			if (l0.equals(l1))
				continue;
			if (l1 != null) {
				// remove l1 from the map for now
				consolidator.remove(l1.get(0));
				consolidator.remove(l1.get(l1.size() - 1));

				if (l1.get(0).equals(cur))
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

		consolidator.put(l0.get(0), l0);
		consolidator.put(l0.get(l0.size() - 1), l0);
	}

	private Point2D.Double getIntersect(Vec3d pt1, Vec3d pt2, double zThresh) {
		double scale = (zThresh - pt1.z) / (pt2.z - pt1.z);
		double newX = pt1.x + scale * (pt2.x - pt1.x);
		double newY = pt1.y + scale * (pt2.y - pt1.y);

		return new Point2D.Double(newX, newY);
	}
}
