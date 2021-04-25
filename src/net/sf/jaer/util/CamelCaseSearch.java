/*
 * Copyright (C) 2020 tobid.
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
package net.sf.jaer.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

class CamelCaseSearch {

    /**
     * Static method to search by CamelCase for a string in list of strings.
     *
     * @param str word to match
     * @param query some pattern in CamCase
     *
     * @return matched word or null if no match
     *
     * @author
     * https://stackoverflow.com/questions/745415/regex-to-match-from-partial-or-camel-case-string
     */
    public static String matchCamelCase(String query, String str) {
        query = query.replaceAll("\\*", ".*?");
        String re = "\\b(" + query.replaceAll("([A-Z][^A-Z]*)", "$1[^A-Z]*") + ".*?)\\b";

//        System.out.println(re);
        try {
            Pattern regex = Pattern.compile(re);
            Matcher m = regex.matcher(str);

            if (m.find()) {
//            System.out.println("found "+m.group());
                return m.group();
            } else {
                return null;
            }
        } catch (PatternSyntaxException e) {
            return null;
        }

    }

    /**
     * Returns list of Strings in dict that match pattern that is given in CCase
     *
     * @param dict
     * @param pattern
     * @return the list of matching words
     */
    public static ArrayList<String> findAllWords(List<String> dict, String pattern) {

        ArrayList<String> r = new ArrayList();
        for (String word : dict) {
            String w = matchCamelCase(pattern, word);
            if (w != null) {
                r.add(w);
            }
        }
        return r;

    }

    // Driver function 
    public static void main(String args[]) {

        // dictionary of words where each word follows 
        // CamelCase notation 
        List<String> dict = Arrays.asList("Hi", "Hello",
                "HelloWorld", "HiTech", "HiGeek",
                "HiTechWorld", "HiTechCity",
                "HiTechLabs", "HiTechLabs1", "HiTechLabs2", "HiTechLabs");

        // pattern consisting of uppercase characters only 
        String pattern = "HTeLa";

        ArrayList<String> matches = findAllWords(dict, pattern);
        System.out.println(String.format("from %s pattern found ", pattern));
        for (String s : matches) {
            System.out.println(s);
        }
    }

}
