package plugins.stef.roi;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.gui.main.FocusedSequenceListener;
import icy.main.Icy;
import icy.painter.AbstractPainter;
import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginDaemon;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.util.GraphicsUtil;
import icy.util.StringUtil;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;

/**
 * ROI Hint plugin.<br>
 * Add hint on focused ROI in Canvas2D.
 * 
 * @author Stephane
 */
public class ROIHint extends Plugin implements PluginDaemon, FocusedSequenceListener
{
    private class HintPainter extends AbstractPainter
    {
        public HintPainter()
        {
            super();
        }

        @Override
        public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
        {
            if (canvas instanceof IcyCanvas2D)
            {
                final IcyCanvas2D cnv2d = (IcyCanvas2D) canvas;

                final ROI roi = sequence.getFocusedROI();

                if (roi instanceof ROI2D)
                {
                    final Rectangle2D bounds = ((ROI2D) roi).getBounds2D();

                    // draw position and size in the tooltip
                    final String roiPositionString = "Position       X=" + StringUtil.toString(bounds.getX(), 1)
                            + "  Y=" + StringUtil.toString(bounds.getY(), 1);
                    final String roiBoundingSizeString = "Dimension  W=" + StringUtil.toString(bounds.getWidth(), 1)
                            + "  H=" + StringUtil.toString(bounds.getHeight(), 1);
                    final String text = roiPositionString + "\n" + roiBoundingSizeString;

                    final Point pos = cnv2d.imageToCanvas(mousePos);
                    pos.translate(8, 8);
                    final Font font = new Font("Arial", Font.PLAIN, 12);

                    final Graphics2D g2 = (Graphics2D) g.create();

                    g2.transform(cnv2d.getInverseTransform());
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(font);

                    GraphicsUtil.drawHint(g2, text, pos.x, pos.y, Color.gray, getDisplayColor());

                    g2.dispose();
                }
                else
                {
                    // not yet supported

                }
            }
        }
    }

    private Sequence focusedSequence;
    private final HintPainter painter;

    public ROIHint()
    {
        super();

        painter = new HintPainter();
    }

    @Override
    public void run()
    {

        focusedSequence = null;
        sequenceFocused(getFocusedSequence());

        Icy.getMainInterface().addFocusedSequenceListener(this);
    }

    @Override
    public void stop()
    {
        Icy.getMainInterface().removeFocusedSequenceListener(this);

        sequenceFocused(null);
    }

    @Override
    public void sequenceFocused(Sequence sequence)
    {
        if (focusedSequence != null)
            focusedSequence.removePainter(painter);

        focusedSequence = sequence;

        if (focusedSequence != null)
            focusedSequence.addPainter(painter);
    }

    @Override
    public void focusedSequenceChanged(SequenceEvent event)
    {
        // nothing to do here
    }
}
