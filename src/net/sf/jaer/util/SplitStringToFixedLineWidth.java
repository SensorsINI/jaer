package net.sf.jaer.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** From http://www.davismol.net/2015/02/03/java-how-to-split-a-string-into-fixed-length-rows-without-breaking-the-words/
 * 
 * @author Dede Blog
 */
public class SplitStringToFixedLineWidth {

    public static List<String> splitString(String msg, int lineSize) {
        List<String> res = new ArrayList<>();

        Pattern p = Pattern.compile("\\b.{1," + (lineSize-1) + "}\\b\\W?");
        Matcher m = p.matcher(msg);
        
	while(m.find()) {
                System.out.println(m.group().trim());   // Debug
                res.add(m.group());
        }
        return res;
    }


    public static void main(String[] args) {

        splitString("This is a message that needs to be split over multiple lines because it is too long. The result must be a list of strings with a maximum length provided as input. Will this procedure work? I hope so!",40);
    }

}