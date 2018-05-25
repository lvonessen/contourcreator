import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class ContourCreator extends JPanel {

	private List<Shape> contours;
	private List<Shape> transformedContours;
	private List<Shape> majorContours;
	private List<Shape> transformedMajorContours;
	private Path2D.Double contour;
	private List<Triangle> mesh;
	private AffineTransform zoom = new AffineTransform();
	private AffineTransform drag = new AffineTransform();

	private double lakeWashThresh = 4.06;
	private double[] seattleMajorVals = { 3, lakeWashThresh, 4.5395, 5.01, 5.45};

	public static void main(String[] args) throws IOException {

		ContourCreator cc = new ContourCreator("seattle-good.stl");

		JFrame f = new JFrame("Line");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.getContentPane().add("Center", cc);

		f.pack();
		f.setSize(new Dimension(700, 900));
		f.setVisible(true);

	}

	public ContourCreator(String file) throws IOException {
		double maxHeight = 6;// 1.5;
		double stepSize = .1;
		double[] majorVals = seattleMajorVals;

		contours = new ArrayList<Shape>();
		transformedContours = new ArrayList<Shape>();
		majorContours = new ArrayList<Shape>();
		transformedMajorContours = new ArrayList<Shape>();

		Path stlPath = Paths.get(file);

		mesh = STLParser.parseSTLFile(stlPath);

		double[][] bounds = getBounds(mesh);

		AffineTransform affine = new AffineTransform();
		// affine.translate(500,500);

		affine.scale(3, 3);

		affine.translate(0, bounds[1][1]);

		affine.scale(1, -1);

		for (double thresh = 4; /* 0.0000000000001; */thresh < maxHeight; thresh += stepSize) {
			Path2D.Double path = process(thresh);
			// System.out.println();
			contours.add(affine.createTransformedShape(path));
			transformedContours.add(affine.createTransformedShape(path));
			// contours[i
		}

		for (double thresh : majorVals) {
			Path2D.Double path = process(thresh);
			// System.out.println();
			majorContours.add(affine.createTransformedShape(path));
			transformedMajorContours.add(affine.createTransformedShape(path));
		}

		addMouseWheelListener(new ZoomListener());
		addMouseMotionListener(new DragListener());
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;

		boolean col = true;
		g2.setColor(Color.RED);
		for (Shape co : transformedContours) {
			g2.draw(co);// zoom.createTransformedShape(drag.createTransformedShape(co)));
		}
		g2.setColor(Color.BLACK);
		g2.setStroke(new BasicStroke(2));
		for (Shape co : transformedMajorContours) {
			g2.draw(co);// zoom.createTransformedShape(drag.createTransformedShape(co)));
		}
	}

	private Color toggle(boolean tog) {
		if (tog) {
			return Color.RED;
		} else {
			return Color.BLACK;
		}
	}

	private double[][] getBounds(List<Triangle> mesh) {
		double[] min = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
		double[] max = { Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE };
		for (Triangle t : mesh) {
			for (Vec3d vertex : t.getVertices()) {
				double[] array = getArray(vertex);
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

	private double[] getArray(Vec3d pt) {
		return new double[] { pt.x, pt.y, pt.z };
	}

	private Path2D.Double process(double zThresh) throws IOException {
		contour = new Path2D.Double();
		double almostZero = 0.0000;

		for (Triangle t : mesh) {
			int countBelow = 0, countAbove = 0, countAt = 0;
			Vec3d prev = null;

			// special lake washington filter
			if (zThresh == lakeWashThresh) {
				if (t.getVertices()[0].x < 110) {
					continue;
				}
			}

			for (Vec3d vertex : t.getVertices()) {

				if ((vertex.z - zThresh) * (vertex.z - zThresh) <= almostZero) {
					// not sure this is the right thing to do for the "equals" case
					countAt++;
				} else if (vertex.z < zThresh) {
					countBelow++;
				} else {
					countAbove++;
				}
				prev = vertex;
			}

			List<Vec3d> list = new ArrayList<Vec3d>();
			if (countBelow != 3 && countAbove != 3) {

				for (Vec3d vertex : t.getVertices()) {
					// if they're on opposite sides
					if ((zThresh - vertex.z) * (zThresh - prev.z) <= 0) {
						list.add(getIntersect(vertex, prev, zThresh));
					}
					if ((vertex.z - zThresh) * (vertex.z - zThresh) <= almostZero) {
						// not sure this is the right thing to do for the "equals" case
						list.add(vertex);
					}
					prev = vertex;
				}
				if (list.size() < 2) {
					System.out.println(countBelow + " " + zThresh);
					System.out.println(t);
				}
			}
			addToContour(list);
		}
		return contour;
	}

	private void addToContour(List<Vec3d> list) {
		if (list.isEmpty()) {
			return;
		}
		contour.moveTo(list.get(0).x, list.get(0).y);
		for (Vec3d pt : list) {
			if (!pt.equals(list.get(0))) {
				contour.lineTo(pt.x, pt.y);
				return;
			}
		}
	}

	private Vec3d getIntersect(Vec3d pt1, Vec3d pt2, double zThresh) {
		double scale = (zThresh - pt1.z) / (pt2.z - pt1.z);
		double newX = pt1.x + scale * (pt2.x - pt1.x);
		double newY = pt1.y + scale * (pt2.y - pt1.y);

		return new Vec3d(newX, newY, zThresh);
	}

	private Point2D.Double convert(Vec3d pt) {
		return new Point2D.Double(pt.x, pt.y);
	}

	private class ZoomListener implements MouseWheelListener {

		@Override
		public void mouseWheelMoved(MouseWheelEvent arg0) {
			// negative values if the mouse wheel was rotated up/away from the user, and
			// positive values if the mouse wheel was rotated down/ towards the user
			int rot = arg0.getWheelRotation();
			double scale = Math.pow(1.1, rot);
			// System.out.println(rot + " " + scale);

			// does the order have to be reversed here?
			zoom.setToScale(scale, scale);
			zoom.translate(arg0.getX() - scale * arg0.getX(), arg0.getY() - scale * arg0.getY());

			List<Shape> newShapes = new ArrayList<Shape>();
			for (Shape co : transformedContours) {
				newShapes.add(zoom.createTransformedShape(co));
			}
			transformedContours = newShapes;
			newShapes = new ArrayList<Shape>();
			for (Shape co : transformedMajorContours) {
				newShapes.add(zoom.createTransformedShape(co));
			}
			transformedMajorContours = newShapes;
			repaint();
		}

	}

	private class DragListener implements MouseMotionListener {

		Point2D.Double pt = new Point2D.Double(0, 0);

		@Override
		public void mouseDragged(MouseEvent arg0) {
			// drag.translate(arg0.getX() - pt.x, arg0.getY() - pt.y);
			drag.setToTranslation(arg0.getX() - pt.x, arg0.getY() - pt.y);

			List<Shape> newShapes = new ArrayList<Shape>();
			for (Shape co : transformedContours) {
				newShapes.add(drag.createTransformedShape(co));
			}
			transformedContours = newShapes;
			newShapes = new ArrayList<Shape>();
			for (Shape co : transformedMajorContours) {
				newShapes.add(drag.createTransformedShape(co));
			}
			transformedMajorContours = newShapes;

			pt.setLocation(arg0.getX(), arg0.getY());
			repaint();
		}

		@Override
		public void mouseMoved(MouseEvent arg0) {
			// TODO Auto-generated method stub
			pt.setLocation(arg0.getX(), arg0.getY());
		}

	}

}