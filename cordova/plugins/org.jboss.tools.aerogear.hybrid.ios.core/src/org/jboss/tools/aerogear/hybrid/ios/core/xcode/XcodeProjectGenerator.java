/*******************************************************************************
 * Copyright (c) 2013 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.aerogear.hybrid.ios.core.xcode;

import static org.jboss.tools.aerogear.hybrid.core.util.FileUtils.directoryCopy;
import static org.jboss.tools.aerogear.hybrid.core.util.FileUtils.fileCopy;
import static org.jboss.tools.aerogear.hybrid.core.util.FileUtils.templatedFileCopy;
import static org.jboss.tools.aerogear.hybrid.core.util.FileUtils.toURL;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.jboss.tools.aerogear.hybrid.core.HybridProject;
import org.jboss.tools.aerogear.hybrid.core.platform.AbstractPlatformProjectGenerator;
import org.jboss.tools.aerogear.hybrid.ios.core.IOSCore;
import org.osgi.framework.Bundle;

public class XcodeProjectGenerator extends AbstractPlatformProjectGenerator{
	
	public XcodeProjectGenerator(IProject project, File generationFolder) {
		super(project, generationFolder);
	}
	
	@Override
	protected void generateNativeFiles() throws CoreException{
		
		try{
			HybridProject hybridProject = HybridProject.getHybridProject(getProject());
			if(hybridProject == null ){
				throw new CoreException(new Status(IStatus.ERROR, IOSCore.PLUGIN_ID, "Not a hybrid mobile project, can not generate files"));
			}

			generateCordovaLib();
			
			Bundle bundle = IOSCore.getContext().getBundle();
			File destinationDir = getDestination();
			
			String name = hybridProject.getBuildArtifactAppName();
			
			File prjdir = new File(destinationDir, name);
			if( !prjdir.exists() ){//create the project directory
				prjdir.mkdirs();
			}
			directoryCopy(bundle.getEntry("/templates/project/__TESTING__"), toURL(prjdir)  );		
			directoryCopy(bundle.getEntry("/templates/project/__TESTING__.xcodeproj"), toURL(new File(destinationDir, name+".xcodeproj")));	
			
			HashMap<String, String > values = new HashMap<String, String>();
			values.put("__TESTING__", name);
			values.put("--ID--", hybridProject.getAppName());
			
			templatedFileCopy(bundle.getEntry("/templates/project/__TESTING__-Info.plist"), 
					toURL(new File(prjdir, name+"-Info.plist")), 
					values);
			templatedFileCopy(bundle.getEntry("/templates/project/__TESTING__-Prefix.pch"),
					toURL(new File(prjdir, name+"-Prefix.pch")),
					values);
			
			
			templatedFileCopy(bundle.getEntry("/templates/project/__TESTING__.xcodeproj/project.pbxproj"),
					toURL(new File(destinationDir, name+".xcodeproj/project.pbxproj")), 
					values);
			templatedFileCopy(bundle.getEntry("/templates/project/__TESTING__/Classes/AppDelegate.h"),
					toURL(new File(prjdir, "/Classes/AppDelegate.h")),
					values);
			templatedFileCopy(bundle.getEntry("/templates/project/__TESTING__/Classes/AppDelegate.m"),
					toURL(new File(prjdir, "/Classes/AppDelegate.m")),
					values);
			templatedFileCopy(bundle.getEntry("/templates/project/__TESTING__/Classes/MainViewController.h"),
					toURL(new File(prjdir, "/Classes/MainViewController.h")),
					values);			
			templatedFileCopy(bundle.getEntry("/templates/project/__TESTING__/Classes/MainViewController.m"),
					toURL(new File(prjdir, "/Classes/MainViewController.m")),
					values);
			templatedFileCopy(bundle.getEntry("/templates/project/__TESTING__/main.m"),
					toURL(new File(prjdir, "/main.m")),
					values);
		}
		catch(IOException e ){
			throw new CoreException(new Status(IStatus.ERROR,IOSCore.PLUGIN_ID,"Error generating the native iOS project", e));
		}
		
	}
	
	private File generateCordovaLib() throws IOException{
		File cordovaLibDirectory = new File(getDestination(),"CordovaLib");
		if ( !cordovaLibDirectory.exists() ){
			directoryCopy(IOSCore.getContext().getBundle().getEntry("/templates/CordovaLib"), toURL(new File(getDestination(),"CordovaLib")));
		}
		return cordovaLibDirectory;
	}
	
	@Override
	protected String getTargetShortName(){
		return "ios";
	}

	@Override
	protected void replaceCordovaPlatformFiles() throws IOException{
		File cordovaLib = generateCordovaLib();
		fileCopy(toURL(new File(cordovaLib,"cordova.ios.js")), toURL(new File(getPlatformWWWDirectory(), "cordova.js")));
		
	}

	@Override
	protected File getPlatformWWWDirectory() {
		return new File(getDestination(), "www");
	}

}
