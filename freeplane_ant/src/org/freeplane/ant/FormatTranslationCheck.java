/**
 * FormatTranslationCheck.java
 *
 * Copyright (C) 2010,  Volker Boerchers
 *
 * FormatTranslationCheck.java is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * FormatTranslationCheck.java is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General
 * Public License for more details.
 */
package org.freeplane.ant;

import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/** checks if the input files are sorted. */
public class FormatTranslationCheck extends Task {
	private FormatTranslation formatTranslation = new FormatTranslation();
	private boolean failOnError = true;

	public void execute() {
		int countUnformatted = formatTranslation.checkOnly();
                if (countUnformatted == 0) {
                    log("all files are formatted in " + formatTranslation.getDir()); 
                }
                else {
                    final String message = countUnformatted + " files require proper formatting - run format-translation to fix";
                    if (failOnError)
			throw new BuildException(message);
                    else
			formatTranslation.log(message, Project.MSG_ERR);
                }
	}

	public void setDir(String inputDir) {
		formatTranslation.setDir(inputDir);
	}

	public void setDir(File inputDir) {
		formatTranslation.setDir(inputDir);
	}

	public void setIncludes(String pattern) {
		formatTranslation.setIncludes(pattern);
	}

	public void setExcludes(String pattern) {
		formatTranslation.setExcludes(pattern);
	}

	public void setFailOnError(boolean failOnError) {
		this.failOnError = failOnError;
	}

	public static void main(String[] args) {
		final FormatTranslationCheck formatTranslationCheck = new FormatTranslationCheck();
		final Project project = createProject(formatTranslationCheck);
		formatTranslationCheck.setTaskName("check-translation");
		formatTranslationCheck.formatTranslation.setProject(project);
		formatTranslationCheck.formatTranslation.setTaskName("check-translation");
		formatTranslationCheck.setDir("/devel/freeplane-bazaar-repo/1_0_x_plain/freeplane/resources/translations");
		formatTranslationCheck.setIncludes("Resources_*.properties");
		formatTranslationCheck.execute();
		System.out.println("done");
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
