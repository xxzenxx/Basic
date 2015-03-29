/****************************************************************************************************

 BASIC! is an implementation of the Basic programming language for
 Android devices.

 Copyright (C) 2010 - 2015 Paul Laughton

 This file is part of BASIC! for Android

 BASIC! is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 BASIC! is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with BASIC!.  If not, see <http://www.gnu.org/licenses/>.

 You may contact the author or current maintainers at http://rfobasic.freeforums.org
 
 Contains contributions from Michael Camacho. 2012

*************************************************************************************************/

package com.rfo.basic;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

import android.util.Log;


public class AddProgramLine {
	private static final String LOGTAG = "AddProgramLine";
	private static String kw_include = "include";

	Boolean BlockFlag = false;
	String stemp = "";
	public static ArrayList<Integer> lineCharCounts;
	public static int charCount = 0;

	public AddProgramLine() {
		charCount = 0;									// Character count = 0
		lineCharCounts = new ArrayList<Integer>();		// create a new list of line char counts
		lineCharCounts.add(0);							// First line starts with a zero char count
		Basic.lines = new ArrayList<Run.ProgramLine>();
	}

	public void AddLine(String line) {
		/* Adds one line to Basic.lines
		 * Each line will have all white space characters removed and all characters
		 * converted to lower case (unless they are within quotes).
		 */
		if (line == null) { return; }

		// Regular expressions
		String ws					= "[" + Basic.whitespace + "]*";
		String ignored_lead_regex	= "[" + Basic.whitespace + ":]*";		// ignore leading characters whitespaces & colon
		String label_regex			= ":" + ws;								// a label followed by 0 or more whitespaces
		String endif_regex			= ".*" + ws + "end" + ws + "if" + ws; 	// ENDIF followed by 0 or more whitespace

		line = line.replaceFirst(ignored_lead_regex, "");			// skip leading whitespaces and colons
		int k = Format.FindKeyWord("%", line, 0);					// find a possible end-of-line comment,
		if (k >= 0) { line = line.substring(0, k); }				// and strip it from the line
		int linelen = line.length();
		int i = 0;

		// Look for block comments. All lines between block comments
		// are tossed out

		if (BlockFlag) {
			if (line.startsWith("!!", i)) {
				BlockFlag = false;
			}
			return;
		}
		if (line.startsWith("!!", i)) {
			BlockFlag = true;
			return;
		}

		// Detect (and transform internally!) multi-commands in single-line IF/THEN/ELSE

		String lcLine = line.toLowerCase();
		k = Format.FindKeyWord(":", lcLine, 0);
		if (lcLine.startsWith("if") && k > 0) {					// line starts with IF and contains a ':' (not between quotes)
			if (!lcLine.matches(endif_regex)) {					// no ENDIF at the end of the global line
				//Log.v(LOGTAG, "AddLine(before): " + line);
				k = Format.FindKeyWord("then", lcLine, 0);
				if (k > 0) { line = Insert(line, ":", k+4); } 	// transform THEN into THEN:
				lcLine = line.toLowerCase();
				k = Format.FindKeyWord("else", lcLine, k+4);
				if (k > 0) {									// transform ELSE into :ELSE:
					line = Insert(line, ":", k+4);
					line = Insert(line, ":", k);
				}
				line += ":endif";								// finally, add an ending :ENDIF
				//Log.v(LOGTAG, "AddLine(after): " + line);
			}
		}

		StringBuilder sb = new StringBuilder();
		String afterColon = "";

		for (; i < linelen; ++i) {					// do not mess with characters
			char c = line.charAt(i);				// between quote marks
			if (c == '"' || c == '\u201c') {		// Change funny quote to real quote
				i = doQuotedString(line, i, linelen, sb);
			} else if (c == ':') {					// if the : character appears,
				if ( sb.indexOf("sql.update") == 0	// keep it if it's part of a SQL.UPDATE command
				  || sb.indexOf("~") != -1 ) {		// or if the line contains a '~' somewhere
					sb.append(c);
				} else {
					if (line.substring(i).matches(label_regex)) {
						sb.append(c);				// followed by nothing, whitespaces, or a comment: it's a label
					} else {						// else it's a delimiter for multiple commands per line
						afterColon = line.substring(i+1);	// split the rest of the line for later use
					}
					break;							// in both cases (label|multi-command), treat the line up to ':'
				}
			} else if (Basic.whitespace.indexOf(c) == -1) { // toss out spaces, tabs, and &nbsp
				c = Character.toLowerCase(c);		// normal character: convert to lower case
				sb.append(c);						// and add it to the line
			}
		}
		String Temp = sb.toString();

		if (Temp.startsWith(kw_include)) {			// If include,
			doInclude(Temp);						// Do the include
			return;
		}

		if (Temp.equals(""))        { return; }		// toss out empty lines
		if (Temp.startsWith("!"))   { return; }		// and whole-line comments
		if (Temp.startsWith("%"))   { return; }		// and end-of-line comments
		if (Temp.startsWith("rem")) { return; }		// and REM lines

		if (stemp.length() == 0) {					// whole line, or first line of a collection
													// connected with continuation markers
			lineCharCounts.add(charCount);			// add char count to array of char counts
		}
		if (Temp.endsWith("~")) {					// Pre-processor: test for line continuation marker
			Temp = Temp.substring(0, Temp.length() - 1);	// remove the marker
			stemp = (stemp.length() == 0) ? Temp : mergeLines(stemp, Temp);	// and collect the line
			return;
		}
		if (stemp.length() > 0) {
			Temp = mergeLines(stemp, Temp);			// add stemp collection to line
			stemp = "";								// clear the collection
		}
		Temp += "\n";								// end the line with New Line
		Basic.lines.add(new Run.ProgramLine(Temp));	// add to Basic.lines

		if (afterColon.length() > 0) {				// if the input line contained a colon (not at the end)
			AddLine(afterColon);					// recursively treat the part after the colon
		}
	}

