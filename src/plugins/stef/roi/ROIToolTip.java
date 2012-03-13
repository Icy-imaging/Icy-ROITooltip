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
import icy.roi.ROI3D;
import icy.roi.ROIEvent;
import icy.roi.ROIEvent.ROIEventType;
import icy.roi.ROIListener;
import icy.sequence.Sequence;
import icy.sequence.SequenceDataIterator;
import icy.sequence.SequenceEvent;
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
public class ROIToolTip extends Plugin implements PluginDaemon, FocusedSequenceListener, ROIListener
{
    private static class ROIExtraInfos
    {
        public long numPixels;
        public double area;
        public double minIntensity;
        public double maxIntensity;
        public double meanIntensity;

        public ROIExtraInfos()
        {
            super();
        }
    }

    private class ROICalculator implements Runnable
    {
        public ROICalculator()
        {
            super();
        }

        @Override
        public void run()
        {
            try
            {
                if ((focusedSequence != null) && (focusedROI != null))
                {
                    final double psx = focusedSequence.getPixelSizeX();
                    final double psy = focusedSequence.getPixelSizeY();
                    final double psz = focusedSequence.getPixelSizeZ();
                    final double tot;

                    // preliminary stuff to display pixel surface
                    if (focusedROI instanceof ROI3D)
                        tot = psx * psy * psz;
                    else
                        tot = psx * psy;

                    final SequenceDataIterator it = new SequenceDataIterator(focusedSequence, focusedROI);

                    long numPixels = 0;
                    double min = Double.MAX_VALUE;
                    double max = Double.MIN_VALUE;
                    double sum = 0;

                    // faster to do all calculation in a single iteration run
                    while (!it.isDone())
                    {
                        // verify if we can abort calculation...
                        if (((numPixels & 0xFFFF) == 0) && processor.hasWaitingTasks())
                            return;

                        final double value = it.getAndNext();

                        if (value < min)
                            min = value;
                        if (value > max)
                            max = value;
                        sum += value;
                        numPixels++;
                    }

                    if (numPixels > 0)
                    {
                        roiInfos.area = numPixels * tot;
                        roiInfos.minIntensity = min;
                        roiInfos.maxIntensity = max;
                        roiInfos.meanIntensity = sum / numPixels;
                    }
                    else
                    {
                        roiInfos.area = 0d;
                        roiInfos.minIntensity = 0d;
                        roiInfos.maxIntensity = 0d;
                        roiInfos.meanIntensity = 0d;
                    }
                    roiInfos.numPixels = numPixels;

                    // roiInfos.numPixels = DataIteratorMath.count(it);
                    //
                    // if (roiInfos.numPixels > 0)
                    // {
                    // roiInfos.area = roiInfos.numPixels * tot;
                    // roiInfos.minIntensity = DataIteratorMath.min(it);
                    // roiInfos.maxIntensity = DataIteratorMath.max(it);
                    // roiInfos.meanIntensity = DataIteratorMath.mean(it);
                    // }
                    // else
                    // {
                    // roiInfos.area = 0d;
                    // roiInfos.minIntensity = 0d;
                    // roiInfos.maxIntensity = 0d;
                    // roiInfos.meanIntensity = 0d;
                    // }
                }
                else
                {

                }
            }
            catch (Exception e)
            {
                // we can have exception as the process can be really long
                // and size modified during this period
            }

            // calculation done, notify painter changed
            painter.changed();
        }
    }

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
                ROI roi = sequence.getFocusedROI();

                if (roi == null)
                {
                    // search in selected ROI
                    final ROI2D selectedRoi = sequence.getSelectedROI2D();

                    // assume this is a focused roi
                    if ((selectedRoi != null) && (selectedRoi.hasSelectedPoint()))
                        roi = selectedRoi;
                }

                if (roi != null)
                {
                    roiFocused(roi);

                    if (roi instanceof ROI2D)
                    {
                        final Rectangle2D bounds = ((ROI2D) roi).getBounds2D();
                        final String updatingMark;
                        String text;

                        if (processor.isProcessing())
                            updatingMark = "*";
                        else
                            updatingMark = "";

                        // draw position and size in the tooltip
                        text = "Position    X=" + StringUtil.toString(bounds.getX(), 1) + "  Y="
                                + StringUtil.toString(bounds.getY(), 1);
                        text += "\n";
                        text += "Dimension   W=" + StringUtil.toString(bounds.getWidth(), 1) + "  H="
                                + StringUtil.toString(bounds.getHeight(), 1);
                        text += "\n";

                        text += "Pixels: " + StringUtil.toString(roiInfos.numPixels) + updatingMark;
                        text += "  Surface: " + StringUtil.toString(roiInfos.area, 1) + updatingMark;
                        text += "\n";
                        text += "Intensity";
                        text += "  min: " + StringUtil.toString(roiInfos.minIntensity, 1) + updatingMark;
                        text += "  max: " + StringUtil.toString(roiInfos.maxIntensity, 1) + updatingMark;
                        text += "  mean: " + StringUtil.toString(roiInfos.meanIntensity, 1) + updatingMark;

                        final Graphics2D g2 = (Graphics2D) g.create();

                        g2.transform(cnv2d.getInverseTransform());
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g2.setFont(new Font("Arial", Font.PLAIN, 12));

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
                    else
                    {
                        // not yet supported

                    }
                }
            }
        }
    }

    Sequence focusedSequence;
    ROI focusedROI;

    final HintPainter painter;
    final SingleProcessor processor;
    final ROIExtraInfos roiInfos;

    public ROIToolTip()
    {
        super();

        painter = new HintPainter();
        processor = new SingleProcessor(true);
        roiInfos = new ROIExtraInfos();
    }

    void updateInfos()
    {
        processor.addTask(new ROICalculator());
    }

    @Override
    public void run()
    {
        focusedSequence = null;
        focusedROI = null;
        sequenceFocused(getFocusedSequence());
        roiFocused((focusedSequence != null) ? focusedSequence.getFocusedROI() : null);

        Icy.getMainInterface().addFocusedSequenceListener(this);
    }

    @Override
    public void stop()
    {
        sequenceFocused(null);
        roiFocused(null);

        Icy.getMainInterface().removeFocusedSequenceListener(this);
    }

    @Override
    public void sequenceFocused(Sequence sequence)
    {
        if (focusedSequence != sequence)
        {
            if (focusedSequence != null)
                focusedSequence.removePainter(painter);

            focusedSequence = sequence;
            roiFocused(null);

            if (focusedSequence != null)
                focusedSequence.addPainter(painter);
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
    public void focusedSequenceChanged(SequenceEvent event)
    {
        // nothing to do here
    }

    @Override
    public void roiChanged(ROIEvent event)
    {
        if (event.getType() == ROIEventType.ROI_CHANGED)
            updateInfos();
    }
}
