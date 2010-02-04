

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/*
 *  Copyright (C) 2009 Dimitry Polivaev
 *  Copyright (C) 2005-2008 by i-net software
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
public class Text2Book {
	private String[] words;

	/**  
	* @param args  
	* @throws Exception  
	*/
	public static void main(String[] args) throws Exception {
		try {
	        if (args.length != 2) {
	        	System.err.println("usage: java Text2Book <language> <input file name>");
	        }
	        File in = new File(args[1]);
	        String encoding = "UTF-8";
	        final Text2Book text2Book = new Text2Book();
	        text2Book.loadWords(in, encoding);
	        text2Book.save(args[0]);
        }
        catch (Exception e) {
        	System.gc();
        	System.err.println("language " + args[0]);
        	final String message = e.getMessage();
			if(message != null) System.err.println(message);
        	e.printStackTrace();
        }
	}

	private void loadWords(File in, String encoding) throws Exception {
		final FileInputStream fileInputStream = new FileInputStream(in);
		BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, encoding));
		SortedSet<String> wordList = new TreeSet<String>();
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			final int length = line.length();
			if (length == 0) {
				continue;
			}
			int first = 0;
			for(;;){
				int  last = nextIndex(line, new char[]{'/', '\''},  first);
				if(last == -1){
					wordList.add(line.substring(first));
					break;
				}
				wordList.add(line.substring(first, last));
				first = last + 1;
			}
		}
		wordList.remove("");
		words = wordList.toArray(new String[wordList.size()]);
	}

	private int nextIndex(String line, char[] chars, int first) {
		int result = line.length();
		 for(char c:chars){
			 final int index = line.indexOf(c, first);
			 if(index == -1){
				 continue;
			 }
			 result = Math.min(result, index);
		 }
		 return result == line.length() ? -1 : result;
    }

	void save(String language) throws Exception {
		File dictFile = new File("dictionary_" + language + ".ortho");
		OutputStream dict = new FileOutputStream(dictFile);
		dict = new BufferedOutputStream(dict);
		Deflater deflater = new Deflater();
		deflater.setLevel(Deflater.BEST_COMPRESSION);
		dict = new DeflaterOutputStream(dict, deflater);
		dict = new BufferedOutputStream(dict);
		PrintStream dictPs = new PrintStream(dict, false, "UTF8");
		//Speichern als Wordliste  
		for (int i = 0; i < words.length; i++) {
			dictPs.print(words[i] + '\n');
		}
		//ps.close();  
		dictPs.close();
		System.out.println("Dictionary size on disk (bytes):" + dictFile.length());
	}
}
