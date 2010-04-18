/**
 * FormatTranslationTest.java
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

import static org.junit.Assert.*;

import java.util.Arrays;

import org.apache.tools.ant.Project;
import org.junit.Test;

public class FormatTranslationTest {
	private static final String TRANSLATIONS_SOURCE_DIR = System.getProperty("TRANSLATIONS_SOURCE_DIR");

	@Test
	public void testComparator() {
		String[] strings = { "a.b = z", "a.b.c= y", "a.b= x", "a.b = x" };
		Arrays.sort(strings, FormatTranslation.KEY_COMPARATOR);
		assertEquals("stable sort, only by key", "a.b = z", strings[0]);
		assertEquals("stable sort, only by key", "a.b= x", strings[1]);
		assertEquals("stable sort, only by key", "a.b = x", strings[2]);
		assertEquals("stable sort, only by key", "a.b.c= y", strings[3]);
	}


	@Test
	public void testCheckForEmptyValues() {
		final String regex = "\\s*(\\[auto\\]|\\[translate me\\])*\\s*";
		assertTrue(" [auto]\r".matches(regex));
		assertTrue("[translate me]\r".matches(regex));
		assertTrue("\r".matches(regex));
		assertTrue("".matches(regex));
		assertFalse(" [nix]\r".matches(regex));
	}
	
	@Test
	public void testFormatTranslation() {
		final FormatTranslation formatTranslation = new FormatTranslation();
		final Project project = TranslationUtils.createProject(formatTranslation);
		formatTranslation.setTaskName("format-translation");
		formatTranslation.setProject(project);
		assertNotNull("system property TRANSLATIONS_SOURCE_DIR not set", TRANSLATIONS_SOURCE_DIR);
		formatTranslation.setDir(TRANSLATIONS_SOURCE_DIR);
		formatTranslation.setIncludes("Resources_*.properties");
//		formatTranslation.setOutputDir(FREEPLANE_BASE_DIR + "/freeplane/resources/translations/sorted");
		formatTranslation.execute();
		System.out.println("done");
	}
}
