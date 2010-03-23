/**
 * FormatTranslation.java
 *
 * Copyright (C) 2010,  Volker Boerchers
 *
 * FormatTranslation.java is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * FormatTranslation.java is distributed in the hope that it will be useful,
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
import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/** formats a translation file and writes the result to another file.
 * The following transformations are made:
 * <ol>
 * <li> sort lines (case insensitive)
 * <li> remove duplicates
 * <li> if a key is present multiple times entries marked as [translate me]
 *      and [auto] are removed in favor of normal entries.
 * <li> newline style is changed to the platform default.
 * </ol>
 * 
 * Attributes:
 * <ul>
 * <li> dir: the input directory (default: ".")
 * <li> outputDir: the output directory. Overwrites existing files if outputDir
 *      equals the input directory (default: the input directory)
 * <li> includes: wildcard pattern (default: all regular files).
 * <li> excludes: wildcard pattern, overrules includes (default: no excludes).
 * </ul>
 */
public class FormatTranslation extends Task {
	private final static int QUALITY_NULL = 0; // for empty values
	private final static int QUALITY_TRANSLATE_ME = 1;
	private final static int QUALITY_AUTO_TRANSLATED = 2;
	private final static int QUALITY_MANUALLY_TRANSLATED = 3;

	private class IncludeFileFilter implements FileFilter {
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
	};

	private File outputDir;
	private boolean writeIfUnchanged = false;
	private File inputDir = new File(".");
	private ArrayList<Pattern> includePatterns = new ArrayList<Pattern>();
	private ArrayList<Pattern> excludePatterns = new ArrayList<Pattern>();

	public void execute() {
		executeImpl(false);
	}

	public int checkOnly() {
		return executeImpl(true);
	}

	/** returns the number of unformatted files. */
	private int executeImpl(boolean checkOnly) {
		if (inputDir == null)
			throw new BuildException("missing attribute 'dir'");
		if (outputDir == null)
			outputDir = inputDir;
		if (!inputDir.isDirectory())
			throw new BuildException("input directory '" + inputDir + "' does not exist");
		File[] inputFiles = inputDir.listFiles(new IncludeFileFilter());
		if (!outputDir.isDirectory() && !outputDir.mkdirs())
			throw new BuildException("cannot create output directory '" + outputDir + "'");
		try {
			int countFormattingRequired = 0;
			for (int i = 0; i < inputFiles.length; i++) {
				File inputFile = inputFiles[i];
				log("processing " + inputFile + "...", Project.MSG_DEBUG);
				final String content = readFile(inputFile);
				// a trailing backslash escapes the following newline
				String[] lines = content.split("(?<!\\\\)[\n\r]+");
				String[] sortedLines = processLines(inputFile, lines);
				if (!Arrays.equals(lines, sortedLines) || writeIfUnchanged) {
					if (checkOnly) {
						++countFormattingRequired;
						warn(inputFile + " requires proper formatting");
					}
					else {
						File outputFile = new File(outputDir, inputFile.getName());
						writeFile(outputFile, sortedLines);
					}
				}
			}
			return countFormattingRequired;
		}
		catch (IOException e) {
			throw new BuildException(e);
		}
	}

	private String[] processLines(File inputFile, String[] lines) {
		Arrays.sort(lines, String.CASE_INSENSITIVE_ORDER);
		ArrayList<String> result = new ArrayList<String>(lines.length);
		String lastKey = null;
		String lastValue = null;
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].indexOf('#') == 0 || lines[i].matches("\\s*"))
				continue;
			final String[] keyValue = lines[i].split("\\s*=\\s*", 2);
			if (keyValue.length != 2) {
				// broken line: no '=' sign
				warn(inputFile.getName() + ": no key/val: " + lines[i]);
				continue;
			}
			final String thisKey = keyValue[0];
			final String thisValue = keyValue[1];
			if (lastKey != null && thisKey.equals(lastKey)) {
				if (quality(thisValue) < quality(lastValue)) {
					log(inputFile.getName() + ": drop " + toLine(lastKey, thisValue));
					continue;
				}
				else if (quality(thisValue) == quality(lastValue)) {
					if (thisValue.equals(lastValue)) {
						log(inputFile.getName() + ": drop duplicate " + toLine(lastKey, thisValue));
					}
					else if (quality(thisValue) == QUALITY_MANUALLY_TRANSLATED) {
						warn(inputFile.getName() //
						        + ": drop one of two of equal quality (revisit!):keep: " + toLine(lastKey, lastValue));
						warn(inputFile.getName() //
						        + ": drop one of two of equal quality (revisit!):drop: " + toLine(thisKey, thisValue));
					}
					else {
						log(inputFile.getName() + ": drop " + toLine(lastKey, thisValue));
					}
					continue;
				}
				else {
					log(inputFile.getName() + ": drop " + toLine(lastKey, lastValue));
				}
				lastValue = thisValue;
			}
			else {
				if (lastKey != null)
					result.add(toLine(lastKey, lastValue));
				lastKey = thisKey;
				lastValue = thisValue;
			}
		}
		if (lastKey != null)
			result.add(toLine(lastKey, lastValue));
		String[] resultArray = new String[result.size()];
		return result.toArray(resultArray);
	}

	private String toLine(String key, String value) {
		return key + " = " + value;
	}

	private int quality(String value) {
		if (value.length() == 0)
			return QUALITY_NULL;
		if (value.indexOf("[translate me]") > 0)
			return QUALITY_TRANSLATE_ME;
		if (value.indexOf("[auto]") > 0)
			return QUALITY_AUTO_TRANSLATED;
		return QUALITY_MANUALLY_TRANSLATED;
	}

	private String readFile(final File inputFile) throws IOException {
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

	private void writeFile(File outputFile, String[] lines) throws IOException {
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

	private void warn(String msg) {
		log(msg, Project.MSG_WARN);
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

	/** per default output files will only be created if the output would
	 * differ from the input file. Set attribute <code>writeIfUnchanged</code>
	 * to "true" to enforce file creation. */
	public void setWriteIfUnchanged(boolean writeIfUnchanged) {
		this.writeIfUnchanged = writeIfUnchanged;
	}

	public void setDir(String inputDir) {
		setDir(new File(inputDir));
	}

	public void setDir(File inputDir) {
		this.inputDir = inputDir;
	}

	public void setIncludes(String pattern) {
		includePatterns.add(Pattern.compile(wildcardToRegex(pattern)));
	}

	public void setExcludes(String pattern) {
		excludePatterns.add(Pattern.compile(wildcardToRegex(pattern)));
	}

	/** parameter is set in the build file via the attribute "outputDir" */
	public void setOutputDir(String outputDir) {
		setOutputDir(new File(outputDir));
	}

	/** parameter is set in the build file via the attribute "outputDir" */
	public void setOutputDir(File outputDir) {
		this.outputDir = outputDir;
	}

	public static void main(String[] args) {
		final FormatTranslation formatTranslation = new FormatTranslation();
		final Project project = FormatTranslationCheck.createProject(formatTranslation);
		formatTranslation.setTaskName("format-translation");
		formatTranslation.setProject(project);
		formatTranslation.setDir("/devel/freeplane-bazaar-repo/1_0_x_plain/freeplane/resources/translations");
		formatTranslation.setIncludes("Resources_*.properties");
		formatTranslation
		    .setOutputDir("/devel/freeplane-bazaar-repo/1_0_x_plain/freeplane/resources/translations/sorted");
		formatTranslation.execute();
		System.out.println("done");
	}
}