	private static String Insert(String main, String ins, int pos) {	// Insert string 'ins' into 'main' at position 'pos'
		return main.substring(0, pos) + ins + main.substring(pos, main.length());
	}

	private int doQuotedString(String line, int index, int linelen, StringBuilder s) {
		char c, c2;
		s.append('"');						// Incoming index points at a quote
		while (true) {						// Loop until quote or no more characters
			++index;
			if (index >= linelen) { break; }	// No more characters, done
			else { c = line.charAt(index); }	// next character
			if (c == '"' || c == '\u201c') {	// Found quote, done
				break;
			}

			c2 = ((index + 1) < linelen) ? line.charAt(index + 1) : '\0';
			if (c == '\\') {
				if (c2 == '"' || c2 == '\\') {	// look for \" or \\ and retain it 
					s.append('\\').append(c2);	// so that user can have quotes and backslashes in strings
					++index;
				} else if (c2 == 'n') {			// change backslash-n to carriage return
					s.append('\r');
					++index;
				} else if (c2 == 't') {			// change backslash-t to tab
					s.append('\t');
					++index;
				}								// else remove the backslash
			} else { s.append(c); }				// not quote or backslash
		}
		s.append('"');							// Close string. If no closing quote in user string, add one.
												// If funny quote, convert it to ASCII quote.
		return index;							// leave index pointing at quote or EOL
	}

	private String mergeLines(String base, String addition) {
		if (base.length() == 0) { return addition; }
		if (addition.length() == 0) { return base; }

		String specialCommands[] = { "array.load", "list.add" };
		for (int i = 0; i < specialCommands.length; ++i) {
			if ( base.startsWith(specialCommands[i]) &&					// command allows continuable data
				 (base.length() > specialCommands[i].length()) ) {		// the command is not alone on the line
				char lastChar = base.charAt(base.length() - 1);
				char nextChar = addition.charAt(0);
				if (lastChar != ',' && nextChar != ',') {				// no comma between adjacent parameters
					return base + ',' + addition;						// insert comma between parameters
				}
			}
		}
		return base + addition;
	}

	private void doInclude(String fileName) {
		// If fileName is enclosed in quotes, the quotes preserved its case in AddLine().
		// Error messages go back through AddLine() again, so keep the quotes.
		String originalFileName = fileName.substring(kw_include.length()).trim();	// use this for error message
		fileName = originalFileName.replace("\"",  "");								// use this for file operations
		BufferedReader buf = null;
		try { buf = Basic.getBufferedReader(Basic.SOURCE_DIR, fileName, Basic.Encryption.ENABLE_DECRYPTION); }
		// If getBufferedReader() returned null, it could not open the file or asset,
		// or it could not decrypt an encrypted asset.
		// It may or may not throw an exception.
		// TODO: "not_found" may not be a good error message. Can we change it?
		catch (Exception e) { }
		if (buf == null) {
			String t = "Error_Include_file (" + originalFileName + ") not_found";
			AddLine(t);
			return;
		}

		String data = null;
		do {
			try { data = buf.readLine(); }
			catch (IOException e) { data = "Error reading Include file " + originalFileName; return; }
			finally { AddLine(data); }							// add the line
		} while (data != null);									// while not EOF and no error
	}

}
