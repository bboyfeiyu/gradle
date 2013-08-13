/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativecode.base.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.AbstractBuildableModelElement;
import org.gradle.language.base.internal.BinaryNamingScheme;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativecode.base.Flavor;
import org.gradle.nativecode.base.NativeComponent;
import org.gradle.nativecode.base.NativeDependencySet;
import org.gradle.nativecode.base.tasks.BuildBinaryTask;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

public abstract class DefaultNativeBinary extends AbstractBuildableModelElement implements NativeBinaryInternal {
    private final NotationParser<Set<LanguageSourceSet>> sourcesNotationParser = SourceSetNotationParser.parser();
    private final ResolvableNativeDependencySet libs = new ResolvableNativeDependencySet();
    private final DomainObjectSet<LanguageSourceSet> source = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class);
    private final ArrayList<Object> compilerArgs = new ArrayList<Object>();
    private final ArrayList<Object> assemblerArgs = new ArrayList<Object>();
    private final ArrayList<Object> linkerArgs = new ArrayList<Object>();
    private final ArrayList<Object> defines = new ArrayList<Object>();
    private final BinaryNamingScheme namer;
    private final String description;
    private final Flavor flavor;
    private final ToolChainInternal toolChain;
    private BuildBinaryTask builderTask;
    private File outputFile;

    protected DefaultNativeBinary(NativeComponent owner, Flavor flavor, String typeString, ToolChainInternal toolChain) {
        // TODO:DAZ Would be better to inject the Namer here, rather than trying to construct out of context
        // TODO:DAZ Make static/shared a dimension in the variant space, rather than a special case
        String baseName = owner.getName() + StringUtils.capitalize(typeString);
        List<String> nameDimensions = new ArrayList<String>();
        if (owner.getFlavors().size() > 1) {
            nameDimensions.add(flavor.getName());
        }
        nameDimensions.add(toolChain.getName());
        namer = new DefaultBinaryNamingScheme(baseName, nameDimensions);

        this.flavor = flavor;
        this.toolChain = toolChain;
        this.description = String.format("%s '%s'", GUtil.toWords(typeString), namer.getLifecycleTaskName());
        owner.getSource().all(new Action<LanguageSourceSet>() {
            public void execute(LanguageSourceSet sourceSet) {
                source.add(sourceSet);
            }
        });
    }

    @Override
    public String toString() {
        return description;
    }

    public Flavor getFlavor() {
        return flavor;
    }

    public BuildBinaryTask getBuilderTask() {
        return builderTask;
    }

    public void setBuilderTask(BuildBinaryTask builderTask) {
        this.builderTask = builderTask;
        dependsOn(builderTask);
    }

    public String getName() {
        return namer.getLifecycleTaskName();
    }

    public ToolChainInternal getToolChain() {
        return toolChain;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public DomainObjectSet<LanguageSourceSet> getSource() {
        return source;
    }

    public void source(Object sources) {
        source.addAll(sourcesNotationParser.parseNotation(sources));
    }

    public List<Object> getMacros() {
        return defines;
    }

    public void define(Object... defines) {
        Collections.addAll(this.defines, defines);
    }

    public List<Object> getCompilerArgs() {
        return compilerArgs;
    }

    public void compilerArgs(Object... args) {
        Collections.addAll(compilerArgs, args);
    }

    public List<Object> getAssemblerArgs() {
        return assemblerArgs;
    }

    public void assemblerArgs(Object... args) {
        Collections.addAll(assemblerArgs, args);
    }

    public List<Object> getLinkerArgs() {
        return linkerArgs;
    }

    public void linkerArgs(Object... args) {
        Collections.addAll(linkerArgs, args);
    }

    public BinaryNamingScheme getNamingScheme() {
        return namer;
    }

    public Collection<NativeDependencySet> getLibs() {
        return libs.resolve(this);
    }

    public void lib(Object notation) {
        libs.add(notation);
    }

    public abstract String getOutputFileName();

    protected abstract NativeComponent getComponent();

}
