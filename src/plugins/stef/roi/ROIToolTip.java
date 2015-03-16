package plugins.stef.roi;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.canvas.Layer;
import icy.gui.main.ActiveViewerListener;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.image.IntensityInfo;
import icy.main.Icy;
import icy.math.MathUtil;
import icy.painter.Overlay;
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
import java.util.ArrayList;
import java.util.List;

/**
 * ROI Tooltip plugin.<br>
 * Display a tool tip with extras informations on focused ROI in Canvas2D.
 * 
 * @author Stephane
 */
public class ROIToolTip extends Plugin implements PluginDaemon, ActiveViewerListener, ROIListener
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
                    points = MathUtil.roundSignificant(roi.getNumberOfPoints(), 2, true);
                    contourPoints = MathUtil.roundSignificant(roi.getNumberOfContourPoints(), 2, true);
                    perimeter = ROIUtil.getPerimeter(activeSequence, roi);
                    area = ROIUtil.getArea(activeSequence, roi);
                    surfaceArea = ROIUtil.getSurfaceArea(activeSequence, roi);
                    volume = ROIUtil.getVolume(activeSequence, roi);
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

        private String enlarge(String text, int len)
        {
            String result = text;
            while (result.length() < len)
                result += ' ';
            return result;
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
                        final List<String> text = new ArrayList<String>();
                        final String perim = perimeter;
                        final String surfArea = surfaceArea;

                        // if (processor.isProcessing())
                        // updatingMark = "*";
                        // else
                        // updatingMark = "";

                        // draw roi informations in the tooltip
                        text.add("Position X " + StringUtil.toString(bounds.getX(), 1));
                        text.add("Size X     " + StringUtil.toString(bounds.getWidth(), 1));
                        text.add("Interior   " + StringUtil.toString(points) + " px");
                        if (!StringUtil.isEmpty(perim))
                            text.add("Perimeter  " + perim);
                        if (!StringUtil.isEmpty(surfArea))
                            text.add("Surf. area " + surfArea);

                        int maxLength = 0;
                        for (String t : text)
                            maxLength = Math.max(maxLength, t.length());
                        maxLength += 2;

                        String tooltipText = "";
                        int ind = 0;

                        tooltipText = enlarge(text.get(ind++), maxLength);
                        tooltipText += "Position Y " + StringUtil.toString(bounds.getY(), 1) + "\n";
                        tooltipText += enlarge(text.get(ind++), maxLength);
                        tooltipText += "Size Y     " + StringUtil.toString(bounds.getHeight(), 1) + "\n";
                        tooltipText += enlarge(text.get(ind++), maxLength);
                        tooltipText += "Contour    " + StringUtil.toString(contourPoints) + " px" + "\n";

                        if (!StringUtil.isEmpty(perim))
                        {
                            tooltipText += enlarge(text.get(ind++), maxLength);
                            tooltipText += "Area       " + area + "\n";
                        }
                        if (!StringUtil.isEmpty(surfArea))
                        {
                            tooltipText += enlarge(text.get(ind++), maxLength);
                            tooltipText += "Volume     " + volume + "\n";
                        }

                        if (intensityInfo != null)
                        {
                            tooltipText += "Intensity  ";
                            tooltipText += "min: " + StringUtil.toString(intensityInfo.minIntensity, 1) + "  ";
                            tooltipText += "max: " + StringUtil.toString(intensityInfo.maxIntensity, 1) + "  ";
                            tooltipText += "mean: " + StringUtil.toString(intensityInfo.meanIntensity, 1);
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
                        final Dimension hintSize = GraphicsUtil.getHintSize(g2, tooltipText);

                        // get best hint position to be visible
                        pos = GraphicsUtil.getBestPosition(pos, hintSize, region, 8, 8);

                        GraphicsUtil.drawHint(g2, tooltipText, pos.x, pos.y, Color.gray, Color.white);

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
    double points;
    double contourPoints;
    String perimeter;
    String area;
    String surfaceArea;
    String volume;

    public ROIToolTip()
    {
        super();

        overlay = new HintOverlay();
        intensityInfo = new IntensityInfo();
        processor = new SingleProcessor(true);
    }

    void updateInfos()
    {
        processor.submit(new ROICalculator());
    }

    @Override
    public void init()
    {
        activeViewer = null;
        activeSequence = null;
        focusedROI = null;

        viewerActivated(getActiveViewer());
        roiFocused((activeSequence != null) ? activeSequence.getFocusedROI() : null);

        Icy.getMainInterface().addActiveViewerListener(this);
    }

    @Override
    public void run()
    {
        // nothing to do here
    }

    @Override
    public void stop()
    {
        Icy.getMainInterface().removeActiveViewerListener(this);

        viewerActivated(null);
        roiFocused(null);
    }

    public void sequenceActivated(Sequence sequence)
    {
        if (activeSequence != sequence)
        {
            if (activeSequence != null)
                activeSequence.removeOverlay(overlay);

            activeSequence = sequence;
            roiFocused(null);

            if (activeSequence != null)
                activeSequence.addOverlay(overlay);
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
    public void viewerActivated(Viewer viewer)
    {
        if (activeViewer != viewer)
        {
            float alpha = 1f;

            if (activeViewer != null)
            {
                final IcyCanvas canvas = activeViewer.getCanvas();
                if (canvas != null)
                {
                    final Layer layer = canvas.getLayer(overlay);
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
                    final Layer layer = canvas.getLayer(overlay);
                    layer.setAlpha(alpha);
                }
            }
        }
    }

    @Override
    public void viewerDeactivated(Viewer viewer)
    {
        // nothing here
    }

    @Override
    public void activeViewerChanged(ViewerEvent event)
    {
        // nothing here
    }
}