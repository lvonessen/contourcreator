import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
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
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JPanel;

// get stl files from 
// http://jthatch.com/Terrain2STL/

// laser color key: red line cut, blue line etch, black fill etch shape
public class ContourCreator extends JPanel {

	private List<Shape> minorContours;
	private List<Shape> majorContours;
	// private AffineTransform zoom = new AffineTransform();
	// private AffineTransform drag = new AffineTransform();
	private AffineTransform at = new AffineTransform();
	private TopoMap tm;
	
	private List<Point2D.Double> importantPoints = new ArrayList<Point2D.Double>();

	private int index = 0;

	Point2D.Double mouseLocation = new Point2D.Double(0, 0), query = new Point2D.Double(0, 0),
			closest = new Point2D.Double(0, 0);

	private double lakeWashThresh = 4.06;
	private double[] seattleMajorVals = { 3, //
			lakeWashThresh, //
			4.5395, 4.967, 5.35 }; // 5.01, 5.45
	private Color[] seattleColors = { //Color.CYAN,//
			Color.BLACK, //
			 Color.GREEN, //
			Color.RED, Color.MAGENTA, Color.BLUE };

	public static void main(String[] args) throws IOException {

		String file = "SLC.stl";
		file = "seattle-good.stl";

		ContourCreator cc = new ContourCreator(file);

		JFrame f = new JFrame("Line");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.getContentPane().add("Center", cc);

		f.pack();
		f.setSize(new Dimension(700, 900));
		f.setVisible(true);

	}

	public ContourCreator(String file) throws IOException {

		tm = new TopoMap(file);

		// 15.7681

		double minHeight = 4;// 14.8;// 4;
		double maxHeight = 6;// 14.86;// 6;
		double stepSize = .1;// .005;// .1;
		// 14.86 for antelope island
		// 15.7681 (15.769) for utah lake
		// fuck if I know for the great salt lake
		double[] majorVals = seattleMajorVals;// {14.8, 14.86,
												// 15.769};//seattleMajorVals;

		minorContours = new ArrayList<Shape>();
		majorContours = new ArrayList<Shape>();

		double[][] bounds = tm.getBounds();
		System.out.println(Arrays.toString(bounds[0]));
		System.out.println(Arrays.toString(bounds[1]));

		at = new AffineTransform();

		// this scales the map so it's bigger from the get go
		at.scale(2, 2);

		// get minor vals
		tm.initialize(minHeight, maxHeight, stepSize);
		//tm.initialize("");
		minorContours.addAll(tm.asPaths());
		//tm.writeSVGFile("compass-rose2.svg");


		tm.initialize(majorVals);
		tm.writeSVGFile("seattle.svg");
		majorContours.addAll(tm.asPaths());
		int i=0;
		for (Shape s:tm.asSimplePaths(0)){
			importantPoints.add((Point2D.Double)((Path2D.Double)s).getCurrentPoint());
			i++;
			if (i==3){
				break;
			}
		}
		//Collections.reverse(majorContours);
		
		Point2D.Double[] lockPts = { //
				new Point2D.Double(32.85014858461594, 85.8465006016415),
				new Point2D.Double(33.63252302035765, 86.0098480645299),
				new Point2D.Double(33.45732049452805, 89.74933161025403), //
				new Point2D.Double(33.14531709838673, 89.0) };

		importantPoints.addAll(Arrays.asList(lockPts));
		
		tm.preComputeSearchMap();
		tm.writePaths("seattle-contours2.obj");

//		tm.preComputeSearchMap();

		setFocusable(true);
		addMouseWheelListener(new ZoomListener());
		DragListener dl = new DragListener();
		addMouseMotionListener(dl);
		addMouseListener(new ClickListener());
		addKeyListener(new KeysListener());
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		// AffineTransform atOrig = g2.getTransform();
		// g2.transform(at);

		boolean col = true;
		g2.setColor(Color.LIGHT_GRAY);
		for (Shape co : minorContours) {
			g2.draw(at.createTransformedShape(co));// zoom.createTransformedShape(drag.createTransformedShape(co)));
		}
		g2.setColor(Color.BLACK);
		g2.setStroke(new BasicStroke(2));

		for (index = 0; index < majorContours.size(); index++)//
		{
			Shape co = majorContours.get(index);
			g2.setColor(new Color(co.hashCode() & 0xFFF0));
			g2.setColor(seattleColors[index]);
			g2.draw(at.createTransformedShape(co));
		}

		int i = 0;
//		// draw off to the right
//		for (Shape co : selectionContours) {
//
//			g2.setColor(Color.RED);
//			g2.draw(at.createTransformedShape(co));
//
//			g2.setColor(Color.BLACK);
//			double[] pts = new double[6];
//			co.getPathIterator(at).currentSegment(pts);
//			g2.drawString("" + co.hashCode(), (float) pts[0], (float) pts[1]);
//
//			i++;
//		}

		drawWithColor(g2, Color.BLACK, mouseLocation);
		drawWithColor(g2, Color.RED, (Point2D.Double) at.transform(query, null));
		drawWithColor(g2, Color.BLACK, (Point2D.Double) at.transform(closest, null));
		
		for (Point2D.Double pt : importantPoints){
			drawWithColor(g2, Color.BLUE, (Point2D.Double) at.transform(pt, null));
			//drawWithColor(g2, Color.BLACK, pt);
		}

		g2.setColor(Color.BLACK);
		g2.drawString("" + index, 10, 10);

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
			Point2D.Double inv = null;
			try {
				inv = (Double) at.inverseTransform(mouseLocation, null);
			} catch (NoninvertibleTransformException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Point2D.Double closestPt = tm.query(inv);
			System.out.println("Query: " + inv + "   Closest: " + closestPt);
			if (inv != null && closestPt != null) {
				query = inv;
				closest = closestPt;
				repaint();
			}
		}

		@Override
		public void mouseEntered(MouseEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void mouseExited(MouseEvent arg0) {
			// TODO Auto-generated method stub
			index++;
			repaint();
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

		Point2D.Double dragStart, dragEnd;

		@Override
		public void mouseDragged(MouseEvent arg0) {
			// only drag the map if shift isn't being held down
			if (!isShiftDown) {
				at.translate((arg0.getX() - mouseLocation.x) / at.getScaleX(),
						(arg0.getY() - mouseLocation.y) / at.getScaleY());
			}
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

	private boolean isShiftDown = false;

	private class KeysListener implements KeyListener {

		@Override
		public void keyPressed(KeyEvent e) {
			// System.out.println("Something happened +" + e.getKeyChar() +
			// "+"+e.getKeyCode());
			// if (e.getKeyChar() == 's') {
			// index++;
			// repaint();
			// }
			if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
				isShiftDown = true;
				System.out.println("Shift is down");
			}

			// TODO Auto-generated method stub

		}

		@Override
		public void keyReleased(KeyEvent e) {
			if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
				isShiftDown = false;
				System.out.println("Shift is up");
			}
		}

		@Override
		public void keyTyped(KeyEvent e) {
			System.out.println("Something happened +" + e.getKeyChar() + "+");
			if (e.getKeyChar() == 's') {
				index++;
				repaint();
			}
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