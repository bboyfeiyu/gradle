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

import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativecode.base.Executable;
import org.gradle.nativecode.base.ExecutableBinary;
import org.gradle.nativecode.base.Flavor;

public class DefaultExecutableBinary extends DefaultNativeBinary implements ExecutableBinary {
    private final Executable executable;

    public DefaultExecutableBinary(Executable executable, Flavor flavor, ToolChainInternal toolChain, DefaultBinaryNamingScheme namingScheme) {
        super(executable, flavor, toolChain, namingScheme.withTypeString("Executable"));
        this.executable = executable;
    }

    public Executable getComponent() {
        return executable;
    }

    public String getOutputFileName() {
        return getToolChain().getExecutableName(getComponent().getBaseName());
    }
}
