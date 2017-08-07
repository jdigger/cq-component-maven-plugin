package com.citytechinc.cq.component.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;

public class ComponentPlugin implements Plugin<Project> {
    // TODO should be namespaced better
    public static final String PLUGIN_ID = "componentplugin";

    public void apply(Project project) {
        // TODO do via detection
        project.getPlugins().apply(JavaPlugin.class);

        project.getExtensions().create("componentPlugin", ComponentPluginExtension.class);

        final Configuration componentPluginConfiguration = project.getConfigurations().create("componentPlugin");
        componentPluginConfiguration.setTransitive(false);

        final GenerateComponentsTask task = project.getTasks().create("generateComponents", GenerateComponentsTask.class);
    }

}
