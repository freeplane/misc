/**
 * Translator.java
 *
 * Copyright (C) 2009, 2010  Dimitry Polivaev, Volker Boerchers
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import com.google.api.GoogleAPI;
import com.google.api.translate.Language;
import com.google.api.translate.Translate;

/**
 * sourceLang: language code like "en-UK" or "en" (default: en).
 * sourceFile: the file containing the strings to translate.
 * translationsDir: the directory containing the translation files. The files are filtered by includePatterns and/or
 *     excludePatterns. The language of the files is determined by applying the languageCodeFilePattern
 *     to the file names.
 * outputDir: the directory to write the completed translation files to (default: translationsDir).
 * languageCodeFilePattern: a regexp with a match group for the language code. To be applied to a translation file name.
 *     Default: ".*Resources_(.*)\\.properties"
 */
public class Translator extends Task {
	private static HashMap<String, String> languageCodeMapping = new HashMap<String, String>();
	static {
		// Norwegian has two official forms but Google only knows about one "Norwegian"...
		languageCodeMapping.put("nb", "no");
		languageCodeMapping.put("nn", "no");
	}
	private File outputDir;
	private File translationsDir;
	private ArrayList<Pattern> includePatterns = new ArrayList<Pattern>();
	private ArrayList<Pattern> excludePatterns = new ArrayList<Pattern>();
	private File sourceFile;
	private Pattern languageCodeFilePattern = Pattern.compile("^Resources_(.+)\\.properties$");
	private TreeSet<Language> translatedLanguages = new TreeSet<Language>();
	private TreeSet<String> failedLanguages = new TreeSet<String>();
	private TreeSet<String> mappedLanguages = new TreeSet<String>();
	private TreeSet<String> translatedStrings = new TreeSet<String>();
	private TreeSet<String> failedStrings = new TreeSet<String>();
	private TreeSet<String> untranslatedStrings = new TreeSet<String>();
	private Language sourceLanguage = Language.ENGLISH;

	@Override
	public void execute() throws BuildException {
		validate();
		File[] translationFiles = translationsDir.listFiles(new TranslationUtils.IncludeFileFilter(includePatterns,
		    excludePatterns));
		try {
			log("processing " + translationFiles.length + " files...", Project.MSG_DEBUG);
			processFiles(translationFiles);
		}
		catch (IOException e) {
			throw new BuildException(e);
		}
	}

	private void processFiles(File[] translationFiles) throws FileNotFoundException, IOException {
		Properties source = loadFile(sourceFile);
		Map<File, Properties> translationFileTranslationsMap = loadTargetFileTranslationsMap(translationFiles);
		removeSurplusKeys(source, translationFileTranslationsMap);
		translate(source, translationFileTranslationsMap);
		writeTargetFiles(translationFileTranslationsMap);
		final String successMsg = "successfully translated " + translatedStrings.size() + " strings" + " to "
		        + translatedLanguages.size() + " languages";
		log(successMsg
		        + (failedStrings.size() > 0 ? " but failed to translate " + failedStrings.size() + " strings" : "")
		        + (untranslatedStrings.size() > 0 ? " - Google found no translation for " + untranslatedStrings.size()
		                + " strings" : ""));
		log("translated keys: " + translatedStrings, Project.MSG_DEBUG);
		log("translated languages: " + translatedLanguages, Project.MSG_DEBUG);
		if (!failedStrings.isEmpty())
			log("failed strings: " + failedStrings, Project.MSG_DEBUG);
		if (!untranslatedStrings.isEmpty())
			log("untranslated strings: " + untranslatedStrings, Project.MSG_DEBUG);
		if (!mappedLanguages.isEmpty())
			log("used language mappings: " + mappedLanguages, Project.MSG_WARN);
		if (!failedLanguages.isEmpty())
			log("unavailable languages: " + failedLanguages, Project.MSG_WARN);
	}

