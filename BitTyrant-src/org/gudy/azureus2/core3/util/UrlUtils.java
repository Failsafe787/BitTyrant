/*
 * Created on Mar 21, 2006 3:09:00 PM
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */
package org.gudy.azureus2.core3.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aelitis.net.magneturi.MagnetURIHandler;

/**
 * @author TuxPaper
 * @created Mar 21, 2006
 *
 */
public class UrlUtils
{
	private static final String[] prefixes = new String[] {
			"http://",
			"https://",
			"ftp://",
			"magnet:?",
			"magnet://?" };

	private static int MAGNETURL_STARTS_AT = 3;

	/**
	 * test string for possibility that it's an URL.  Considers 40 byte hex 
	 * strings as URLs
	 * 
	 * @param sURL
	 * @return
	 */
	public static boolean isURL(String sURL) {
		return parseTextForURL(sURL, true) != null;
	}

	public static String parseTextForURL(String text, boolean accept_magnets) {

		if (text == null || text.length() < 5) {
			return null;
		}

		String href = parseHTMLforURL(text);
		if (href != null) {
			return href;
		}

		try {
			text = text.trim();
			text = URLDecoder.decode(text);
		} catch (Exception e) {
			// sometimes fires a IllegalArgumentException
			// catch everything and ignore.
		}

		String textLower;
		try {
			textLower = text.toLowerCase();
		} catch (Throwable e) {
			textLower = text;
		}
		int max = accept_magnets ? prefixes.length : MAGNETURL_STARTS_AT;
		for (int i = 0; i < max; i++) {
			final int begin = textLower.indexOf(prefixes[i]);
			if (begin >= 0) {
				final int end = text.indexOf("\n", begin + prefixes[i].length());
				final String stringURL = (end >= 0) ? text.substring(begin, end - 1)
						: text.substring(begin);
				try {
					URL parsedURL = new URL(stringURL);
					return parsedURL.toExternalForm();
				} catch (MalformedURLException e1) {
					e1.printStackTrace();
					if (i >= MAGNETURL_STARTS_AT) {
						return stringURL;
					}
				}
			}
		}

		// accept raw hash of 40 hex chars
		if (accept_magnets && text.matches("^[a-fA-F0-9]{40}$")) {
			// convert from HEX to raw bytes
			byte[] infohash = ByteFormatter.decodeString(text.toUpperCase());
			// convert to BASE32
			return "magnet:?xt=urn:btih:" + Base32.encode(infohash);
		}

		return null;
	}

	public static String parseHTMLforURL(String text) {
		// examples:
		// <A HREF=http://abc.om/moo>test</a>
		// <A style=cow HREF="http://abc.om/moo">test</a>
		// <a href="http://www.gnu.org/licenses/fdl.html" target="_top">moo</a>

		Pattern pat = Pattern.compile("<.*a\\s++.*href=\"?([^\\'\"\\s>]++).*",
				Pattern.CASE_INSENSITIVE);
		Matcher m = pat.matcher(text);
		if (m.find()) {
			String sURL = m.group(1);
			try {
				sURL = URLDecoder.decode(sURL);
			} catch (Exception e) {
				// sometimes fires a IllegalArgumentException
				// catch everything and ignore.
			}
			return sURL;
		}

		return null;
	}

	public static void main(String[] args) {
		MagnetURIHandler.getSingleton();
		byte[] infohash = ByteFormatter.decodeString("1234567890123456789012345678901234567890");
		String[] test = {
				"http://moo.com",
				"http%3A%2F/moo%2Ecom",
				"magnet:?moo",
				"magnet%3A%3Fxt=urn:btih:26",
				"magnet%3A//%3Fmooo",
				"magnet:?xt=urn:btih:" + Base32.encode(infohash),
				"aaaaaaaaaabbbbbbbbbbccccccccccdddddddddd" };
		for (int i = 0; i < test.length; i++) {
			System.out.println(test[i] + " -> " + URLDecoder.decode(test[i]));
			System.out.println(test[i] + " -> " + isURL(test[i]));
			System.out.println(test[i] + " -> " + parseTextForURL(test[i], true));
		}

	}

	/**
	 * Like URLEncoder.encode, except translates spaces into %20 instead of +
	 * @param s
	 * @return
	 */
	public static String encode(String s) {
		return URLEncoder.encode(s).replaceAll("\\+", "%20");
	}
}
