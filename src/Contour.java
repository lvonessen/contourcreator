import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical form for a contour has points in clockwise order (assuming x
 * increases to the right and y decreases downward) and the first point of the
 * contour is, of the points with minimal y, that with minimal x ( "upper
 * left-ish")
 */
public class Contour implements Serializable {
	/**
	 * default serial version uid
	 */
	private static final long serialVersionUID = 1L;
	private List<Point2D> contour;
	private Point2D upperLeft;
	private final int id;
	private static int nextId = 0;

	public Contour(List<Point2D> outline) {
		id = nextId;
		nextId++;
		contour = new ArrayList<Point2D>();
		contour.addAll(outline);

		// reduces complexity (potentially not by much, but eh)
		removeColinearPts();

		// converts to canonical form
		forceCanonical();
	}

	/**
	 * Unmodifiable version of the underlying points of this contour
	 * 
	 * @return
	 */
	public Collection<Point2D> points() {
		return Collections.unmodifiableCollection(contour);
	}

	/**
	 * Inclusive. Treats this contour as a loop if start > end
	 * 
	 * @param start
	 * @param end
	 * @return
	 */
	private List<Point2D> getSubsequence(int start, int end) {
		List<Point2D> list = new ArrayList<Point2D>((end - start + contour.size()) % contour.size());
		if (start <= end) {
			for (int i = start; i <= end; i++) {
				list.add(contour.get(i));
			}
		} else {
			// i < contour.size()-1 to avoid duplicating the start/end node
			// of contour
			for (int i = start; i < contour.size() - 1; i++) {
				list.add(contour.get(i));
			}
			// add it if it's not actually a duplicate
			if (!contour.get(0).equals(contour.get(contour.size() - 1))) {
				list.add(contour.get(contour.size() - 1));
			}
			// add the rest of the points
			for (int i = 0; i <= end; i++) {
				list.add(contour.get(i));
			}
		}
		return list;
	}

	private void forceCanonical() {
		if (!contour.get(0).equals(contour.get(contour.size() - 1))) {
			contour.add(contour.get(0));
		}
		if (!isClockwise()) {
			Collections.reverse(contour);
		}
		reIndex(findUpperLeft());
	}

	/**
	 * Splices another contour into this one. Everything (inclusive) between
	 * thisStart and thisEnd (in clockwise order) will be kept. Everything
	 * (inclusive) from otherStart to otherEnd (in clockwise order) will be placed
	 * in between, like so: </br>
	 * thisStart... (this contour)... thisEnd otherStart ...(other contour piece)...
	 * otherEnd thisStart </br>
	 * to flip the join order, so that the segment of the other contour is in
	 * reversed order, pass in reverse == true</br>
	 * The other contour will remain unchanged, and after this operation this
	 * contour will be in canonical form once more.
	 * 
	 * @param other
	 * @param thisEnd
	 * @param thisStart
	 * @param otherStart
	 * @param otherEnd
	 * @param reverse
	 */
	public void splice(Contour other, Point2D thisStart, Point2D thisEnd, //
			Point2D otherStart, Point2D otherEnd, boolean reverse) {
		int thisStartI = contour.indexOf(thisStart);
		int thisEndI = contour.indexOf(thisEnd);
		int otherStartI = other.contour.indexOf(otherStart);
		int otherEndI = other.contour.indexOf(otherEnd);

		// get points
		List<Point2D> thisList = getSubsequence(thisStartI, thisEndI);
		List<Point2D> otherList = other.getSubsequence(otherStartI, otherEndI);

		System.out.println("Ready to splice: " + thisList.size() + " " + otherList.size());
		System.out.println(thisStartI + " " + thisEndI);

		// update this
		contour.clear();
		contour.addAll(thisList);
		if (reverse) {
			Collections.reverse(otherList);
		}
		contour.addAll(otherList);
		contour.add(contour.get(0));

		// canonical form!
		forceCanonical();
	}

	/**
	 * 
	 * @param startPt
	 * @param toInsert The points to be inserted between startPt and the next
	 *                 clockwise point in this contour
	 */
	public void insert(Point2D startPt, List<Point2D> toInsert) {
		int insertAt = contour.indexOf(startPt);
		contour.addAll(insertAt, toInsert);
		forceCanonical();
	}

	/**
	 * Deletes the inclusive clockwise range between startPt and endPt
	 * 
	 * @requires that both startPt and endPt be part of the contour
	 * 
	 * @param startPt
	 * @param endPt
	 */
	public void delete(Point2D startPt, Point2D endPt) {
		int deleteAt = contour.indexOf(startPt);
		while (!contour.get(deleteAt).equals(endPt)) {
			contour.remove(deleteAt);
			if (deleteAt == contour.size()) {
				deleteAt = 0;
			}
		}
		contour.remove(deleteAt);

	}

	/**
	 * Splits this contour into two. Everything (inclusive) between startPt and
	 * endPt (in clockwise order) will be kept in this contour. Everything
	 * (inclusive) from endPt to startPt (in clockwise order) will be placed in a
	 * new contour, which will be returned. After this operation this contour will
	 * be in canonical form once more.
	 * 
	 * @param startPt
	 * @param endPt
	 * @return
	 */
	public Contour split(Point2D startPt, Point2D endPt) {
		int thisStartI = contour.indexOf(startPt);
		int thisEndI = contour.indexOf(endPt);

		// get points
		List<Point2D> thisList = getSubsequence(thisStartI, thisEndI);
		List<Point2D> otherList = getSubsequence(thisEndI, thisStartI);

		contour = thisList;
		forceCanonical();

		return new Contour(otherList);
	}

	public Path2D asPath(double xOffset) {
		Path2D path2d = new Path2D.Double();
		path2d.moveTo(contour.get(0).getX() + xOffset, contour.get(0).getY());
		int i = 0;
		for (Point2D pt : contour) {
			if (i % 5 == 0) {
				path2d.lineTo(pt.getX() + xOffset, pt.getY());
			}
			i++;
		}
		path2d.lineTo(contour.get(0).getX() + xOffset, contour.get(0).getY());
		return path2d;
	}

	public boolean contains(Point2D pt) {
		return contour.contains(pt);
	}

	public int indexOf(Point2D pt) {
		return contour.indexOf(pt);
	}

	private void reIndex(int inflectionIndex) {

		if (inflectionIndex == 0 || inflectionIndex == contour.size() - 1) {
			return;
		}

		List<Point2D> l1 = new ArrayList<Point2D>(), l2 = new ArrayList<Point2D>();

		for (int i = 1; i < contour.size(); i++) {
			if (i < inflectionIndex) {
				l1.add(contour.get(i));
			} else {
				l2.add(contour.get(i));
			}
		}
		contour.clear();
		contour.addAll(l2);
		contour.addAll(l1);
		contour.add(contour.get(0));
	}

	/**
	 * 
	 * @return The index of the first point to satisfy upper-left criteria
	 */
	private int findUpperLeft() {
		upperLeft = new Point2D.Double(Double.MAX_VALUE, Double.MAX_VALUE);
		int index = -1;
		for (int i = 0; i < contour.size(); i++) {
			Point2D pt = contour.get(i);
			if (pt.getY() < upperLeft.getY() //
					|| (pt.getY() == upperLeft.getY() && pt.getX() < upperLeft.getX())) {
				upperLeft = pt;
				index = i;
			}
		}
		return index;
	}

	private boolean isClockwise() {
		// Source of algorithm:
		// https://stackoverflow.com/questions/1165647/how-to-determine-if-a-list-of-polygon-points-are-in-clockwise-order

		Point2D prev = contour.get(contour.size() - 1);
		double sum = 0;
		for (Point2D pt : contour) {
			sum += (pt.getX() - prev.getX()) * (pt.getY() + prev.getY());
			prev = pt;
		}
		return sum < 0;
	}

	private void removeColinearPts() {
		// if theta <= nearlyZero, the pts are colinear
		// (theta is in radians)
		double nearlyZero = 1.0 / 180.0 * Math.PI;

		Set<Point2D> ptsToRemove = new HashSet<Point2D>();
		Point2D prev = contour.get(contour.size() - 2);
		for (int i = 0; i < contour.size() - 1; i++) {
			// remove duplicate points
			if (contour.get(i).equals(contour.get(i + 1))) {
				contour.remove(i);
				i--;
				continue;
			}
			// check whether they're colinear
			if (getTheta(prev, contour.get(i), contour.get(i + 1)) <= nearlyZero) {
				ptsToRemove.add(contour.get(i));
			} else {
				// the else is important, because it means you're
				// comparing the next point to the new previous
				// point, not the one you've just slated for removal
				prev = contour.get(i);
			}
		}
		contour.removeAll(ptsToRemove);
		// if you removed the start point, make sure the contour
		// is still a loop
		if (!contour.get(0).equals(contour.get(contour.size() - 1))) {
			contour.add(contour.get(0));
		}
	}

	/**
	 * 
	 * @param p one endpoint
	 * @param q middle point
	 * @param r other endpoint
	 * @return 0 if the points are colinear with q in the middle, up to Math.PI if
	 *         they're colinear but q isn't in the middle.
	 */
	private double getTheta(Point2D p, Point2D q, Point2D r) {
		return Math.acos(//
				((q.getX() - p.getX()) * (r.getX() - q.getX()) + (q.getY() - p.getY()) * (r.getY() - q.getY()))//
						/ (p.distance(q) * q.distance(r)));
	}

	/**
	 * 
	 * @return The area of the tight bounding box containing this contour.
	 */
	public double getAreaEstimate() {
		double[] minXY = { Double.MAX_VALUE, Double.MAX_VALUE };
		double[] maxXY = { Double.MIN_VALUE, Double.MIN_VALUE };
		for (Point2D pt : contour) {

			double[] array = { pt.getX(), pt.getY() };
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

	@Override
	public String toString() {
		return contour.toString();
	}

	public String toStringSimple() {
		String str = "";

		for (Point2D pt : contour) {
			str += pt.getX() + "," + pt.getY() + " ";
		}

		return str.trim();
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof Contour)) {
			return false;
		}
		return id == ((Contour) obj).id;
	}
}