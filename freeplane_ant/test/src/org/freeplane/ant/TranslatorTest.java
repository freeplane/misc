/**
 * TranslatorTest.java
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

import static junit.framework.Assert.assertEquals;

import java.util.Iterator;
import java.util.Vector;

import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.BuildLogger;
import org.apache.tools.ant.Project;
import org.junit.Test;

public class TranslatorTest {
	@Test
	public void testHexToLower() {
		assertEquals("\\u00af", Translator.hexToLower("\\u00AF"));
		assertEquals("ab\\u00afcd", Translator.hexToLower("ab\\u00AFcd"));
		assertEquals("\\u00af\\u00af", Translator.hexToLower("\\u00AF\\u00Af"));
		assertEquals("ab\\uabcdcd", Translator.hexToLower("ab\\uABCDcd"));
		// care for the case of the \\u
		assertEquals("ab\\UABCDcd", Translator.hexToLower("ab\\UABCDcd"));
	}
	
	@Test
	public void testTask() {
		final long startMillis = System.currentTimeMillis();
		final String FREEPLANE_BASE_DIR = "/devel/freeplane-bazaar-repo/1_0_x_plain";
		final Translator translator = new Translator();
		final Project project = TranslationUtils.createProject(translator);
		setLogLevel(project, Project.MSG_DEBUG);
		translator.setTaskName("translator");
		translator.setProject(project);
		translator.setTranslationsDir(FREEPLANE_BASE_DIR + "/freeplane/resources/translations");
		translator.setIncludes("Resources_*.properties");
		translator.setOutputDir(FREEPLANE_BASE_DIR + "/freeplane/resources/translations/sorted");
		translator.setSourceFile(FREEPLANE_BASE_DIR + "/freeplane/viewer-resources/translations/Resources_en.properties");
		translator.execute();
		translator.log("translation took " + formatTimeDiff(startMillis, System.currentTimeMillis()));
	}

	@SuppressWarnings("unchecked")
    private void setLogLevel(Project project, int logLevel) {
        Vector<BuildListener> listeners = project.getBuildListeners();
        for (Iterator i = listeners.iterator(); i.hasNext(); ) {
            BuildListener listener = (BuildListener) i.next();

            if (listener instanceof BuildLogger) {
                BuildLogger logger = (BuildLogger) listener;
                logger.setMessageOutputLevel(logLevel);
            }
        }
    }

	private String formatTimeDiff(long startMillis, long currentTimeMillis) {
		long s = Math.round((currentTimeMillis - startMillis)/1000.);
	    return String.format("%02d:%02d minutes", s/60, s%60);
    }
}
