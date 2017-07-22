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
	private Path2D.Double contour;
	private List<Triangle> mesh;
	private AffineTransform zoom = new AffineTransform();
	private AffineTransform drag = new AffineTransform();

	public static void main(String[] args) throws IOException {

		ContourCreator cc = new ContourCreator("rawmodel-5627.stl");

		JFrame f = new JFrame("Line");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.getContentPane().add("Center", cc);

		f.pack();
		f.setSize(new Dimension(700, 900));
		f.setVisible(true);

	}

	public ContourCreator(String file) throws IOException {
		int maxHeight = 9;
		double stepSize = .25;

		contours = new ArrayList<Shape>();

		Path stlPath = Paths.get(file);

		mesh = STLParser.parseSTLFile(stlPath);

		double[][] bounds = getBounds(mesh);

		AffineTransform affine = new AffineTransform();
		// affine.translate(500,500);

		affine.scale(3, 3);

		affine.translate(0, bounds[1][1]);

		affine.scale(1, -1);

		for (double thresh = 0;/*0.0000000000001; */thresh < maxHeight; thresh += stepSize) {
			contours.add(affine.createTransformedShape(process(thresh)));
			// contours[i
		}

		addMouseWheelListener(new ZoomListener());
		addMouseMotionListener(new DragListener());
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setColor(Color.RED);
		
		for (Shape co : contours) {
			g2.draw(drag.createTransformedShape(zoom.createTransformedShape(co)));
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

		for (Triangle t : mesh) {
			int countBelow = 0;
			Vec3d prev = null;
			for (Vec3d vertex : t.getVertices()) {
				if (vertex.z <= zThresh) {
					countBelow++;
				}
				prev = vertex;
			}
			if (countBelow % 3 != 0) {
				List<Vec3d> list = new ArrayList<Vec3d>();
				for (Vec3d vertex : t.getVertices()) {
					// if they're on opposite sides
					if ((zThresh - vertex.z) * (zThresh - prev.z) <= 0) {
						list.add(getIntersect(vertex, prev, zThresh));
					}
					prev = vertex;
				}
				if (list.size() < 2) {
					System.out.println(countBelow + " " + zThresh);
					System.out.println(t);
				}
				addToContour(list);
			}
		}
		return contour;
	}

	private void addToContour(List<Vec3d> list) {
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
			System.out.println(rot + " " + scale);
			zoom.scale(scale, scale);
			repaint();
		}

	}
	
	private class DragListener implements MouseMotionListener {
		
		Point2D.Double pt = new Point2D.Double(0, 0);

		@Override
		public void mouseDragged(MouseEvent arg0) {
			drag.translate(arg0.getX()-pt.x, arg0.getY() - pt.y);
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