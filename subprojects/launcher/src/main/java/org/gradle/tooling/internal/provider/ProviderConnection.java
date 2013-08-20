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

import org.gradle.StartParameter;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.BuildAction;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.Factory;
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter;
import org.gradle.launcher.cli.converter.PropertiesToDaemonParametersConverter;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.DaemonClientServices;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.OutputEventRenderer;
import org.gradle.process.internal.streams.SafeStreams;
import org.gradle.tooling.internal.build.DefaultBuildEnvironment;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.provider.connection.ProviderConnectionParameters;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProviderConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderConnection.class);
    private final PayloadSerializer payloadSerializer;
    private final ActionClasspathFactory actionClasspathFactory;
    private final LoggingServiceRegistry loggingServices;
    private final GradleLauncherFactory gradleLauncherFactory;

    public ProviderConnection(LoggingServiceRegistry loggingServices, GradleLauncherFactory gradleLauncherFactory, PayloadSerializer payloadSerializer, ActionClasspathFactory actionClasspathFactory) {
        this.loggingServices = loggingServices;
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.payloadSerializer = payloadSerializer;
        this.actionClasspathFactory = actionClasspathFactory;
    }

    public void configure(ProviderConnectionParameters parameters) {
        LogLevel providerLogLevel = parameters.getVerboseLogging() ? LogLevel.DEBUG : LogLevel.INFO;
        LOGGER.debug("Configuring logging to level: {}", providerLogLevel);
        LoggingManagerInternal loggingManager = loggingServices.newInstance(LoggingManagerInternal.class);
        loggingManager.setLevel(providerLogLevel);
        loggingManager.start();
    }

    public Object run(String modelName, ProviderOperationParameters providerParameters) {
        List<String> tasks = providerParameters.getTasks();
        if (modelName.equals(ModelIdentifier.NULL_MODEL) && tasks == null) {
            throw new IllegalArgumentException("No model type or tasks specified.");
        }
        Parameters params = initParams(providerParameters);
        Class<?> type = new ModelMapping().getProtocolTypeFromModelName(modelName);
        if (type == InternalBuildEnvironment.class) {
            //we don't really need to launch the daemon to acquire information needed for BuildEnvironment
            if (tasks != null) {
                throw new IllegalArgumentException("Cannot run tasks and fetch the build environment model.");
            }
            return new DefaultBuildEnvironment(
                    GradleVersion.current().getVersion(),
                    params.daemonParams.getEffectiveJavaHome(),
                    params.daemonParams.getEffectiveJvmArgs());
        }

        BuildAction<SerializedPayload> action = new BuildModelAction(modelName, tasks != null);

        // TODO:ADAM - need to clean up error handling here

        SerializedPayload model;
        try {
            model = run(action, providerParameters, params.properties);
        } catch (RuntimeException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                // TODO:ADAM - This is not right, it's just to get an initial implementation going
                if (t instanceof UnsupportedOperationException) {
                    throw new InternalUnsupportedModelException();
                }
            }
            throw e;
        }

        return payloadSerializer.deserialize(model);
    }

    public Object run(InternalBuildAction<?> clientAction, final BuildActionSerializationDetails serializationDetails, ProviderOperationParameters providerParameters) {
        List<URL> classPath = actionClasspathFactory.getClassPathForAction(serializationDetails.getActionClasses());
        final PayloadSerializer.ClassLoaderDetails actionClassLoader = new PayloadSerializer.ClassLoaderDetails(UUID.randomUUID(), classPath);
        SerializedPayload serializedAction = payloadSerializer.serialize(clientAction, new PayloadSerializer.SerializeMap() {
            public PayloadSerializer.ClassLoaderDetails getDetails(ClassLoader target) {
                return actionClassLoader;
            }
        });

        BuildAction<SerializedPayload> action = new ClientProvidedBuildAction(serializedAction);
        Parameters params = initParams(providerParameters);
        SerializedPayload result = run(action, providerParameters, params.properties);

        return payloadSerializer.deserialize(result, new PayloadSerializer.DeserializeMap() {
            public ClassLoader getClassLoader(PayloadSerializer.ClassLoaderDetails classLoaderDetails) {
                if (classLoaderDetails.uuid.equals(actionClassLoader.uuid)) {
                    return serializationDetails.getResultClassLoader();
                }
                return null;
            }
        });
    }

    private <T> T run(BuildAction<T> action, ProviderOperationParameters operationParameters, Map<String, String> properties) {
        BuildActionExecuter<ProviderOperationParameters> executer = createExecuter(operationParameters);
        ConfiguringBuildAction<T> configuringAction = new ConfiguringBuildAction<T>(operationParameters, action, properties);
        return executer.execute(configuringAction, operationParameters);
    }

    private BuildActionExecuter<ProviderOperationParameters> createExecuter(ProviderOperationParameters operationParameters) {
        LoggingServiceRegistry loggingServices;
        Parameters params = initParams(operationParameters);
        BuildActionExecuter<BuildActionParameters> executer;
        if (Boolean.TRUE.equals(operationParameters.isEmbedded())) {
            loggingServices = this.loggingServices;
            executer = new InProcessBuildActionExecuter(gradleLauncherFactory);
        } else {
            loggingServices = this.loggingServices.newLogging();
            loggingServices.get(OutputEventRenderer.class).configure(operationParameters.getBuildLogLevel());
            DaemonClientServices clientServices = new DaemonClientServices(loggingServices, params.daemonParams, operationParameters.getStandardInput(SafeStreams.emptyInput()));
            executer = clientServices.get(DaemonClient.class);
        }
        Factory<LoggingManagerInternal> loggingManagerFactory = loggingServices.getFactory(LoggingManagerInternal.class);
        return new LoggingBridgingBuildActionExecuter(new DaemonBuildActionExecuter(executer, params.daemonParams), loggingManagerFactory);
    }

    private Parameters initParams(ProviderOperationParameters operationParameters) {
        BuildLayoutParameters layout = new BuildLayoutParameters()
                .setGradleUserHomeDir(GUtil.elvis(operationParameters.getGradleUserHomeDir(), StartParameter.DEFAULT_GRADLE_USER_HOME))
                .setSearchUpwards(operationParameters.isSearchUpwards() != null ? operationParameters.isSearchUpwards() : true)
                .setProjectDir(operationParameters.getProjectDir());

        Map<String, String> properties = new HashMap<String, String>();
        new LayoutToPropertiesConverter().convert(layout, properties);

        DaemonParameters daemonParams = new DaemonParameters(layout);
        new PropertiesToDaemonParametersConverter().convert(properties, daemonParams);

        //override the params with the explicit settings provided by the tooling api
        List<String> defaultJvmArgs = daemonParams.getAllJvmArgs();
        daemonParams.setJvmArgs(operationParameters.getJvmArguments(defaultJvmArgs));
        File defaultJavaHome = daemonParams.getEffectiveJavaHome();
        daemonParams.setJavaHome(operationParameters.getJavaHome(defaultJavaHome));

        if (operationParameters.getDaemonMaxIdleTimeValue() != null && operationParameters.getDaemonMaxIdleTimeUnits() != null) {
            int idleTimeout = (int) operationParameters.getDaemonMaxIdleTimeUnits().toMillis(operationParameters.getDaemonMaxIdleTimeValue());
            daemonParams.setIdleTimeout(idleTimeout);
        }
        return new Parameters(daemonParams, properties);
    }

    private static class Parameters {
        DaemonParameters daemonParams;
        Map<String, String> properties;

        public Parameters(DaemonParameters daemonParams, Map<String, String> properties) {
            this.daemonParams = daemonParams;
            this.properties = properties;
        }
    }

}