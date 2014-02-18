/////////////////////////////////////////////////////////////////////////////
// This file is part of the "Java-DAP" project, a Java implementation
// of the OPeNDAP Data Access Protocol.
//
// Copyright (c) 2010, OPeNDAP, Inc.
// Copyright (c) 2002,2003 OPeNDAP, Inc.
// 
// Author: James Gallagher <jgallagher@opendap.org>
// 
// All rights reserved.
// 
// Redistribution and use in source and binary forms,
// with or without modification, are permitted provided
// that the following conditions are met:
// 
// - Redistributions of source code must retain the above copyright
//   notice, this list of conditions and the following disclaimer.
// 
// - Redistributions in binary form must reproduce the above copyright
//   notice, this list of conditions and the following disclaimer in the
//   documentation and/or other materials provided with the distribution.
// 
// - Neither the name of the OPeNDAP nor the names of its contributors may
//   be used to endorse or promote products derived from this software
//   without specific prior written permission.
// 
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
// IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
// TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, O
// PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
// NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
/////////////////////////////////////////////////////////////////////////////

package ucar.httpclient;

import java.net.*;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Escape
{
    public static final Charset utf8Charset = Charset.forName("UTF-8");

    // Sets of ascii characters
    protected static final String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    protected static final String numeric = "0123456789";
    protected static final String alphaNumeric = alpha + numeric;

    // Experimentally determined url and query
    // legal and illegal chars as defined by apache httpclient3
    // Sets are larger than strictly necessary
    protected static final String urllegal     = "!#$&'()*+,-./:;=?@_~";
    protected static final String querylegal   = urllegal + "%";
    protected static final String queryillegal = " \"<>[\\]^`{|}";
    protected static final String urlillegal   = queryillegal + "%";

    // Set of all ascii printable non-alphanumeric (aka nan) characters
    protected static final String nonAlphaNumeric = " !\"#$%&'()*+,-./:;<=>?@[]\\^_`|{}~" ;

    protected static final String queryReserved
        = queryillegal; // special parsing meaning in queries
    static protected  final String urlReserved
        = urlillegal; // special parsing meaning in url

    // We assume that whoever constructs a url (minus the query)
    // has properly percent encoded whatever characters need to be encoded.
    // Sets of characters absolutely DIS-allowed in url.
    static private final String urlDisallowed  = urlReserved;

    // Complement of urlDisallowed
    static private final String urlAllowed = "\"%[\\]^`{|}<>";

    // This is set of legal characters that can appear unescaped in a url
    static private final String _allowableInUrl= alphaNumeric + urlAllowed;

    // Sets of characters absolutely DIS-allowed in query string identifiers.
    // Basically, this set is determined by what kind of query parsing needs to occur.
    static private final String queryIdentDisallowed
                                = queryReserved+"\"\\^`|<>[]{}";

    // Complement of queryIdentDisallowed
    static private final String queryIdentAllowed = nonAlphaNumeric+queryIdentDisallowed;

    // This is set of legal characters that can appear unescaped in a url query.
    static private final String _allowableInUrlQuery = alphaNumeric + queryIdentAllowed;

    // Define the set of characters allowable in DAP Identifiers
    static private final String dapSpecAllowed = "_!~*'-\"" ; //as specified in dap2 spec
    static private final String _namAllowedInDAP = dapSpecAllowed
                                                   + "./"; // for groups and structure names
    static private final String _allowableInDAP = alphaNumeric + _namAllowedInDAP;

    static private final char _URIEscape = '%';


    static {
	System.err.println("alphaNumeric = "+alphaNumeric);
	System.err.println("urllegal     = "+urllegal);
	System.err.println("querylegal "+ =querylegal);
	System.err.println("queryillegal = "+queryillegal);
	System.err.println("urlillegal "+ =urlillegal);
	System.err.println("nonAlphaNumeric = "+nonAlphaNumeric);
	System.err.println("queryReserved = "+queryReserved);
	System.err.println("urlReserved = "+urlReserved);
	System.err.println("urlDisallowed  = "+urlDisallowed);
	System.err.println("urlAllowed = "+urlAllowed);
	System.err.println("_allowableInUrl= "+allowableInUrl);
	System.err.println("queryIdentDisallowed = "+queryIdentDisallowed);
	System.err.println("queryIdentAllowed = "+queryIdentAllowed);
	System.err.println("_allowableInUrlQuery = "+_allowableInUrlQuery);
	System.err.println("dapSpecAllowed = "+dapSpecAllowed);
	System.err.println("_namAllowedInDAP = "+_namAllowedInDAP);
	System.err.println("_allowableInDAP = "+_allowableInDAP);
    }


    /**
     * Replace all characters in the String <code>in</code> not present in the String <code>allowable</code> with
     * their hexidecimal values (encoded as UTF8) and preceeded by the String <code>esc</code>
     * <p/>
     * The <cods>esc</code> character may not appear on the allowable list, as if it did it would break the 1:1
     * and onto mapping between the unescaped character space and the escaped characater space.
     *
     * @param in        The string in which to replace characters.
     * @param allowable The set of allowable characters.
     * @param esc       The escape String (typically "%" for a URI or "\" for a regular expression).
     * @param spaceplus True if spaces should be replaced by '+'.
     * @return The modified identifier.
     */

    // Useful constants
    static final byte blank = ((byte)' ');
    static final byte plus = ((byte)'+');

    private static String xescapeString(String in, String allowable, char esc, boolean spaceplus)
    {
        try {
            StringBuffer out = new StringBuffer();
            int i;
            if (in == null) return null;
            byte[] utf8 = in.getBytes(utf8Charset);
            byte[] allow8 = allowable.getBytes(utf8Charset);
            for(byte b: utf8) {
                if(b == blank && spaceplus) {
                   out.append('+');
                } else {
                    // search allow8
                    boolean found = false;
                    for(byte a: allow8) {
                        if(a == b) {found = true; break;}
                    }
                    if(found) {out.append((char)b);}
                    else {
                        String c = Integer.toHexString(b);
                        out.append(esc);
                        if (c.length() < 2) out.append('0');
                        out.append(c);
                    }
                }
            }

            return out.toString();

        } catch (Exception e) {
            return in;
        }
    }

   private static String escapeString(String in, String allowable)
   {return xescapeString(in,allowable,_URIEscape,false);}

   private static String escapeString(String in, String allowable, char esc)
   {return xescapeString(in,allowable,esc,false);}

    /**
     * Given a string that contains WWW escape sequences, translate those escape
     * sequences back into ASCII characters. Return the modified string.
     *
     * @param in     The string to modify.
     * @param escape The character used to signal the begining of an escape sequence.
     * param except If there is some escape code that should not be removed by
     *               this call (e.g., you might not want to remove spaces, %20) use this
     *               parameter to specify that code. The function will then transform all
     *               escapes except that one.
     * @param spaceplus True if spaces should be replaced by '+'.
     * @return The modified string.
     */

    private static String xunescapeString(String in, char escape, boolean spaceplus)
    {
        try {
            if (in == null) return null;

            byte[] utf8 = in.getBytes(utf8Charset);
            byte escape8 = (byte)escape;
            byte[] out = new byte[utf8.length]; // Should be max we need

            int index8 = 0;
            for(int i=0;i<utf8.length;) {
                byte b = utf8[i++];
                if(b == plus && spaceplus) {
                   out[index8++] = blank;
                } else if(b == escape8) {
                    // check to see if there are enough characters left
                    if(i+2 <= utf8.length) {
                        b = (byte)(fromHex(utf8[i])<<4 | fromHex(utf8[i + 1]));
                        i += 2;
                    }
                }
                out[index8++] = b;
            }
            return new String(out,0,index8, utf8Charset);
        } catch(Exception e) {
            return in;
        }

    }

    private static String unescapeString(String in)
    {return xunescapeString(in, _URIEscape,false);}

    private static String unescapeString(String in, char escape)
    {return xunescapeString(in, escape,false);}

    static final byte hexa = (byte)'a';
    static final byte hexf = (byte)'f';
    static final byte hexA = (byte)'A';
    static final byte hexF = (byte)'F';
    static final byte hex0 = (byte)'0';
    static final byte hex9 = (byte)'9';
    static final byte ten = (byte)10;

    private static byte fromHex(byte b) throws NumberFormatException
    {
        if(b >= hex0 && b <= hex9) return (byte)(b - hex0);
        if(b >= hexa && b <= hexf) return (byte)(ten + (b - hexa));
        if(b >= hexA && b <= hexF) return (byte)(ten + (b - hexA));
        throw new NumberFormatException("Illegal hex character: "+b);
    }

    /**
     * Define the DEFINITIVE URL escape function.
     * Beware, this is a rather complex operation
     *
     * @param url The url string
     * @return The escaped expression.
     */
     static private final Pattern p
            = Pattern.compile("([\\w]+)://([.\\w]+(:[\\d]+)?)([/][^?#])?([?][^#]*)?([#].*)?");

     public static String escapeURL(String url)
     {
        String protocol = null;
        String authority = null;
        String path = null;
        String query = null;
        String fragment = null;
        if(false) {
            // We split the url ourselves to minimize character dependencies
            Matcher m = p.matcher(url);
            boolean match = m.matches();
            if(!match) return null;
            protocol = m.group(1);
            authority = m.group(2);
            path = m.group(3);
            query = m.group(4);
            fragment = m.group(5);
        } else {// faster, but may not work quite right
            URL u = null;
            try {u = new URL(url);} catch (MalformedURLException e) {
                return null;
            }
            protocol = u.getProtocol();
            authority = u.getAuthority();
            path = u.getPath();
            query = u.getQuery();
            fragment = u.getRef();
        }
        // Reassemble
        url = protocol + "://" + authority;
        if(path != null && path.length() > 0) {
            // Encode pieces between '/'
            String pieces[] = path.split("[/]",-1);
            for(int i=0;i<pieces.length;i++)  {
                String p = pieces[i];
                if(p == null) p = "";
                if(i > 0) url += "/";
                url += urlEncode(p);
            }
        }
        if(query != null && query.length() > 0)
            url += ("?"+escapeURLQuery(query));
        if(fragment != null && fragment.length() > 0)
            url += ("#"+urlEncode(fragment));
        return url;
     }

     static int nextpiece(String s, int index, String sep)
     {
         index = s.indexOf(sep,index);
         if(index < 0)
             index = s.length();
         return index;
     }

    /**
     * Define the DEFINITIVE URL constraint expression escape function.
     *
     * @param ce The expression to modify.
     * @return The escaped expression.
     */
     public static String escapeURLQuery(String ce)
     {
        try {
            ce = escapeString(ce, _allowableInUrlQuery);
        } catch(Exception e) {ce = null;}
        return ce;
     }

    /**
     * Define the DEFINITIVE URL constraint expression unescape function.
     *
     * @param ce The expression to unescape.
     * @return The unescaped expression.
     */
     public static String unescapeURLQuery(String ce)
     {
        try {
            ce = unescapeString(ce);
        } catch(Exception e) {ce = null;}
        return ce;
     }

    /**
     * Define the DEFINITIVE URL escape function.
     * Note that the whole string is escaped, so
     * be careful what you pass into this procedure.
     *
     * @param s The string to modify.
     * @return The escaped expression.
     */
     public static String urlEncode(String s)
     {
        //try {s = URLEncoder.encode(s,"UTF-8");} catch(Exception e) {s = null;}
        s = escapeString(s, _allowableInUrl);
        return s;
     }

    /**
     * Define the DEFINITIVE URL unescape function.
     *
     * @param s The string to unescape.
     * @return The unescaped expression.
     */
     public static String urlDecode(String s)
     {
        try {
            s = URLDecoder.decode(s,"UTF-8");
        } catch(Exception e) {s = null;}
        return s;
     }

}
