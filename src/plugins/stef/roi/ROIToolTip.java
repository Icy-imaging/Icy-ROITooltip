package plugins.stef.roi;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.canvas.Layer;
import icy.gui.main.FocusedViewerListener;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.image.IntensityInfo;
import icy.main.Icy;
import icy.painter.Overlay;
import icy.painter.Painter;
import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginDaemon;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.roi.ROI3D;
import icy.roi.ROIEvent;
import icy.roi.ROIEvent.ROIEventType;
import icy.roi.ROIListener;
import icy.roi.ROIUtil;
import icy.sequence.Sequence;
import icy.system.thread.SingleProcessor;
import icy.util.GraphicsUtil;
import icy.util.StringUtil;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;

/**
 * ROI Tooltip plugin.<br>
 * Display a tool tip with extras informations on focused ROI in Canvas2D.
 * 
 * @author Stephane
 */
public class ROIToolTip extends Plugin implements PluginDaemon, FocusedViewerListener, ROIListener
{
    private class ROICalculator implements Runnable
    {
        public ROICalculator()
        {
            super();
        }

        @Override
        public void run()
        {
            final Sequence seq = activeSequence;
            final ROI roi = focusedROI;

            try
            {
                if ((seq != null) && (roi != null))
                {
                    intensityInfo = ROIUtil.getIntensityInfo(activeSequence, focusedROI);
                    perimeter = Math.round(roi.getPerimeter());
                    volume = Math.round(roi.getVolume());
                }
            }
            catch (Exception e)
            {
                // async process, ROI can change in the meantime
            }

            overlay.painterChanged();
        }
    }

    private class HintOverlay extends Overlay
    {
        public HintOverlay()
        {
            super("ROI tip", OverlayPriority.TOOLTIP_LOW);
        }

        @Override
        public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
        {
            if (canvas instanceof IcyCanvas2D)
            {
                final IcyCanvas2D cnv2d = (IcyCanvas2D) canvas;
                ROI roi = sequence.getFocusedROI();

                if (roi == null)
                {
                    // search in selected ROI
                    final ROI2D selectedRoi = sequence.getSelectedROI2D();

                    // assume this is a focused ROI
                    if ((selectedRoi != null) && (selectedRoi.hasSelectedPoint()))
                        roi = selectedRoi;
                }

                if (roi != null)
                {
                    roiFocused(roi);

                    if (roi instanceof ROI2D)
                    {
                        final Rectangle2D bounds = ((ROI2D) roi).getBounds2D();
                        String text;

                        // if (processor.isProcessing())
                        // updatingMark = "*";
                        // else
                        // updatingMark = "";

                        // draw position and size in the tooltip
                        text = "Position   X: " + StringUtil.toString(bounds.getX(), 1) + "  Y: "
                                + StringUtil.toString(bounds.getY(), 1);
                        text += "\n";
                        text += "Dimension  W: " + StringUtil.toString(bounds.getWidth(), 1) + "  H: "
                                + StringUtil.toString(bounds.getHeight(), 1);
                        text += "\n";

                        text += "Perimeter  " + sequence.calculateSize(perimeter, roi.getDimension() - 1, 5) + " ("
                                + StringUtil.toString(perimeter) + " pixels)";
                        text += "\n";
                        text += "Surface    " + sequence.calculateSize(volume, roi.getDimension(), 5) + " ("
                                + StringUtil.toString(volume, 1) + " pixels)";
                        
                        if (intensityInfo != null)
                        {
                            text += "\n";
                            text += "Intensity";
                            text += "  min: " + StringUtil.toString(intensityInfo.minIntensity, 1);
                            text += "  max: " + StringUtil.toString(intensityInfo.maxIntensity, 1);
                            text += "  mean: " + StringUtil.toString(intensityInfo.meanIntensity, 1);
                        }

                        final Graphics2D g2 = (Graphics2D) g.create();

                        g2.transform(cnv2d.getInverseTransform());
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

                        // default position
                        Point pos = cnv2d.getMousePosition(true);
                        if (pos == null)
                            pos = new Point((int) bounds.getMinX(), (int) bounds.getMaxY());

                        // canvas visible region
                        final Rectangle region = cnv2d.getCanvasVisibleRect();
                        // hint size
                        final Dimension hintSize = GraphicsUtil.getHintSize(g2, text);

                        // get best hint position to be visible
                        pos = GraphicsUtil.getBestPosition(pos, hintSize, region, 8, 8);

                        GraphicsUtil.drawHint(g2, text, pos.x, pos.y, Color.gray, Color.white);

                        g2.dispose();
                    }
                    else if (roi instanceof ROI3D)
                    {
                        // not yet supported

                    }
                }
            }
        }
    }

    Viewer activeViewer;
    Sequence activeSequence;
    ROI focusedROI;

    final HintOverlay overlay;
    final SingleProcessor processor;

    /**
     * ROI calculated infos
     */
    IntensityInfo intensityInfo;
    double perimeter;
    double volume;

    public ROIToolTip()
    {
        super();

        overlay = new HintOverlay();
        intensityInfo = new IntensityInfo();
        processor = new SingleProcessor(true);
    }

    void updateInfos()
    {
        processor.addTask(new ROICalculator());
    }

    @Override
    public void init()
    {
        activeViewer = null;
        activeSequence = null;
        focusedROI = null;

        focusChanged(getFocusedViewer());
        roiFocused((activeSequence != null) ? activeSequence.getFocusedROI() : null);

        Icy.getMainInterface().addFocusedViewerListener(this);
    }

    @Override
    public void run()
    {
        // nothing to do here
    }

    @Override
    public void stop()
    {
        Icy.getMainInterface().removeFocusedViewerListener(this);

        focusChanged(null);
        roiFocused(null);
    }

    public void sequenceActivated(Sequence sequence)
    {
        if (activeSequence != sequence)
        {
            if (activeSequence != null)
                activeSequence.removePainter(overlay);

            activeSequence = sequence;
            roiFocused(null);

            if (activeSequence != null)
                activeSequence.addPainter(overlay);
        }
    }

    public void roiFocused(ROI roi)
    {
        if (focusedROI != roi)
        {
            if (focusedROI != null)
                focusedROI.removeListener(this);

            focusedROI = roi;

            if (focusedROI != null)
                focusedROI.addListener(this);

            updateInfos();
        }
    }

    @Override
    public void roiChanged(ROIEvent event)
    {
        if (event.getType() == ROIEventType.ROI_CHANGED)
            updateInfos();
    }

    @Override
    public void focusChanged(Viewer viewer)
    {

        if (activeViewer != viewer)
        {
            float alpha = 1f;

            if (activeViewer != null)
            {
                final IcyCanvas canvas = activeViewer.getCanvas();
                if (canvas != null)
                {
                    final Layer layer = canvas.getLayer((Painter) overlay);
                    if (layer != null)
                        alpha = layer.getAlpha();
                }
            }

            activeViewer = viewer;

            final Sequence sequence;
            if (activeViewer != null)
                sequence = activeViewer.getSequence();
            else
                sequence = null;

            sequenceActivated(sequence);

            if (activeViewer != null)
            {
                final IcyCanvas canvas = activeViewer.getCanvas();
                if (canvas != null)
                {
                    final Layer layer = canvas.getLayer((Painter) overlay);
                    layer.setAlpha(alpha);
                }
            }
        }
    }

    @Override
    public void focusedViewerChanged(ViewerEvent event)
    {
        // TODO Auto-generated method stub

    }
}