	private void validate() {
		if (sourceFile == null)
			throw new BuildException("missing attribute 'sourceFile'");
		if (translationsDir == null)
			throw new BuildException("missing attribute 'translationsDir'");
		if (outputDir == null)
			outputDir = translationsDir;
		if (!translationsDir.isDirectory())
			throw new BuildException("input translations directory '" + translationsDir + "' does not exist");
		if (!outputDir.isDirectory() && !outputDir.mkdirs())
			throw new BuildException("cannot create output directory '" + outputDir + "'");
	}

	private void translate(Properties source, Map<File, Properties> translationFileTranslationsMap) {
		GoogleAPI.setHttpReferrer("http://code.google.com/p/i18n-translator/");
		Enumeration<Object> keys = source.keys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement().toString();
			String toTranslate = source.getProperty(key).replaceFirst("&(\\w)", "$1");
			Language[] languageArray = getMissingTranslations(key, toTranslate, translationFileTranslationsMap);
			if (languageArray.length == 0) {
				for (Properties translationProperties : translationFileTranslationsMap.values()) {
					if (translationProperties.containsKey(key)) {
						translationProperties.remove(key);
					}
				}
				continue;
			}
			final List<Language> languageList = Arrays.asList(languageArray);
			translatedLanguages.addAll(languageList);
			log("translating to " + languageList + ": >>" + toTranslate.replace("\n", "\\n") + "<<", Project.MSG_DEBUG);
			String[] translations = callGoogle(toTranslate, sourceLanguage, languageArray);
			evalGoogleResponse(translations, key, toTranslate, translationFileTranslationsMap, languageList);
		}
	}

	private void evalGoogleResponse(String[] translations, String key, String toTranslate,
	                                Map<File, Properties> translationFileTranslationsMap, List<Language> languageList) {
		if (translations == null)
			throw new BuildException("translations must not be null");
		int i = 0;
		for (Properties translationProperties : translationFileTranslationsMap.values()) {
			if (translationProperties.containsKey(key)) {
				translationProperties.remove(key);
				continue;
			}
			final Language language = languageList.get(i);
			String translatedValue = translations[i];
			++i;
			if (translatedValue == null || translatedValue.equals(toTranslate)) {
				if (translatedValue == null) {
					log("can not translate to " + language + ": >>" + toTranslate.replace("\n", "\\n") + "<<",
					    Project.MSG_WARN);
					failedStrings.add(key);
				}
				else {
					untranslatedStrings.add(key);
				}
				translationProperties.setProperty(key, translatedValue + "[translate me]");
				continue;
			}
			translatedStrings.add(key);
			translationProperties.setProperty(key, fixTranslation(toTranslate, translatedValue) + "[auto]");
		}
	}

	private String fixTranslation(String toTranslate, String translatedValue) {
		String result = hexToLower(translatedValue);
		// Google translates "{1}" into "(1)"
		result = toTranslate.matches("\\{\\d\\}") ? result.replaceAll("\\((\\d)\\)", "{$1}") : result;
		// fixme: remove once '$' expansion isn't used anymore
		result = toTranslate.indexOf('$') > 0 ? result.replaceAll("\\$ (\\d)\\b", "\\$$1") : result;
		return result;
	}

	// find all languages without a translation for toTranslate
	private Language[] getMissingTranslations(String key, String toTranslate,
	                                          Map<File, Properties> translationFileTranslationsMap) {
		List<Language> languages = new LinkedList<Language>();
		for (Entry<File, Properties> entry : translationFileTranslationsMap.entrySet()) {
			if (entry.getValue().containsKey(key)) {
				continue;
			}
			languages.add(getLanguage(entry.getKey()));
		}
		Language[] languageArray1 = new Language[languages.size()];
		languages.toArray(languageArray1);
		return languageArray1;
	}

	private String[] callGoogle(String toTranslate, Language originalLanguage, Language[] languageArray) {
		try {
			// translate English string <toTranslate> into multiple languages
			// Google returns the original <toTranslate> for strings that are not translateable
			return Translate.execute(toTranslate, originalLanguage, languageArray);
		}
		catch (Exception e) {
			// The max HTTP GET request size might be exceeded if a long string has to be translated to many languages.
			// So try with only one language at once.
			log("failed for " + languageArray.length + " languages, trying one language at a time for >>"
			        + toTranslate.replace("\n", "\\n") + "<<, error=" + e.getMessage(), Project.MSG_DEBUG);
			final ArrayList<String> list = new ArrayList<String>(Collections.nCopies(languageArray.length,
			    (String) null));
			for (int nTry = 1, errors = 1; nTry < 5 && errors > 0; nTry++) {
				errors = 0;
				for (int i = 0; i < languageArray.length; i++) {
					try {
						if (list.get(i) == null)
							list.set(i, Translate.execute(toTranslate, originalLanguage, languageArray[i]));
					}
					catch (Exception e1) {
						++errors;
						log("failed for " + languageArray[i] + ": >>" + toTranslate.replace("\n", "\\n") //
						        + "<<, error=" + e.getMessage());
					}
				}
				try {
					if (errors > 0)
						Thread.sleep(500);
				}
				catch (InterruptedException e1) {
					// ignore
				}
			}
			String[] result = new String[languageArray.length];
			return list.toArray(result);
		}
	}

	private void removeSurplusKeys(Properties source, Map<File, Properties> translationFileTranslationsMap) {
		for (Properties translationProperties : translationFileTranslationsMap.values()) {
			Enumeration<Object> keys = translationProperties.keys();
			while (keys.hasMoreElements()) {
				String key = keys.nextElement().toString();
				if (!source.containsKey(key)) {
					translationProperties.remove(key);;
				}
			}
		}
	}

	public void setOutputDir(File outputDir) {
		this.outputDir = outputDir;
	}

	/** parameter is set in the build file via the attribute "outputDir" */
	public void setOutputDir(String outputDir) {
		setOutputDir(new File(outputDir));
	}

	public void setTranslationsDir(File translationsDir) {
		this.translationsDir = translationsDir;
	}

	public void setTranslationsDir(String translationsDir) {
		setTranslationsDir(new File(translationsDir));
	}

	public void setIncludes(String pattern) {
		includePatterns.add(Pattern.compile(TranslationUtils.wildcardToRegex(pattern)));
	}

	public void setExcludes(String pattern) {
		excludePatterns.add(Pattern.compile(TranslationUtils.wildcardToRegex(pattern)));
	}

	public void setSourceLanguage(String sourceLanguage) {
		this.sourceLanguage = Language.fromString(sourceLanguage);
		if (sourceLanguage == null)
			throw new BuildException("can not set source language to unknown language '" + sourceLanguage + "'");
	}

	public void setSourceFile(String sourceFile) {
		this.sourceFile = new File(sourceFile);
	}

	public void setSourceFile(File sourceFile) {
		this.sourceFile = sourceFile;
	}

	public void setLanguageCodeFilePattern(Pattern languageCodeFilePattern) {
		this.languageCodeFilePattern = languageCodeFilePattern;
	}

	private Map<File, Properties> loadTargetFileTranslationsMap(final File[] translationFiles)
	        throws FileNotFoundException, IOException {
		Map<File, Properties> translations = new LinkedHashMap<File, Properties>();
		for (final File file : translationFiles) {
			Language language = getLanguage(file);
			if (language == null)
				continue;
			translations.put(file, loadFile(file));
		}
		return translations;
	}

	private static Properties loadFile(File file) throws FileNotFoundException, IOException {
		FileInputStream inStream = new FileInputStream(file);
		Properties source = new Properties();
		source.load(inStream);
		inStream.close();
		return source;
	}

	private void writeTargetFiles(Map<File, Properties> translations) throws FileNotFoundException, IOException {
		for (Entry<File, Properties> entry : translations.entrySet()) {
			Properties outputProperties = entry.getValue();
			if (outputProperties.isEmpty()) {
				continue;
			}
			File outputFile = entry.getKey();
			PrintStream outLn = new PrintStream(new FileOutputStream(outputFile, true));
			outLn.println();
			outputProperties.store(outLn, "automatic translated values");
			outLn.close();
		}
	}

	private Language getLanguage(File file) {
		final String languageCode = getLanguageCode(file);
		if (languageCode == null)
			return null;
		Language language = Language.fromString(languageCode);
		if (language != null) {
			return language;
		}
		else if (languageCode.length() > 2) {
			if (failedLanguages.add(languageCode))
				log("unknown language code " + languageCode + " - trying " + languageCode.substring(0, 2));
			return Language.fromString(languageCode.substring(0, 2));
		}
		else {
			if (failedLanguages.add(languageCode))
				log("unknown language code " + languageCode + " in " + file, Project.MSG_WARN);
			if (languageCode.contains(languageCode)) {
				final String replacement = languageCodeMapping.get(languageCode);
				if (mappedLanguages.add(languageCode)) {
					log("translating to mapped " + replacement + " instead of unavailable " + languageCode,
					    Project.MSG_WARN);
				}
				return Language.fromString(replacement);
			}
			return language;
		}
	}

	private String getLanguageCode(File file) {
		Matcher matcher = languageCodeFilePattern.matcher(file.getName());
		if (!matcher.matches()) {
			log("file name '" + file + "' does not contain a language code for pattern '" + languageCodeFilePattern
			        + "' ignored", Project.MSG_WARN);
			return null;
		}
		return matcher.group(1).replace('_', '-');
	}

	// converts unicode strings to lowercase \u00FC -> \u00fc
	static String hexToLower(String translatedValue) {
		final Pattern pattern = Pattern.compile("\\\\u[0-9a-fA-F]{4}");
		final Matcher matcher = pattern.matcher(translatedValue);
		if (!matcher.find())
			return translatedValue;
		matcher.reset();
		StringBuffer sb = new StringBuffer(translatedValue.length());
		while (matcher.find()) {
			matcher.appendReplacement(sb, "\\" + matcher.group().toLowerCase());
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Automatically translates all entries in a file matching the pattern "*_en.properties" 
	 * expects language resource files in the current directory. Uses
	 * use:
	 * <pre>
	 *   cd work/
	 *   java -cp ../bin:../lib/google-api-translate-java-0.92.jar org.dpolivaev.translator.Translator
	 * </pre>
	 * or use ant:
	 * <pre>
	 *   ant translate
	 *   ls work/
	 * </pre>
	 */
	public static void main(String[] args) throws Exception {
		final Translator translator = new Translator();
		final Project project = TranslationUtils.createProject(translator);
		translator.setTaskName("translator");
		translator.setProject(project);
		File[] translationsFiles = new File(".").listFiles();
		translator.log("processing " + (translationsFiles.length - 1) + " files", Project.MSG_DEBUG);
		translator.setSourceFile(translator.pickSourceFromInfiles(translationsFiles, "_en.properties"));
		final File[] translationFiles = translator.pickTranslationsFromInfiles(translationsFiles, "_en.properties");
		translator.validate();
		translator.processFiles(translationFiles);
	}

	// only for main()
	private File[] pickTranslationsFromInfiles(File[] inFiles, String enSuffix) {
		ArrayList<File> translationFiles = new ArrayList<File>();
		for (final File file : inFiles) {
			if (!file.getName().endsWith(enSuffix)) {
				translationFiles.add(file);
			}
		}
		File[] result = new File[translationFiles.size()];
		translationFiles.toArray(result);
		return result;
	}

	// only for main()
	private File pickSourceFromInfiles(File[] listFiles, String enSuffix) {
		for (final File file : listFiles) {
			if (file.getName().endsWith(enSuffix)) {
				return file;
			}
		}
		return null;
	}
}
