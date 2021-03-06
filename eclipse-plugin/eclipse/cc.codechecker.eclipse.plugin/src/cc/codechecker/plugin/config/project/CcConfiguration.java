package cc.codechecker.plugin.config.project;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.envvar.IContributedEnvironment;
import org.eclipse.cdt.core.envvar.IEnvironmentVariable;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.osgi.service.prefs.BackingStoreException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import cc.codechecker.api.runtime.CodeCheckEnvironmentChecker;
import cc.codechecker.api.runtime.CodecheckerServerThread;
import cc.codechecker.api.runtime.EnvironmentDifference;
import cc.codechecker.api.runtime.EnvironmentDifference.ModificationAction;
import cc.codechecker.plugin.CodeCheckerNature;
import cc.codechecker.plugin.config.CodeCheckerContext;

import java.nio.file.Files;

public class CcConfiguration {

    public static String CODECHECKER_DIRECTORY_KEY = "server_url";
    public static String PYTHON_ENV_KEY = "location_prefix";
    static CodecheckerServerThread ct = null;
    IProject project;
    IEclipsePreferences projectPreferences;

    public CcConfiguration(IProject project) {
        super();

        this.project = project;

        try {
            if (project.hasNature(CodeCheckerNature.NATURE_ID)) {
                IScopeContext context = new ProjectScope(project);
                projectPreferences = context.getNode(CodeCheckerNature.NATURE_ID);
            }
        } catch (CoreException e) {
            e.printStackTrace();
        }
    }

    public static void modifyProjectEnvironmentVariables(IProject project,
            ImmutableList<EnvironmentDifference> env) {
        IContributedEnvironment ice = CCorePlugin.getDefault().getBuildEnvironmentManager()
                .getContributedEnvironment();
        ICProjectDescription prjd = CoreModel.getDefault().getProjectDescription(project, true);
        ICConfigurationDescription cfgd = prjd.getActiveConfiguration();

        for (EnvironmentDifference ed : env) {
            ice.addVariable(ed.variableName, ed.parameter, toVariableAction(ed.action), "", cfgd);
        }

        try {
            CoreModel.getDefault().setProjectDescription(project, prjd);
        } catch (CoreException e) {
            e.printStackTrace();
        }
    }

    private static int toVariableAction(ModificationAction action) {
        switch (action) {
            case ADD:
                return IEnvironmentVariable.ENVVAR_REPLACE;
            case REPLACE:
                return IEnvironmentVariable.ENVVAR_REPLACE;
            case APPEND:
                return IEnvironmentVariable.ENVVAR_APPEND;
            case PREPEND:
                return IEnvironmentVariable.ENVVAR_PREPEND;
            case REMOVE:
                return IEnvironmentVariable.ENVVAR_REMOVE;
        }
        return IEnvironmentVariable.ENVVAR_REPLACE;
    }

    public String getServerUrl() {
        try {
            return CodeCheckerContext.getInstance().getServerObject(project).getServerUrl();
        } catch (Exception e) {
            return "http://localhost:11444/";
        }
    }

    public String getCodecheckerDirectory() {
        return projectPreferences.get(CODECHECKER_DIRECTORY_KEY, "");
    }

    public Optional<String> getPythonEnv() {
        String s = projectPreferences.get(PYTHON_ENV_KEY, "");
        if (s.isEmpty()) {
            return Optional.absent();
        } else {
            return Optional.of(s);
        }
    }

    public void update(String serverUrl, String locationPrefix) {
        projectPreferences.put(CODECHECKER_DIRECTORY_KEY, serverUrl);
        projectPreferences.put(PYTHON_ENV_KEY, locationPrefix);

        try {
            projectPreferences.flush();

            updateServer(project, CodeCheckerContext.getInstance().getServerObject(project));
        } catch (BackingStoreException e) {
        }
    }

    public String convertFilenameToServer(String projectRelativeFile) {
        return getLocationPrefix() + projectRelativeFile;
    }

    public String getLocationPrefix() {
        // TODO Auto-generated method stub
        return project.getLocation().toOSString() + "/";
    }

    public String convertFilenameFromServer(String serverFile) {
        if (getLocationPrefix().equals("")) {
            return serverFile;
        }
        return serverFile.replace(getLocationPrefix(), "");
    }

    public boolean isConfigured() {
        return !getServerUrl().equals("");
    }

    public void updateServer(IProject project, CodecheckerServerThread server) {

        String location = getCodecheckerDirectory();
        try {
            System.out.println(project.getLocation());
            CodeCheckEnvironmentChecker ccec = new CodeCheckEnvironmentChecker(getPythonEnv(),
                    location, project.getLocation() + "/cc_Workspace");

            server.setCodecheckerEnvironment(ccec);

            modifyProjectEnvironmentVariables(project, ccec.environmentDifference);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
