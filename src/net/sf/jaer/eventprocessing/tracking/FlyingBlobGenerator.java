/*
 * Copyright (C) 2025 tobid.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.sf.jaer.eventprocessing.tracking;

import java.awt.geom.Point2D;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.util.EngineeringFormat;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

/**
 * Generates and injects synthetic blobs of events from model of flying object
 * into the event stream.
 *
 * @author tobid
 */
@Description("Generates and injects synthetic blobs of events from model of flying object into the event stream")
@net.sf.jaer.DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class FlyingBlobGenerator extends EventFilter2DMouseAdaptor {

    private EngineeringFormat eng=new EngineeringFormat();
    @Preferred
    @Description("Mean velocity for flying blobs")
    private float velocityMps = getFloat("velocityMps", 5);

    @Description("Lens focal length (default 3.7mm for Kowa 3.5mm)")
    private float lensFocalLengthMm = getFloat("lensFocalLengthMm", 3.7f);

    @Description("Blob size in meters")
    private float blobSizeM = getFloat("blobSizeM", .25f);

    @Preferred
    @Description("CoV of speeds of flying blobs")
    private float covSpeed = getFloat("covSpeed", 1);

    private Vector3D blobPosition = new Vector3D(0, 0, 0), blobVelocity = new Vector3D(0, 0, 0);
    private Vector2D blob2dPosition = new Vector2D(0, 0), blob2dVelocity = new Vector2D(0, 0);

    private float startingDistanceM = Float.NaN; // computed in initFilter
    private EventPacket outPacket=null;

    public FlyingBlobGenerator(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        // iterate over input events, injecting blob events into the stream
        if(outPacket==null){
            outPacket=new EventPacket(in.getEventClass());
        }
        OutputEventIterator outItr = outPacket.outputIterator();
        for (BasicEvent ie : in) {
            BasicEvent oe=outItr.nextOutput();
            oe.copyFrom(ie);
        }
        return outPacket;
    }

    private void computeBlobProjection() {
        // computes projection from 3d blob to 2d image
    }

    /**
     * Called in rewind or when user wants to reset filter state.
     *
     */
    @Override
    public void resetFilter() {

    }

    /**
     * Called after AEChip and this are fully constructed.
     *
     */
    @Override
    public void initFilter() {
        computeStartingDistance();
        computeFoVDeg();
    }

    private float radToDeg(float rad) {
        return 180f * rad;
    }

    private Point2D.Float computeFoVDeg() {
        // computes the horizontal and vertical field of view in degrees
        int nx = chip.getSizeX(), ny = chip.getSizeY();
        float pxSizeM = chip.getPixelWidthUm() * 1e-6f;
        float pxAngRad = pxSizeM / (getLensFocalLengthMm() * 1e-3f); // approx tan
        float fovX = radToDeg(2 * (float) Math.atan(nx * pxAngRad / 2));
        float fovY = radToDeg(2 * (float) Math.atan(ny * pxAngRad / 2));
        return new Point2D.Float(fovX, fovY);
    }

    private void computeStartingDistance() {
        // compute starting distance such that blobs are 1 pixel in size
        float pxSizeM = chip.getPixelWidthUm() * 1e-6f;
        float pxAngRad = pxSizeM / (getLensFocalLengthMm() * 1e-3f); // approx tan
        // when blob size/distance =pxAngRad the blob will be one pizel.
        // therefore distance=blob size/pxAngRad
        startingDistanceM = getBlobSizeM() / pxAngRad;
        log.info(String.format("Pixels subtend %s deg and blob starting distance is %sm",
                eng.format(radToDeg(pxAngRad)),
                eng.format(startingDistanceM))
        );
    }

    /**
     * @return the velocityMps
     */
    public float getVelocityMps() {
        return velocityMps;
    }

    /**
     * @param velocityMps the velocityMps to set
     */
    public void setVelocityMps(float velocityMps) {
        this.velocityMps = velocityMps;
        putFloat("velocityMps", velocityMps);
    }

    /**
     * @return the lensFocalLengthMm
     */
    public float getLensFocalLengthMm() {
        return lensFocalLengthMm;
    }

    /**
     * @param lensFocalLengthMm the lensFocalLengthMm to set
     */
    public void setLensFocalLengthMm(float lensFocalLengthMm) {
        this.lensFocalLengthMm = lensFocalLengthMm;
        putFloat("lensFocalLengthMm",lensFocalLengthMm);
        computeStartingDistance();
        computeFoVDeg();
    }

    /**
     * @return the blobSizeM
     */
    public float getBlobSizeM() {
        return blobSizeM;
    }

    /**
     * @param blobSizeM the blobSizeM to set
     */
    public void setBlobSizeM(float blobSizeM) {
        this.blobSizeM = blobSizeM;
        putFloat("blobSizeM",blobSizeM);
        computeStartingDistance();
    }

    /**
     * @return the covSpeed
     */
    public float getCovSpeed() {
        return covSpeed;
    }

    /**
     * @param covSpeed the covSpeed to set
     */
    public void setCovSpeed(float covSpeed) {
        this.covSpeed = covSpeed;
        putFloat("covSpeed",covSpeed);
    }

}
