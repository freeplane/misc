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
			for (int i = 0; i < inputFiles.length; i++) {
				File inputFile = inputFiles[i];
				log("processing " + inputFile + "...");
				final String content = readFile(inputFile);
				String[] lines = content.split("[\n\r]+");
				String[] sortedLines = processLines(inputFile, lines);
				if (!Arrays.equals(lines, sortedLines) || writeIfUnchanged) {
					File outputFile = new File(outputDir, inputFile.getName());
					writeFile(outputFile, sortedLines);
				}
			}
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
				warn(inputFile.getName() + ": no key/val: " + lines[i]);
				continue;
			}
			if (lastKey != null && keyValue[0].equals(lastKey)) {
				if (quality(keyValue[1]) <= quality(lastValue)) {
					log(inputFile.getName() + ": dropping " + toLine(lastKey, keyValue[1]));
					continue;
				}
				else {
					log(inputFile.getName() + ": dropping " + toLine(lastKey, lastValue));
				}
				lastValue = keyValue[1];
			}
			else {
				if (lastKey != null)
					result.add(toLine(lastKey, lastValue));
				lastKey = keyValue[0];
				lastValue = keyValue[1];
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
		if (value.indexOf("[translate me]") > 0)
			return 0;
		if (value.indexOf("[auto]") > 0)
			return 1;
		return 2;
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
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "US-ASCII"));
			for (int i = 0; i != lines.length; i++) {
				out.write(lines[i]);
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

	private void log(Object o) {
		System.out.println(o);
	}

	private void warn(Object o) {
		System.err.println(o);
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

	public void setDir(File file) {
		this.inputDir = file;
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
		formatTranslation.setDir("/devel/freeplane-bazaar-repo/1_0_x_plain/freeplane/resources/translations");
		formatTranslation.setIncludes("Resources_*.properties");
		formatTranslation.setOutputDir("/devel/freeplane-bazaar-repo/1_0_x_plain/freeplane/resources/translations/sorted");
		formatTranslation.execute();
		System.out.println("done");
	}
}
