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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativecode.toolchain.ConfigurableToolChain;
import org.gradle.nativecode.toolchain.Tool;
import org.gradle.nativecode.toolchain.internal.ToolRegistry;
import org.gradle.nativecode.toolchain.internal.ToolType;

import java.io.File;
import java.util.List;

public abstract class AbstractToolChain implements ToolChainInternal, ConfigurableToolChain {
    private final String name;
    protected final OperatingSystem operatingSystem;
    protected final ToolRegistry tools;
    private final FileResolver fileResolver;
    private ToolChainAvailability availability;

    protected AbstractToolChain(String name, OperatingSystem operatingSystem, ToolRegistry tools, FileResolver fileResolver) {
        this.name = name;
        this.operatingSystem = operatingSystem;
        this.tools = tools;
        this.fileResolver = fileResolver;
    }

    public String getName() {
        return name;
    }

    protected abstract String getTypeName();

    @Override
    public String toString() {
        return String.format("ToolChain '%s' (%s)", getName(), getTypeName());
    }

    public ToolChainAvailability getAvailability() {
        if (availability == null) {
            availability = new ToolChainAvailability();
            checkAvailable(availability);
        }
        return availability;
    }

    protected void checkAvailable() {
        if (!getAvailability().isAvailable()) {
            throw new IllegalStateException(String.format("Tool chain %s is not available", getName()));
        }
    }

    public String getOutputType() {
        return String.format("%s-%s", getName(), operatingSystem.getName());
    }

    protected abstract void checkAvailable(ToolChainAvailability availability);

    public String getExecutableName(String executablePath) {
        return operatingSystem.getExecutableName(executablePath);
    }

    public String getSharedLibraryName(String libraryName) {
        return operatingSystem.getSharedLibraryName(libraryName);
    }

    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName);
    }

    public String getStaticLibraryName(String libraryName) {
        return operatingSystem.getStaticLibraryName(libraryName);
    }

    public List<File> getPaths() {
        return tools.getPath();
    }

    public void path(Object... paths) {
        for (Object path : paths) {
            tools.path(resolve(path));
        }
    }

    protected File resolve(Object path) {
        return fileResolver.resolve(path);
    }

    public Tool getCppCompiler() {
        return new DefaultTool(ToolType.CPP_COMPILER);
    }

    public Tool getCCompiler() {
        return new DefaultTool(ToolType.C_COMPILER);
    }

    public Tool getAssembler() {
        return new DefaultTool(ToolType.ASSEMBLER);
    }

    public Tool getLinker() {
        return new DefaultTool(ToolType.LINKER);
    }

    public Tool getStaticLibArchiver() {
        return new DefaultTool(ToolType.STATIC_LIB_ARCHIVER);
    }

    private class DefaultTool implements Tool {
        private final ToolType toolType;

        private DefaultTool(ToolType toolType) {
            this.toolType = toolType;
        }

        public String getExe() {
            return tools.getExeName(toolType);
        }

        public void setExe(String file) {
            tools.setExeName(toolType, file);
        }
    }
}
