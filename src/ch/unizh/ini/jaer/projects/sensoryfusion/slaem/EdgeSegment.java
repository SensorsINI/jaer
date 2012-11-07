/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import ch.unizh.ini.jaer.projects.sensoryfusion.slaem.EdgeFragments.Snakelet;
import java.awt.geom.Line2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

/**
 *
 * @author Christian
 */
public class EdgeSegment extends Line2D.Float implements UpdatedStackElement{
    public float phi;
    public Edge edge;
    public int evidence;
    public boolean mature;

    public EdgeSegment(EdgeFragments.Snakelet snakelet, Edge edge){
        this.phi = snakelet.phi;
        this.setLine(snakelet.line.getP1(), snakelet.line.getP2());
        this.edge = edge;
        edge.timestamp=snakelet.timestamp;
        evidence = 1;
        this.mature = false;
    } 

    @Override
    public boolean contains(Object snk, float oriTolerance, float distTolerance){
        Snakelet snakelet = null;
        if(snk.getClass()==Snakelet.class){
            snakelet = (Snakelet) snk;
        }else{
            return false;
        }
        float dPhi, dS1, dS2;
        boolean close1 = false;
        dPhi = getAngleDiff(phi, snakelet.phi);
        dS1 = (float)ptSegDist(snakelet.line.getP1());
        dS2 = (float)ptSegDist(snakelet.line.getP2());
        if(dS1 < dS2){
            close1 = true;
        }
        if(dPhi<oriTolerance && (dS1<distTolerance || dS2<distTolerance)){
            if(close1){
                float dL = (float)ptLineDist(snakelet.line.getP1());
                int dir = relativeCCW(snakelet.line.getP1());
                translateSegment(dL, dir);
            }else{
                float dL = (float)ptLineDist(snakelet.line.getP2());
                int dir = relativeCCW(snakelet.line.getP2());
                translateSegment(dL, dir);
            }
            stretchSegment(snakelet.line, close1);
            phi = calculatePhi();
            udateMaturiy();
            edge.timestamp = snakelet.timestamp;
            return true;
        }else{
            return false;
        }
    }

    public boolean crosses(EdgeSegment segment, float oriTolerance, float distTolerance){
        float dPhi, dS1, dS2;
        boolean close1 = false;
        dPhi = getAngleDiff(phi, segment.phi);
        dS1 = (float)ptSegDist(segment.getP1());
        dS2 = (float)ptSegDist(segment.getP2());
        if(dS1 < dS2){
            close1 = true;
        }
        if(dPhi<oriTolerance && (dS1<distTolerance || dS2<distTolerance)){
            if(close1){
                float theta = (float)(phi-relativeCCW(segment.getP1())*Math.PI/2.0);
                translateSegment((float)(Math.sin(theta)*ptLineDist(segment.getP1())), (float)(Math.cos(theta)*ptLineDist(segment.getP1())));
            }else{
                float theta = (float)(phi-relativeCCW(segment.getP2())*Math.PI/2.0);
                translateSegment((float)(Math.sin(theta)*ptLineDist(segment.getP2())), (float)(Math.cos(theta)*ptLineDist(segment.getP2())));
            }
            stretchSegment(segment, close1);
            phi = calculatePhi();
            udateMaturiy();
            if(edge.timestamp < segment.edge.timestamp)edge.timestamp = segment.edge.timestamp;
            return true;
        }else{
            return false;
        }
    }

    void translateSegment(float dX, float dY){
        x1 = x1+dX;
        y1 = y1+dY;
        x2 = x2+dX;
        y2 = y2+dY;
    }

    void stretchSegment(Line2D.Float otherLine, boolean close1){
        if(close1){
//                    line.x2=(line.x2+snakelet.line.x2)/2.0f;
//                    line.y2=(line.y2+snakelet.line.y2)/2.0f;
            x2=otherLine.x2;
            y2=otherLine.y2;
        } else {
//                    line.x1=(line.x1+snakelet.line.x1)/2.0f;
//                    line.y1=(line.y1+snakelet.line.y1)/2.0f;
            x1=otherLine.x1;
            y1=otherLine.y1;
        }
    }

    void udateMaturiy(){
        evidence++;
        if(evidence>4){
            this.mature = true;
            this.edge.matureSegment = true;
            //this.edge.setMature(true);
        }
    }

    float calculatePhi(){
        return (float)Math.atan2((x1-x2),(y1-y2));
    }

    float getAngleDiff(float angle1, float angle2){
        float diff = (float)Math.abs(angle1-angle2);
        if(diff > Math.PI){
            diff = (float)(2*Math.PI)-diff;
        }
        return diff;
    }

    @Override
    public void draw(GLAutoDrawable drawable){
        GL gl=drawable.getGL();
        if(mature && edge.mature){
            gl.glLineWidth(4.0f);
            gl.glColor3f(edge.color[0],edge.color[1],edge.color[2]); 
        }else if(mature){
            gl.glLineWidth(3.0f);
            gl.glColor3f(0.5f+0.5f*edge.color[0],0.5f+0.5f*edge.color[1],0.5f+0.5f*edge.color[2]);
        }else{
            gl.glLineWidth(2.0f);
            gl.glColor3f(1.0f,0.5f,0.5f); 
        } 
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2d(x1,y1);
        gl.glVertex2d(x2,y2);
        gl.glEnd();
    }
    
}
