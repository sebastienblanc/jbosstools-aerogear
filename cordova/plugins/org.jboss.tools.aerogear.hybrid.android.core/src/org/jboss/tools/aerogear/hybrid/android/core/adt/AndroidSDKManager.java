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
package org.jboss.tools.aerogear.hybrid.android.core.adt;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ant.launching.IAntLaunchConstants;
import org.eclipse.core.externaltools.internal.IExternalToolConstants;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.jboss.tools.aerogear.hybrid.android.core.AndroidConstants;
import org.jboss.tools.aerogear.hybrid.android.core.AndroidCore;
import org.jboss.tools.aerogear.hybrid.core.HybridProjectConventions;
import org.jboss.tools.aerogear.hybrid.core.util.ExternalProcessUtility;
import org.jboss.tools.aerogear.hybrid.core.util.TextDetectingStreamListener;

/**
 * Wrapper around the Android CommandLine tools.
 * 
 * 
 * @author Gorkem Ercan
 *
 */
public class AndroidSDKManager {
	
	
	private static class DeviceListParser implements IStreamListener{
		private StringBuffer buffer = new StringBuffer();
		
		@Override
		public void streamAppended(String text, IStreamMonitor monitor) {
			buffer.append(text);
		}
		
		public List<AndroidDevice> getDeviceList(){

			if (buffer == null || buffer.length() < 1)
				return null;

			StringReader reader = new StringReader(buffer.toString());
			BufferedReader read = new BufferedReader(reader);
			String line =null;
			ArrayList<AndroidDevice> list = new ArrayList<AndroidDevice>();
			try{
				while ((line = read.readLine()) != null) {
					if(line.isEmpty() || line.contains("List of devices attached"))
						continue;
					String[] values = line.split("\t");
					if(values.length == 2){
						AndroidDevice device = new AndroidDevice();
						device.setSerialNumber(values[0].trim());
						device.setEmulator(values[0].contains("emulator"));
						
						if("device".equals(values[1].trim())){
							device.setState(AndroidDevice.STATE_DEVICE);
						}
						else if("offline".equals(values[1].trim())){
							device.setState(AndroidDevice.STATE_OFFLINE);
						}
						list.add(device);
					}
					
				}
			}
			catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			return list;
		
		}
		
		
	}
	
	private static class AVDListParser implements IStreamListener{
		private static final String PREFIX_NAME = "Name:";
		private StringBuffer buffer = new StringBuffer();
		@Override
		public void streamAppended(String text, IStreamMonitor monitor) {
			buffer.append(text);
		}
		
