/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.util;

import java.util.logging.ConsoleHandler;

/**
 * Redirects logging to System.out
 * @author tobi
 */
public class JAERConsoleHandler extends ConsoleHandler{

    public JAERConsoleHandler(){
        setOutputStream(System.out);
    }

}
