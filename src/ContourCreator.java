import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class ContourCreator extends JPanel {

	private List<Shape> contours;
	private List<Shape> majorContours;
	// private AffineTransform zoom = new AffineTransform();
	// private AffineTransform drag = new AffineTransform();
	private AffineTransform at = new AffineTransform();

	Point2D.Double mouseLocation = new Point2D.Double(0, 0);

	private double lakeWashThresh = 4.06;
	private double[] seattleMajorVals = { 3, //
			lakeWashThresh, //
			4.5395, 5.01, 5.35 }; // 5.45
	private Color[] seattleColors = { Color.BLACK, //
			Color.BLACK, //
			Color.RED, Color.MAGENTA, Color.BLUE };

	public static void main(String[] args) throws IOException {

		ContourCreator cc = new ContourCreator("seattle-good.stl");

		JFrame f = new JFrame("Line");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.getContentPane().add("Center", cc);

		f.pack();
		f.setSize(new Dimension(700, 900));
		f.setVisible(true);

		AffineTransform at = new AffineTransform();

		at.scale(1, 2);
		System.out.println(at);
		at.translate(3, 4);
		System.out.println(at);
		at.scale(5, 6);
		System.out.println(at);
		at.translate(7, 8);
		System.out.println(at);

	}

	public ContourCreator(String file) throws IOException {

		TopoMap tm = new TopoMap(file);

		double maxHeight = 6;// 1.5;
		double stepSize = .1;

		contours = new ArrayList<Shape>();
		majorContours = new ArrayList<Shape>();

		double[][] bounds = tm.getBounds();
		System.out.println(Arrays.toString(bounds[0]));
		System.out.println(Arrays.toString(bounds[1]));

		AffineTransform affine = new AffineTransform();

		// this mirrors the map so it's right side up, etc
		affine.scale(3, 3);
		affine.translate(0, bounds[1][1]);
		affine.scale(1, -1);

		tm.initialize(seattleMajorVals);
		List<Path2D.Double> cts = tm.asPaths();
		for (Path2D.Double ct : cts) {
			majorContours.add(affine.createTransformedShape(ct));
		}

		// get minor vals
		tm.initialize(4, maxHeight, stepSize);
		cts = tm.asPaths();
		for (Path2D.Double ct : cts) {
			contours.add(affine.createTransformedShape(ct));
		}

		addMouseWheelListener(new ZoomListener());
		addMouseMotionListener(new DragListener());
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		// AffineTransform atOrig = g2.getTransform();
		// g2.transform(at);

		boolean col = true;
		g2.setColor(Color.LIGHT_GRAY);
		for (Shape co : contours) {
			g2.draw(at.createTransformedShape(co));// zoom.createTransformedShape(drag.createTransformedShape(co)));
		}
		g2.setColor(Color.BLACK);
		g2.setStroke(new BasicStroke(2));
		for (int i = 0; i < majorContours.size(); i++) {
			Shape co = majorContours.get(i);
			g2.setColor(seattleColors[i]);
			g2.draw(at.createTransformedShape(co));
		}

		drawWithColor(g2, Color.BLACK, mouseLocation);

		// g2.setTransform(atOrig);
	}

	private Color toggle(boolean tog) {
		if (tog) {
			return Color.RED;
		} else {
			return Color.BLACK;
		}
	}

	private class ZoomListener implements MouseWheelListener {

		@Override
		public void mouseWheelMoved(MouseWheelEvent arg0) {
			// negative values if the mouse wheel was rotated up/away from the
			// user, and
			// positive values if the mouse wheel was rotated down/ towards the
			// user
			int rot = -arg0.getWheelRotation();
			double scale = Math.pow(1.1, rot);
			// System.out.println(rot + " " + scale);

			// no scaling, no shifting
			Point2D.Double pt1 = null;
			try {
				pt1 = (Double) at.inverseTransform(mouseLocation, null);
			} catch (NoninvertibleTransformException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			at.scale(scale, scale);

			Point2D.Double pt2 = (Double) at.transform(pt1, null);

			at.translate((-pt2.x + mouseLocation.x) / at.getScaleX(), (-pt2.y + mouseLocation.y) / at.getScaleY());

			// zoom.setToScale(scale, scale);
			// zoom.translate(arg0.getX() - scale * arg0.getX(), arg0.getY() -
			// scale * arg0.getY());
			// at.translate(arg0.getX() - scale * arg0.getX(), arg0.getY() -
			// scale * arg0.getY());

			repaint();
		}

	}

	private class ClickListener implements MouseListener {

		@Override
		public void mouseClicked(MouseEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void mouseEntered(MouseEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void mouseExited(MouseEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void mousePressed(MouseEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void mouseReleased(MouseEvent arg0) {
			// TODO Auto-generated method stub

		}

	}

	private class DragListener implements MouseMotionListener {

		@Override
		public void mouseDragged(MouseEvent arg0) {
			// drag.translate(arg0.getX() - pt.x, arg0.getY() - pt.y);
			// drag.setToTranslation
			at.translate((arg0.getX() - mouseLocation.x) / at.getScaleX(),
					(arg0.getY() - mouseLocation.y) / at.getScaleY());

			// List<Shape> newShapes = new ArrayList<Shape>();
			// for (Shape co : contours) {
			// newShapes.add(drag.createTransformedShape(co));
			// }
			// contours = newShapes;
			// newShapes = new ArrayList<Shape>();
			// for (Shape co : majorContours) {
			// newShapes.add(drag.createTransformedShape(co));
			// }
			// majorContours = newShapes;
			//
			// pt.setLocation(arg0.getX(), arg0.getY());

			mouseLocation.setLocation(arg0.getX(), arg0.getY());
			repaint();
		}

		@Override
		public void mouseMoved(MouseEvent arg0) {
			// TODO Auto-generated method stub
			mouseLocation.setLocation(arg0.getX(), arg0.getY());
			repaint();
		}

	}

	public void drawWithColor(Graphics2D g, Color color, Point2D.Double pt) {
		Color c = g.getColor();
		g.setColor(color);
		AffineTransform at = g.getTransform();

		// half of final width/height in pixels
		double crossWidth = 3, x = pt.x, y = pt.y;

		// change so affine transform doesn't ruin
		double xDiff = crossWidth / at.getScaleX(), yDiff = crossWidth / at.getScaleY();

		// draw
		Line2D.Double l = new Line2D.Double(x - xDiff, y, x + xDiff, y);
		g.draw(l);
		l = new Line2D.Double(x, y - yDiff, x, y + yDiff);
		g.draw(l);
		g.setColor(c);
	}

}