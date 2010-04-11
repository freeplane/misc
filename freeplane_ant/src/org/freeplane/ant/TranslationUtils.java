/**
 * TranslationUtils.java
 *
 * Copyright (C) 2010,  Volker Boerchers
 *
 * Translator.java is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Translator.java is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package org.freeplane.ant;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

public class TranslationUtils {

	static class IncludeFileFilter implements FileFilter {
    	private ArrayList<Pattern> includePatterns = new ArrayList<Pattern>();
    	private ArrayList<Pattern> excludePatterns = new ArrayList<Pattern>();
    	IncludeFileFilter(ArrayList<Pattern> includePatterns, ArrayList<Pattern> excludePatterns){
    		this.includePatterns = includePatterns;
    		this.excludePatterns = excludePatterns;
    	}
    	public boolean accept(File pathname) {
    		if (pathname.isDirectory())
    			return false;
    		for (Pattern pattern : excludePatterns) {
    			if (pattern.matcher(pathname.getName()).matches())
    				return false;
    		}
    		if (includePatterns.isEmpty())
    			return true;
    		for (Pattern pattern : includePatterns) {
    			if (pattern.matcher(pathname.getName()).matches())
    				return true;
    		}
    		return false;
    	}
    }

	static void writeFile(File outputFile, String[] lines) throws IOException {
    	final String endLine = System.getProperty("line.separator");
    	BufferedWriter out = null;
    	try {
    		out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "US-ASCII"));
    		for (int i = 0; i != lines.length; i++) {
    			out.write(lines[i].replaceAll("\\\\[\n\r]+", "\\\\" + endLine));
    			// change this to write(<sep>) to enforce Unix or Dos or Mac newlines
    			out.newLine();
    		}
    	}
    	finally {
    		if (out != null) {
    			try {
    				out.close();
    			}
    			catch (IOException e) {
    				// can't help it
    			}
    		}
    	}
    }

	// adapted from http://www.rgagnon.com/javadetails/java-0515.html, Réal Gagnon
    public static String wildcardToRegex(String wildcard) {
    	StringBuilder s = new StringBuilder(wildcard.length());
    	s.append('^');
    	for (int i = 0, is = wildcard.length(); i < is; i++) {
    		char c = wildcard.charAt(i);
    		switch (c) {
    			case '*':
    				s.append(".*");
    				break;
    			case '?':
    				s.append(".");
    				break;
    			// escape special regexp-characters
    			case '(':
    			case ')':
    			case '$':
    			case '^':
    			case '.':
    			case '{':
    			case '}':
    			case '|':
    			case '\\':
    				s.append("\\");
    				s.append(c);
    				break;
    			default:
    				s.append(c);
    				break;
    		}
    	}
    	s.append('$');
    	return (s.toString());
    }

	static String readFile(final File inputFile) throws IOException {
    	InputStreamReader in = null;
    	try {
    		in = new InputStreamReader(new FileInputStream(inputFile), "US-ASCII");
    		StringBuilder builder = new StringBuilder();
    		final char[] buf = new char[1024];
    		int len;
    		while ((len = in.read(buf)) > 0) {
    			builder.append(buf, 0, len);
    		}
    		return builder.toString();
    	}
    	finally {
    		if (in != null) {
    			try {
    				in.close();
    			}
    			catch (IOException e) {
    				// can't help it
    			}
    		}
    	}
    }

	static String[] readLines(File inputFile) throws IOException {
		final String content = readFile(inputFile);
		// a trailing backslash escapes the following newline
		return content.split("(?<!\\\\)[\n\r]+");
    }
	
	static String toLine(String key, String value) {
		return key + " = " + value;
	}

	static Project createProject(final Task task) {
        final Project project = new Project();
    	final DefaultLogger logger = new DefaultLogger();
    	logger.setMessageOutputLevel(Project.MSG_INFO);
    	logger.setOutputPrintStream(System.out);
    	logger.setErrorPrintStream(System.err);
    	project.addBuildListener(logger);
    	task.setProject(project);
        return project;
    }
}
