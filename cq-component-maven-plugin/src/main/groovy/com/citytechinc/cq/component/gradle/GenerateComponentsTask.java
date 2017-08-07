package com.citytechinc.cq.component.gradle;

import com.citytechinc.cq.component.annotations.config.Widget;
import com.citytechinc.cq.component.dialog.ComponentNameTransformer;
import com.citytechinc.cq.component.dialog.widget.WidgetRegistry;
import com.citytechinc.cq.component.dialog.widget.impl.DefaultWidgetRegistry;
import com.citytechinc.cq.component.editconfig.registry.DefaultInPlaceEditorRegistry;
import com.citytechinc.cq.component.editconfig.registry.InPlaceEditorRegistry;
import com.citytechinc.cq.component.maven.util.ComponentMojoUtil;
import com.citytechinc.cq.component.touchuidialog.widget.registry.DefaultTouchUIWidgetRegistry;
import com.citytechinc.cq.component.touchuidialog.widget.registry.TouchUIWidgetRegistry;
import com.google.common.base.Predicate;
import groovy.util.ObservableList;
import javassist.ClassPool;
import javassist.CtClass;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.util.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.util.Configurable;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.slf4j.MarkerFactory;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class GenerateComponentsTask extends DefaultTask {
    private File archiveFile;

    @TaskAction
    public void generateComponents() throws NoSuchFieldException {
        final Configuration componentPluginConfiguration = getProject().getConfigurations().getByName("componentPlugin");
        final Set<File> files = componentPluginConfiguration.resolve();

        final Set<URL> urlList = new HashSet<>();

        final List<Class<?>> coreLibraryClasses = Arrays.asList(
                GenerateComponentsTask.class,
                Widget.class,
                ComponentMojoUtil.class,
                DefaultTask.class,
                Configurable.class,
                GradleException.class,
                StandardOutputCapture.class,
                ObservableList.class,
                MarkerFactory.class,
                ConsoleMetaData.class,
                ListenerBroadcast.class,
                ClassPool.class,
                org.reflections.Configuration.class,
                ArchiveEntry.class,
                org.apache.commons.compress.archivers.ArchiveEntry.class,
                Predicate.class,
                StringUtils.class,
                IOUtils.class
        );
        for (Class clazz : coreLibraryClasses) {
            urlList.add(ClasspathHelper.forClass(clazz, this.getClass().getClassLoader()));
        }

        for (File file : files) {
            urlList.add(file2url(file));
        }

        final URL[] urls = urlList.toArray(new URL[urlList.size()]);
        ClassLoader classLoader = new URLClassLoader(urls, String.class.getClassLoader());

        ComponentPluginExtension componentPluginExtension = getProject().getExtensions().getByType(ComponentPluginExtension.class);
        final String transformerName = componentPluginExtension.getTransformerName();
        final File buildDir = getProject().getBuildDir();
        final String componentPathBase = componentPluginExtension.getComponentPathBase();
        final String componentPathSuffix = componentPluginExtension.getComponentPathSuffix();
        final String defaultComponentGroup = componentPluginExtension.getDefaultComponentGroup();
        final File archiveFileForProject = getArchiveFileForProject();
        final File tempArchiveFileForProject = getTempArchiveFileForProject();
        final boolean generateTouchUiDialogs = componentPluginExtension.getGenerateTouchUiDialogs();
        final boolean generateClassicUiDialogs = componentPluginExtension.getGenerateClassicUiDialogs();

        callIsolatedGenerateComponents(classLoader, transformerName, buildDir, componentPathBase, componentPathSuffix, defaultComponentGroup, archiveFileForProject, tempArchiveFileForProject, generateTouchUiDialogs, generateClassicUiDialogs);
    }


    /**
     * Generate the components in its own classloader as an optimization since it does classpath scanning.
     * <p>
     * In the simple case, results in at least a 10x performance improvement.
     */
    private static void callIsolatedGenerateComponents(ClassLoader classLoader, String transformerName, File buildDir,
                                                       String componentPathBase, String componentPathSuffix,
                                                       String defaultComponentGroup, File archiveFileForProject,
                                                       File tempArchiveFileForProject, boolean generateTouchUiDialogs,
                                                       boolean generateClassicUiDialogs) {
        try {
            final Class<?> aClass = ReflectionUtils.forName(GenerateComponentsTask.class.getName(), classLoader);
            final Method generateTheComponents = aClass.getDeclaredMethod("generateTheComponents",
                    String.class,
                    File.class,
                    String.class,
                    String.class,
                    String.class,
                    File.class,
                    File.class,
                    boolean.class,
                    boolean.class);
            generateTheComponents.setAccessible(true);
            generateTheComponents.invoke(null,
                    transformerName,
                    buildDir,
                    componentPathBase,
                    componentPathSuffix,
                    defaultComponentGroup,
                    archiveFileForProject,
                    tempArchiveFileForProject,
                    generateTouchUiDialogs,
                    generateClassicUiDialogs);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }


    @SuppressWarnings({"RedundantTypeArguments", "unused"})
    private static void generateTheComponents(String transformerName, File buildDir, String componentPathBase,
                                              String componentPathSuffix,
                                              String defaultComponentGroup,
                                              File archiveFileForProject,
                                              File tempArchiveFileForProject,
                                              boolean generateTouchUiDialogs,
                                              boolean generateClassicUiDialogs) {
        final ClassLoader classLoader = GenerateComponentsTask.class.getClassLoader();

        ClassPool classPool = ComponentMojoUtil.getClassPool(classLoader);

        Reflections reflections = ComponentMojoUtil.getReflections(classLoader);

        try {
            //TODO: Implement excludedDependences
            List<CtClass> classList = ComponentMojoUtil.getAllComponentAnnotations(classPool, reflections, Collections.<String>emptySet());

            WidgetRegistry widgetRegistry = new DefaultWidgetRegistry(classPool, classLoader, reflections, Collections.<String>emptyList());

            TouchUIWidgetRegistry touchUIWidgetRegistry = new DefaultTouchUIWidgetRegistry(classPool, classLoader, reflections, Collections.<String>emptyList());

            InPlaceEditorRegistry inPlaceEditorRegistry = new DefaultInPlaceEditorRegistry(classPool, classLoader, reflections);

            Map<String, ComponentNameTransformer> transformers = ComponentMojoUtil.getAllTransformers(classPool, reflections);

            ComponentNameTransformer transformer = transformers.get(transformerName);

            if (transformer == null) {
                throw new GradleException("The configured transformer wasn't found");
            }

            ComponentMojoUtil.buildArchiveFileForProjectAndClassList(classList, widgetRegistry, touchUIWidgetRegistry,
                    inPlaceEditorRegistry, classLoader, classPool, buildDir,
                    componentPathBase, componentPathSuffix,
                    defaultComponentGroup, archiveFileForProject,
                    tempArchiveFileForProject, transformer, generateTouchUiDialogs,
                    generateClassicUiDialogs);
        } catch (Exception e) {
            if (e instanceof GradleException) throw (GradleException) e;
            throw new GradleException(e.getMessage(), e);
        }
    }

    private static URL file2url(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @InputFile
    @OutputFile
    @Optional
    public File getArchiveFileForProject() {
        if (archiveFile == null) {
            File buildDirectory = new File(getProject().getBuildDir(), "distributions");

            String zipFileName = getProject().getName() + "-" + getProject().getVersion() + ".zip";

            archiveFile = new File(buildDirectory, zipFileName);
        }

        return archiveFile;
    }

    public File getTempArchiveFileForProject() {
        String zipFileName = getProject().getName() + "-" + getProject().getVersion() + "-temp.zip";

        return new File(getProject().getBuildDir(), zipFileName);
    }

    public File getArchiveFile() {
        return archiveFile;
    }

    public void setArchiveFile(File archiveFile) {
        this.archiveFile = archiveFile;
    }

}
