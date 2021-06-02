/*
 * HexString.java
 *
 * Created on August 29, 2005, 7:46 AM
 */

package net.sf.jaer.util;

import java.text.ParseException;

/**
 * Provides static methods for converting from integer types to Hex string representations.
 * @author tobi
 */
public class HexString {
    
    /** Creates a new instance of HexString */
    private HexString() {
        
    }
    
    /** @return e.g. "0xf3" */
    public static String toString(byte n){
        return "0x"+byteToHexString(n);
    }
    
    /** @return e.g. "0xfe0033f3" */
    public static String toString(int n){
        return "0x"+intToHexString(n);
    }
    
    /** @return e.g. "0xfe0033f3" */
    public static String toString(long n){
        return "0x"+longToHexString(n);
    }
    
    /** @return e.g. "0x86f3" */
    public static String toString(short n){
        return "0x"+shortToHexString(n);
    }
    
    /** parses a string as a short in hex format. It can have leading "0x"
     *@param s string to parse
     *@return short value
     */
    public static short parseShort(String s) throws ParseException {
        if(s.startsWith("0x")){
            s=s.substring(2);
        }
        s=s.toLowerCase();
        char[] chars=s.toCharArray();
        int v=0;
        int k=0;
        for(char c:chars){
            v*=16;
            if(c>='0' && c<='9'){
                v+= c-'0';
            }else if(c>='a' && c<='f'){
                v+= (c-'a'+10);
            }else{
                throw new ParseException("can't parse "+s+" as hex string, error at position "+k, k);
            }
            k++;
        }
        return (short)v;
        
    }
    
    // helper functions
    
    private static final char[] HEX_DIGITS = {
        '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'
    };
    
    private static String byteToHexString(byte n) {
        char[] buf = new char[2];
        for(int i = 1; i >= 0; i--) {
            buf[i] = HEX_DIGITS[n & 0x0F];
            n >>>= 4;
        }
        return new String(buf);
    }
    
    private static String intToHexString(int n) {
        char[] buf = new char[8];
        for(int i = 7; i >= 0; i--) {
            buf[i] = HEX_DIGITS[n & 0x0F];
            n >>>= 4;
        }
        return new String(buf);
    }
    
    private static String longToHexString(long n) {
        char[] buf = new char[16];
        for(int i = 15; i >= 0; i--) {
            buf[i] = HEX_DIGITS[(int)n & 0x0F];
            n >>>= 4;
        }
        return new String(buf);
    }
    
    private static String shortToHexString(short n) {
        char[] buf = new char[4];
        for(int i = 3; i >= 0; i--) {
            buf[i] = HEX_DIGITS[n & 0x0F];
            n >>>= 4;
        }
        return new String(buf);
    }
}
