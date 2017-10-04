package org.eclipse.cdt.cmake.core.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.eclipse.cdt.core.build.ICBuildConfiguration;
import org.eclipse.cdt.core.model.IBinary;
import org.eclipse.cdt.launch.LaunchUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.launchbar.core.target.ILaunchTarget;
import org.eclipse.launchbar.core.target.launch.ITargetedLaunch;

public class CMakeCoreBuildLocalRunLaunchDelegate extends AbstractCMakeCoreBuildLaunchConfigDelegate {

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		IProject project = getProject(configuration);
		ILaunchTarget target = ((ITargetedLaunch) launch).getLaunchTarget();

		ICBuildConfiguration buildConfig = getBuildConfiguration(project, mode, target, monitor);
		IBinary exeFile = getBinary(buildConfig);

		String args = LaunchUtils.getProgramArguments(configuration);
		if(args == null) {
			args = new String();
		}
		
		File workDirectory = verifyWorkingDirectory(configuration);
		if (workDirectory == null) {
			workDirectory = new File(System.getProperty("user.home", ".")); //$NON-NLS-1$ //$NON-NLS-2$
		}
				
		try {
			ProcessBuilder builder = new ProcessBuilder(Paths.get(exeFile.getLocationURI()).toString(),args).directory(workDirectory);
			buildConfig.setBuildEnvironment(builder.environment());
			Process process = builder.start();
			DebugPlugin.newProcess(launch, process, exeFile.getPath().lastSegment());
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.getId(), "Error launching", e));
		}
		
	}
	
}
