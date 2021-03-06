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

package org.gradle.tooling.internal.provider;

import com.google.common.collect.Maps;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.ClassLoaderVisitor;
import org.gradle.internal.classloader.MutableURLClassLoader;
import org.gradle.util.CollectionUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class DefaultPayloadClassLoaderRegistry implements PayloadClassLoaderRegistry {
    private final Lock lock = new ReentrantLock();
    private final ModelClassLoaderFactory classLoaderFactory;
    // TODO:ADAM - don't use strong references
    private final Map<ClassLoader, ClassLoaderDetails> classLoaderDetails = Maps.newHashMap();
    private final Map<UUID, ClassLoader> classLoaderIds = Maps.newHashMap();

    public DefaultPayloadClassLoaderRegistry(ModelClassLoaderFactory modelClassLoaderFactory) {
        this.classLoaderFactory = modelClassLoaderFactory;
    }

    public ClassLoader getClassLoader(ClassLoaderDetails details) {
        lock.lock();
        try {
            ClassLoader classLoader = classLoaderIds.get(details.uuid);
            if (classLoader != null) {
                return classLoader;
            }

            List<ClassLoader> parents = new ArrayList<ClassLoader>();
            for (ClassLoaderDetails parentDetails : details.parents) {
                parents.add(getClassLoader(parentDetails));
            }
            if (parents.isEmpty()) {
                parents.add(classLoaderFactory.getClassLoaderFor(ClassLoaderSpec.SYSTEM_CLASS_LOADER, null));
            }

            System.out.println(String.format("=> Creating ClassLoader %s from %s and %s", details.uuid, details.spec, parents));

            classLoader = classLoaderFactory.getClassLoaderFor(details.spec, parents);
            classLoaderIds.put(details.uuid, classLoader);
            classLoaderDetails.put(classLoader, details);
            return classLoader;
        } finally {
            lock.unlock();
        }
    }

    public ClassLoaderDetails getDetails(ClassLoader classLoader) {
        lock.lock();
        try {
            ClassLoaderDetails details = classLoaderDetails.get(classLoader);
            if (details != null) {
                return details;
            }

            ClassLoaderSpecVisitor visitor = new ClassLoaderSpecVisitor(classLoader);
            visitor.visit(classLoader);

            if (visitor.spec == null) {
                if (visitor.classPath == null) {
                    visitor.spec = ClassLoaderSpec.SYSTEM_CLASS_LOADER;
                } else {
                    visitor.spec = new MutableURLClassLoader.Spec(CollectionUtils.toList(visitor.classPath));
                }
            }

            UUID uuid = UUID.randomUUID();
            details = new ClassLoaderDetails(uuid, visitor.spec);
            for (ClassLoader parent : visitor.parents) {
                details.parents.add(getDetails(parent));
            }

            classLoaderDetails.put(classLoader, details);
            classLoaderIds.put(details.uuid, classLoader);
            return details;
        } finally {
            lock.unlock();
        }
    }

    private static class ClassLoaderSpecVisitor extends ClassLoaderVisitor {
        final ClassLoader classLoader;
        final List<ClassLoader> parents = new ArrayList<ClassLoader>();
        ClassLoaderSpec spec;
        URL[] classPath;

        public ClassLoaderSpecVisitor(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public void visit(ClassLoader candidate) {
            if (candidate == classLoader) {
                super.visit(candidate);
            } else {
                parents.add(candidate);
            }
        }

        @Override
        public void visitClassPath(URL[] classPath) {
            this.classPath = classPath;
        }

        @Override
        public void visitSpec(ClassLoaderSpec spec) {
            this.spec = spec;
        }
    }
}
