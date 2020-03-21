// from https://www.ibm.com/developerworks/library/j-prefapi/index.html

package net.sf.jaer.util;

import java.io.*;
import java.util.prefs.*;

/** Stores objects and loads them from Java Preferences. 
 * From https://www.ibm.com/developerworks/library/j-prefapi/index.html
 * */
public class PrefObj
{
  // Max byte count is 3/4 max string length (see Preferences
  // documentation).
  static private final int pieceLength =
    ((3*Preferences.MAX_VALUE_LENGTH)/4);

  static private byte[] object2Bytes( Object o ) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream( baos );
    oos.writeObject( o );
    return baos.toByteArray();
  }

  static private byte[][] breakIntoPieces( byte raw[] ) {
    int numPieces = (raw.length + pieceLength - 1) / pieceLength;
    byte pieces[][] = new byte[numPieces][];
    for (int i=0; i<numPieces; ++i) {
      int startByte = i * pieceLength;
      int endByte = startByte + pieceLength;
      if (endByte > raw.length) endByte = raw.length;
      int length = endByte - startByte;
      pieces[i] = new byte[length];
      System.arraycopy( raw, startByte, pieces[i], 0, length );
    }
    return pieces;
  }

  static private void writePieces( Preferences prefs, String key,
      byte pieces[][] ) throws BackingStoreException {
    Preferences node = prefs.node( key );
    node.clear();
    for (int i=0; i<pieces.length; ++i) {
      node.putByteArray( ""+i, pieces[i] );
    }
  }

  static private byte[][] readPieces( Preferences prefs, String key )
      throws BackingStoreException {
    Preferences node = prefs.node( key );
    String keys[] = node.keys();
    int numPieces = keys.length;
    byte pieces[][] = new byte[numPieces][];
    for (int i=0; i<numPieces; ++i) {
      pieces[i] = node.getByteArray( ""+i, null );
    }
    return pieces;
  }

  static private byte[] combinePieces( byte pieces[][] ) {
    int length = 0;
    for (int i=0; i<pieces.length; ++i) {
      length += pieces[i].length;
    }
    byte raw[] = new byte[length];
    int cursor = 0;
    for (int i=0; i<pieces.length; ++i) {
      System.arraycopy( pieces[i], 0, raw, cursor, pieces[i].length );
      cursor += pieces[i].length;
    }
    return raw;
  }

  static private Object bytes2Object( byte raw[] )
      throws IOException, ClassNotFoundException {
    ByteArrayInputStream bais = new ByteArrayInputStream( raw );
    ObjectInputStream ois = new ObjectInputStream( bais );
    Object o = ois.readObject();
    return o;
  }

  /** Stores object to preferences, even if it is too large for usual Preferences String 
   * 
   * @param prefs the Preferences; obtain for example in an EventFilter with getChip().getPrefs()
   * @param key your key
   * @param o the object to store
   * @throws IOException
   * @throws BackingStoreException
   * @throws ClassNotFoundException 
   */
  static public void putObject( Preferences prefs, String key, Object o )
      throws IOException, BackingStoreException, ClassNotFoundException {
    byte raw[] = object2Bytes( o );
    byte pieces[][] = breakIntoPieces( raw );
    writePieces( prefs, key, pieces );
  }

  /** Loads the object, which must be cast to the correct type
   * 
   * @param prefs the preferences 
   * @param key your key
   * @return the Object
   * @throws IOException
   * @throws BackingStoreException
   * @throws ClassNotFoundException 
   */
  static public Object getObject( Preferences prefs, String key )
      throws IOException, BackingStoreException, ClassNotFoundException {
    byte pieces[][] = readPieces( prefs, key );
    byte raw[] = combinePieces( pieces );
    Object o = bytes2Object( raw );
    return o;
  }
}
