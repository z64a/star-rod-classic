package game.map.config;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;

import app.Environment;
import app.SwingUtils;
import game.map.config.MapConfigTable.AreaConfig;
import game.map.config.MapConfigTable.MapConfig;
import game.map.config.MapConfigTable.Resource;
import net.miginfocom.swing.MigLayout;

public class MapTableTreeCellRenderer extends JPanel implements TreeCellRenderer
{
	private LevelEditor editor;

	private boolean selected;
	private boolean hasFocus;
	private boolean drawsFocusBorderAroundIcon;
	private boolean drawDashedFocusIndicator;

	private JLabel iconLabel;
	private JLabel nameLabel;
	private JLabel nickLabel;

	// Icons
	private transient Icon closedIcon;
	private transient Icon leafIcon;
	private transient Icon openIcon;

	// Colors
	private Color textSelectionColor;
	private Color textNonSelectionColor;
	private Color backgroundSelectionColor;
	private Color backgroundNonSelectionColor;
	private Color borderSelectionColor;

	private Color treeBGColor;
	private Color focusBGColor;

	private boolean isDropCell;
	private boolean fillBackground;

	private boolean constructed;

	private static final Dimension DEFAULT_SIZE = new Dimension(230, 32);

	@Override
	public Dimension getPreferredSize()
	{
		return DEFAULT_SIZE;
	}

	public MapTableTreeCellRenderer(LevelEditor editor)
	{
		super();
		this.editor = editor;
		iconLabel = new JLabel(Environment.ICON_ERROR, JLabel.CENTER);
		nameLabel = new JLabel();
		nickLabel = new JLabel();

		setLayout(new MigLayout("ins 0, fillx"));
		add(iconLabel, "w 16!");
		add(nameLabel, "w 60::");
		add(nickLabel, "growx, pushx");

		setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

		setOpaque(false);

		constructed = true;
	}

	@Override
	public void updateUI()
	{
		super.updateUI();

		if (!constructed || (leafIcon instanceof UIResource)) {
			leafIcon = UIManager.getIcon("Tree.leafIcon");
		}
		if (!constructed || (closedIcon instanceof UIResource)) {
			closedIcon = UIManager.getIcon("Tree.closedIcon");
		}
		if (!constructed || (openIcon instanceof UIManager)) {
			openIcon = UIManager.getIcon("Tree.openIcon");
		}
		if (!constructed || (textSelectionColor instanceof UIResource)) {
			textSelectionColor = UIManager.getColor("Tree.selectionForeground");
		}
		if (!constructed || (textNonSelectionColor instanceof UIResource)) {
			textNonSelectionColor = UIManager.getColor("Tree.textForeground");
		}
		if (!constructed || (backgroundSelectionColor instanceof UIResource)) {
			backgroundSelectionColor = UIManager.getColor("Tree.selectionBackground");
		}
		if (!constructed || (backgroundNonSelectionColor instanceof UIResource)) {
			backgroundNonSelectionColor = UIManager.getColor("Tree.textBackground");
		}
		if (!constructed || (borderSelectionColor instanceof UIResource)) {
			borderSelectionColor = UIManager.getColor("Tree.selectionBorderColor");
		}

		fillBackground = UIManager.getBoolean("Tree.rendererFillBackground");

		drawsFocusBorderAroundIcon = UIManager.getBoolean("Tree.drawsFocusBorderAroundIcon");
		drawDashedFocusIndicator = UIManager.getBoolean("Tree.drawDashedFocusIndicator");

		setName("Tree.cellRenderer");
	}

	@Override
	public Component getTreeCellRendererComponent(
		JTree tree, Object node,
		boolean selected, boolean expanded,
		boolean leaf, int row, boolean hasFocus)
	{

		Object obj = ((DefaultMutableTreeNode) node).getUserObject();
		this.hasFocus = hasFocus;
		this.selected = selected;

		if (obj instanceof AreaConfig area) {
			if (expanded)
				iconLabel.setIcon(openIcon);
			else
				iconLabel.setIcon(closedIcon);

			nameLabel.setText(area.name);
			nickLabel.setText("<html><i>" + area.nickname + "</i></html>");

			Color textColor = (area.invalidName) ? SwingUtils.getRedTextColor() : null;
			nameLabel.setForeground(textColor);
			nickLabel.setForeground(textColor);
		}
		else if (obj instanceof MapConfig map) {
			iconLabel.setIcon(leafIcon);
			nameLabel.setText(map.name);
			nickLabel.setText("<html><i>" + map.nickname + "</i></html>");

			Color textColor = (map.invalidName) ? SwingUtils.getRedTextColor() : null;
			nameLabel.setForeground(textColor);
			nickLabel.setForeground(textColor);
		}
		else if (obj instanceof Resource res) {
			iconLabel.setIcon(leafIcon);
			nameLabel.setText(res.name);
			nickLabel.setText("<html><i>" + res.nickname + "</i></html>");

			Color textColor = (res.invalidName) ? SwingUtils.getRedTextColor() : null;
			nameLabel.setForeground(textColor);
			nickLabel.setForeground(textColor);
		}
		else
			iconLabel.setIcon(leafIcon);

		return this;
	}

	@Override
	public void paint(Graphics g)
	{
		Color bColor;

		if (isDropCell) {
			bColor = UIManager.getColor("Tree.dropCellBackground");
			if (bColor == null) {
				bColor = backgroundSelectionColor;
			}
		}
		else if (selected) {
			bColor = backgroundSelectionColor;
		}
		else {
			bColor = backgroundNonSelectionColor;
			if (bColor == null) {
				bColor = getBackground();
			}
		}

		int imageOffset = -1;
		if (bColor != null && fillBackground) {
			imageOffset = getLabelStart();
			g.setColor(bColor);
			if (getComponentOrientation().isLeftToRight()) {
				g.fillRect(imageOffset, 0, getWidth() - imageOffset,
					getHeight());
			}
			else {
				g.fillRect(0, 0, getWidth() - imageOffset,
					getHeight());
			}
		}

		if (hasFocus) {
			if (drawsFocusBorderAroundIcon) {
				imageOffset = 0;
			}
			else if (imageOffset == -1) {
				imageOffset = getLabelStart();
			}
			if (getComponentOrientation().isLeftToRight()) {
				paintFocus(g, imageOffset, 0, getWidth() - imageOffset,
					getHeight(), bColor);
			}
			else {
				paintFocus(g, 0, 0, getWidth() - imageOffset, getHeight(), bColor);
			}
		}

		super.paint(g);
	}

	private void paintFocus(Graphics g, int x, int y, int w, int h, Color notColor)
	{
		Color bsColor = borderSelectionColor;
		if (bsColor != null && (selected || !drawDashedFocusIndicator)) {
			g.setColor(bsColor);
			g.drawRect(x, y, w - 1, h - 1);
		}
		if (drawDashedFocusIndicator && notColor != null) {
			if (treeBGColor != notColor) {
				treeBGColor = notColor;
				focusBGColor = new Color(~notColor.getRGB());
			}
			g.setColor(focusBGColor);
			BasicGraphicsUtils.drawDashedRect(g, x, y, w, h);
		}
	}

	private int getLabelStart()
	{
		//		return iconLabel.getWidth() + Math.max(0, iconLabel.getIconTextGap() - 1);
		return 0;
	}
}
