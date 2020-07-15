import java.awt.Dimension;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

public class MenuPanel extends JPanel {

    DefaultListModel<Double> listModel;

    JList<Double> list;

    JButton addButton, removeButton;

    JTextField newContour;

    JLabel title;

    public MenuPanel(final List<Double> majorContours) {
        super();

        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

        setUpContourSection(majorContours);
    }

    private void setUpContourSection(List<Double> majorContours) {
        title = new JLabel("Major contours");
        add(title);

        listModel = new DefaultListModel<Double>();
        for (final double contour : majorContours) {
            listModel.addElement(contour);
        }
        list = new JList<Double>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL);
        list.setVisibleRowCount(-1);
        final JScrollPane listScroller = new JScrollPane(list);
        listScroller.setPreferredSize(new Dimension(250, 80));
        add(listScroller);

        removeButton = new JButton("Remove contour");
        removeButton.addActionListener(e -> {
            int index = list.getSelectedIndex();
            listModel.remove(index);

            int size = listModel.getSize();

            if (size == 0) { // Nobody's left, disable firing.
                removeButton.setEnabled(false);

            } else { // Select an index.
                if (index == listModel.getSize()) {
                    // removed item in last position
                    index--;
                }

                list.setSelectedIndex(index);
                list.ensureIndexIsVisible(index);
            }
        });
        add(removeButton);

        newContour = new JTextField();
        add(newContour);
        addButton = new JButton("Add contour");
        addButton.addActionListener(e -> {
            double contour = 0;
            try {
                contour = Double.parseDouble(newContour.getText());
            } catch (NumberFormatException ex) {
                newContour.requestFocusInWindow();
                newContour.selectAll();
                return;
            }

            // Insert in sorted order.
            int i = 0;
            for (; i < listModel.size(); i++) {
                if (contour < listModel.getElementAt(i)) {
                    break;
                }
            }
            listModel.insertElementAt(contour, i);

            // Reset the text field.
            newContour.requestFocusInWindow();
            newContour.setText("");

            // Select the new item and make it visible.
            list.setSelectedIndex(i);
            list.ensureIndexIsVisible(i);
        });
        add(addButton);
    }

}