		public List<String> getAVDList(){
			if (buffer == null || buffer.length() < 1)
				return null;

			StringReader reader = new StringReader(buffer.toString());
			BufferedReader read = new BufferedReader(reader);
			String line =null;
			ArrayList<String> list = new ArrayList<String>();
			try{
				while ((line = read.readLine()) != null) {
					int idx = line.indexOf(PREFIX_NAME);
					if(idx > -1){
						list.add(line.substring(idx+PREFIX_NAME.length()).trim());
					}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			return list;
		}
		
	}
	
	private static class TargetListParser implements IStreamListener{
	
		private StringBuffer buffer = new StringBuffer();
		
		@Override
		public void streamAppended(String text, IStreamMonitor monitor) {
			buffer.append(text);
		}
		
		
		public List<AndroidSDK> getSDKList() {
			if (buffer == null || buffer.length() < 1)
				return null;

			StringReader reader = new StringReader(buffer.toString());
			BufferedReader read = new BufferedReader(reader);
			ArrayList<AndroidSDK> sdkList = new ArrayList<AndroidSDK>();

			String line = null;
			try {
				AndroidSDK sdk = null;
				while ((line = read.readLine()) != null) {
					final int scolIdx = line.indexOf(':');
					if (scolIdx < 0) {
						continue;
					}
					String[] pair = new String[2];
					pair[0] = line.substring(0, scolIdx).trim();
					pair[1] = line.substring(scolIdx + 1).trim();
					if ("id".equalsIgnoreCase(pair[0])) {
						sdk = new AndroidSDK();
						sdkList.add(sdk);
						int vIndex = pair[1].indexOf("or");
						sdk.setId(pair[1].substring(vIndex + "or".length())
								.replace("\"", ""));
					} else if ("Type".equalsIgnoreCase(pair[0])) {
						Assert.isNotNull(sdk);
						sdk.setType(pair[1].trim());
					} else if ("API level".equalsIgnoreCase(pair[0])) {
						Assert.isNotNull(sdk);
						sdk.setApiLevel(Integer.parseInt(pair[1]));
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return sdkList;
		}
		
	}
	
	public void createProject(AndroidSDK target, String projectName, 
			File path, String activity, String packageName) throws CoreException{
		IStatus status = HybridProjectConventions.validateProjectName(projectName);
		if(!status.isOK())
			throw new CoreException(status);
		//activity class name matches the project name
		status = HybridProjectConventions.validateProjectName(activity);
		if(!status.isOK())
			throw new CoreException(status);
		
		status = HybridProjectConventions.validateProjectID(packageName);
		if(!status.isOK())
			throw new CoreException(status);
	
		ExternalProcessUtility processUtility = new ExternalProcessUtility();
		StringBuilder command = new StringBuilder();
		command.append("android create project");
		command.append(" --target ").append(target.getId());
		command.append(" --path ").append('"').append(path.getPath()).append('"');
		command.append(" --name ").append('"').append(projectName).append('"');
		command.append(" --activity ").append(activity);
		command.append(" --package ").append(packageName);
		
		processUtility.execSync(command.toString(), null, null, null, new NullProgressMonitor(), null, null);
	}
	
	public void startADBServer() throws CoreException{
		ExternalProcessUtility processUtility = new ExternalProcessUtility();
		processUtility.execAsync("adb start-server ", null, null, null,  null);

	}
	
	public List<String> listAVDs() throws CoreException{
		ExternalProcessUtility processUtility = new ExternalProcessUtility();
		AVDListParser parser = new AVDListParser();
		processUtility.execSync("android list avd", null, parser, parser, 
				new NullProgressMonitor(), null, null);
		return parser.getAVDList();
	}
	
	public List<AndroidSDK> listTargets() throws CoreException{
		ExternalProcessUtility processUtility = new ExternalProcessUtility();
		TargetListParser parser = new TargetListParser();
		processUtility.execSync("android list target", 
				null, parser, parser, new NullProgressMonitor(), null, null);
		return parser.getSDKList();
	}
	
	public List<AndroidDevice> listDevices() throws CoreException{
		ExternalProcessUtility processUtility = new ExternalProcessUtility();
		DeviceListParser parser = new DeviceListParser();
		processUtility.execSync("adb devices", null, parser, parser, 
				new NullProgressMonitor(), null, null);
		return parser.getDeviceList();
		
	}
	
	
	public void installApk(File apkFile, String serialNumber) throws CoreException{
		Assert.isNotNull(serialNumber);
		ExternalProcessUtility processUtility = new ExternalProcessUtility();
		StringBuilder command = new StringBuilder("adb");
		command.append(" -s ").append(serialNumber);
		command.append(" install");
		command.append(" -r ");
		command.append("\"").append(apkFile.getPath()).append("\"");
		TextDetectingStreamListener listener = new TextDetectingStreamListener("Success");
		processUtility.execSync(command.toString(), null,listener, listener, new NullProgressMonitor(), null, null);
		if (!listener.isTextDetected()){
			throw new CoreException(new Status(IStatus.ERROR, AndroidCore.PLUGIN_ID, "APK installation did not succeed"));
		}
	}
	
	public void waitForEmulator() throws CoreException{
		while(true){
			List<AndroidDevice> devices = this.listDevices();
			if(devices != null ){
				for (AndroidDevice androidDevice : devices) {
					if(androidDevice.isEmulator() && androidDevice.getState() == AndroidDevice.STATE_DEVICE)
						return;
				}
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void startApp(String component, String serialNumber) throws CoreException{
		ExternalProcessUtility processUtility = new ExternalProcessUtility();
		StringBuilder command = new StringBuilder("adb");
		command.append(" -s ").append(serialNumber);
		command.append(" shell am start");
		command.append(" -n ");
		command.append(component);
		processUtility.execSync(command.toString(), null, null, null,new NullProgressMonitor(), null, null);
		
	}
	
	public void logcat(String filter, IStreamListener outListener, IStreamListener errorListener, String serialNumber) throws CoreException{
		ExternalProcessUtility processUtility = new ExternalProcessUtility();
		StringBuilder command = new StringBuilder("adb");
		command.append(" -s ").append(serialNumber);
		command.append(" logcat");
		if(filter !=null && !filter.isEmpty()){
			command.append(filter);
		}
		processUtility.execAsync(command.toString(), null, outListener, errorListener, null);
	}
	
	
	public void startEmulator(String avd) throws CoreException{
		ExternalProcessUtility processUtility = new ExternalProcessUtility();
		StringBuilder command = new StringBuilder("emulator");
		command.append(" -cpu-delay 0"); 
		command.append(" -no-boot-anim");
		command.append(" -avd ").append(avd);
		processUtility.execAsync(command.toString(), null, null, null, null);
	}
	
	public File buildProject(File projectLocation) throws CoreException{
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType antLaunchConfigType = launchManager.getLaunchConfigurationType(IAntLaunchConstants.ID_ANT_LAUNCH_CONFIGURATION_TYPE);
		if(antLaunchConfigType == null ){
			throw new CoreException(new Status(IStatus.ERROR, AndroidCore.PLUGIN_ID, "Ant launch configuration type is not available"));
		}
		ILaunchConfigurationWorkingCopy wc = antLaunchConfigType.newInstance(null, "Android project builder"); //$NON-NLS-1$
		wc.setContainer(null);
		File buildFile = new File(projectLocation, AndroidConstants.FILE_XML_BUILD);
		if(!buildFile.exists()){
			throw new CoreException(new Status(IStatus.ERROR, AndroidCore.PLUGIN_ID, "build.xml does not exist in "+ projectLocation.getPath()));
		}
		wc.setAttribute(IExternalToolConstants.ATTR_LOCATION, buildFile.getPath());
		wc.setAttribute(IAntLaunchConstants.ATTR_ANT_TARGETS, "debug");
		wc.setAttribute(IAntLaunchConstants.ATTR_DEFAULT_VM_INSTALL, true);

		wc.setAttribute(IExternalToolConstants.ATTR_LAUNCH_IN_BACKGROUND, false);
		wc.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, true);

		ILaunchConfiguration launchConfig = wc.doSave();
        launchConfig.launch(ILaunchManager.RUN_MODE, new NullProgressMonitor(), true, true);
        return new File(projectLocation, AndroidConstants.DIR_BIN);
	}

}
