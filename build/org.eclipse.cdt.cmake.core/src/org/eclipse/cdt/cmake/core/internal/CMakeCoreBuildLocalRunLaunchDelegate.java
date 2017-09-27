package org.eclipse.cdt.cmake.core.internal;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.build.ICBuildConfiguration;
import org.eclipse.cdt.core.cdtvariables.CdtVariableException;
import org.eclipse.cdt.core.cdtvariables.ICdtVariable;
import org.eclipse.cdt.core.cdtvariables.ICdtVariableManager;
import org.eclipse.cdt.core.envvar.IEnvironmentVariable;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.IBinary;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.debug.core.ICDTLaunchConfigurationConstants;
import org.eclipse.cdt.launch.LaunchUtils;
import org.eclipse.cdt.utils.CommandLineUtil;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
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
		
		File workDirectory = verifyWorkingDirectory(configuration);
		if (workDirectory == null) {
			workDirectory = new File(System.getProperty("user.home", ".")); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		ProcessBuilder builder = new ProcessBuilder(Paths.get(exeFile.getLocationURI()).toString());
		buildConfig.setBuildEnvironment(builder.environment());
		
		String[] arguments = CommandLineUtil.argumentsToArray(args);
		ArrayList<String> command = new ArrayList<>(1 + arguments.length);
		command.add(Paths.get(exeFile.getLocationURI()).toString());
		command.addAll(Arrays.asList(arguments));

		String[] commandArray = command.toArray(new String[command.size()]);
		String[] environment = getLaunchEnvironment(configuration);
		Process process = exec(commandArray, environment, workDirectory);
		
		DebugPlugin.newProcess(launch, process, exeFile.getPath().lastSegment());
	}
	
	/**
	 * Gets the CDT environment from the CDT project's configuration referenced
	 * by the launch
	 * 
	 * This code matches what
	 * org.eclipse.cdt.dsf.gdb.launching.GdbLaunch.getLaunchEnvironment() and
	 * org.eclipse.cdt.dsf.gdb.service.DebugNewProcessSequence.stepSetEnvironmentVariables(RequestMonitor)
	 * do. In the GDB case the former is used as the environment for launching
	 * GDB and the latter for launching the inferior. In the case of run we need
	 * to combine the two environments as that is what the GDB inferior sees.
	 */
	protected String[] getLaunchEnvironment(ILaunchConfiguration config) throws CoreException {
		// Get the project
		String projectName = config.getAttribute(ICDTLaunchConfigurationConstants.ATTR_PROJECT_NAME, (String) null);
		IProject project = null;
		if (projectName == null) {
			IResource[] resources = config.getMappedResources();
			if (resources != null && resources.length > 0 && resources[0] instanceof IProject) {
				project = (IProject) resources[0];
			}
		} else {
			projectName = projectName.trim();
			if (!projectName.isEmpty()) {
				project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
			}
		}

		HashMap<String, String> envMap = new HashMap<String, String>();

		// If the launch configuration is the only environment the inferior should see, just use that
		boolean append = config.getAttribute(ILaunchManager.ATTR_APPEND_ENVIRONMENT_VARIABLES, true);
		boolean environmentCollectedFromProject = false;
		
		if (append && project != null && project.isAccessible()) {
			ICProjectDescription projDesc = CoreModel.getDefault().getProjectDescription(project, false);
			if (projDesc != null) {
				environmentCollectedFromProject = true;

				String buildConfigID = config
						.getAttribute(ICDTLaunchConfigurationConstants.ATTR_PROJECT_BUILD_CONFIG_ID, ""); //$NON-NLS-1$
				ICConfigurationDescription cfg = null;
				if (buildConfigID.length() != 0) {
					cfg = projDesc.getConfigurationById(buildConfigID);
				}

				// if configuration is null fall-back to active
				if (cfg == null) {
					cfg = projDesc.getActiveConfiguration();
				}

				// Environment variables and inherited vars
				IEnvironmentVariable[] vars = CCorePlugin.getDefault().getBuildEnvironmentManager().getVariables(cfg,
						true);
				for (IEnvironmentVariable var : vars) {
					envMap.put(var.getName(), var.getValue());
				}

				// Add variables from build info
				ICdtVariableManager manager = CCorePlugin.getDefault().getCdtVariableManager();
				ICdtVariable[] buildVars = manager.getVariables(cfg);
				for (ICdtVariable var : buildVars) {
					try {
						// The project_classpath variable contributed by JDT is
						// useless for running C/C++ binaries, but it can be
						// lethal if it has a very large value that exceeds
						// shell limit. See
						// http://bugs.eclipse.org/bugs/show_bug.cgi?id=408522
						if (!"project_classpath".equals(var.getName())) {//$NON-NLS-1$
							String value = manager.resolveValue(var.getStringValue(), "", File.pathSeparator, cfg); //$NON-NLS-1$
							envMap.put(var.getName(), value);
						}
					} catch (CdtVariableException e) {
						// Some Eclipse dynamic variables can't be resolved
						// dynamically... we don't care.
					}
				}
			}
		}
		
		if (!environmentCollectedFromProject) {
			// we haven't collected any environment variables from the project settings,
			// therefore simply use the launch settings
			return DebugPlugin.getDefault().getLaunchManager().getEnvironment(config);			
		}
		
		// Now that we have the environment from the project, update it with 
		// the environment settings the user has explicitly set in the launch
		// configuration. There is no API in the launch manager to do this,
		// so we create a temp copy with append = false to get around that.
		ILaunchConfigurationWorkingCopy wc = config.copy(""); //$NON-NLS-1$
		// Don't save this change, it is just temporary, and in just a
		// copy of our launchConfig.
		wc.setAttribute(ILaunchManager.ATTR_APPEND_ENVIRONMENT_VARIABLES, false);
		String[] properties = DebugPlugin.getDefault().getLaunchManager().getEnvironment(wc);
		if (properties != null) {
			for (String env : properties) {
				String[] parts = env.split("=", 2); //$NON-NLS-1$
				if (parts.length == 2) {
					envMap.put(parts[0], parts[1]);
				}
			}
		}
		
		// Turn it into an envp format
		List<String> strings = new ArrayList<String>(envMap.size());
		for (Entry<String, String> entry : envMap.entrySet()) {
			StringBuilder buffer = new StringBuilder(entry.getKey());
			buffer.append('=').append(entry.getValue());
			strings.add(buffer.toString());
		}

		return strings.toArray(new String[strings.size()]);
	}
}
