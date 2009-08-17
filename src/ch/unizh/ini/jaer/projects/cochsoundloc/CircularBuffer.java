/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

/**
 *
 * @author Holger
 */
public class CircularBuffer<E> {

    private double[] queue;
    private int head;
    private int tail;

    public CircularBuffer() {
        queue = new double[100];
        clear();
    }

    public CircularBuffer(int n) {
        queue = new double[n];
        clear();
    }

    public void clear() {
        head = 0;
        tail = 0;
        return;
    }

    public double dequeue() {
        if ((tail == queue.length - 1) && (head != 0)) {
            tail = 0;
        } else {
            tail++;
        }
        return (queue[tail]);
    }

    public void enqueue(double x) {

        if ((head == queue.length - 1) && (tail != 0)) {
            head = 0;
        } else {
            head++;
        }

        queue[head] = x;
        return;

    }
}
