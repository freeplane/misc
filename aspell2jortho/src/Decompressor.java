import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.zip.InflaterInputStream;

/*
 *  Copyright (C) 2009 Dimitry Polivaev
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * @author Dimitry Polivaev
 * Sep 6, 2009
 */
public class Decompressor {
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
        if (args.length != 2) {
        	System.err.println("usage: java Compressor <input file> <output file>");
        	return;
        }
		new Decompressor().save(args[0], args[1]);
	}
	void save(String inFile, String outFile) throws Exception {
		File dictFile = new File(inFile);
		File outputFile = new File(outFile);
		InputStream input = new BufferedInputStream(new FileInputStream(dictFile));
		input = new InflaterInputStream(input);
		InputStreamReader reader = new InputStreamReader(input, "UTF8");
		OutputStream dict = new BufferedOutputStream(new FileOutputStream(outputFile));
		dict = new BufferedOutputStream(dict);
		PrintStream dictPs = new PrintStream(dict, false, "UTF8");
		while (reader.ready()) {
			final char c = (char)reader.read();
			dictPs.append(c);
		}
		dictPs.close();
		System.out.println("Uncompressed dictionary size on disk (bytes):" + outputFile.length());
	}
}
