/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.nativecode.language.cpp

import org.gradle.nativecode.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class CppBinariesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "can configure the binaries of a C++ application"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            executables {
                main {
                    binaries.all {
                        outputFile file('${executable("build/test").toURI()}')
                        define 'ENABLE_GREETING'
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/main/cpp/helloworld.cpp") << """
            #include <iostream>

            int main () {
              #ifdef ENABLE_GREETING
              std::cout << "Hello!";
              #endif
              return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/test")
        executable.exec().out == "Hello!"
    }

    def "can build debug binaries for a C++ executable"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            executables {
                main {
                    binaries.all {
                        if (toolChain == toolChains.visualCpp) {
                            compilerArgs '/Zi'
                            linkerArgs '/DEBUG'
                        } else {
                            compilerArgs '-g'
                        }
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/main/cpp/helloworld.cpp") << """
            #include <iostream>

            int main () {
              std::cout << "Hello!";
              return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/binaries/mainExecutable/test")
        executable.exec().out == "Hello!"
        executable.assertDebugFileExists()
        // TODO - need to verify that the debug info ended up in the binary
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "can configure the binaries of a C++ library"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"

            sources {
                hello {}
            }
            libraries {
                hello {
                    source sources.hello.cpp
                    binaries.all {
                        outputFile file('${staticLibrary("build/hello").toURI()}')
                        define 'ENABLE_GREETING'
                    }
                }
            }
            executables {
                main {
                    binaries.all {
                        lib libraries.hello.static
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/hello/cpp/hello.cpp") << """
            #include <iostream>

            void hello(const char* str) {
              #ifdef ENABLE_GREETING
              std::cout << str;
              #endif
            }
        """

        and:
        file("src/hello/headers/hello.h") << """
            void hello(const char* str);
        """

        and:
        file("src/main/cpp/main.cpp") << """
            #include "hello.h"

            int main () {
              hello("Hello!");
              return 0;
            }
        """

        when:
        run "installMainExecutable"

        then:
        staticLibrary("build/hello").assertExists()
        executable("build/install/mainExecutable/test").exec().out == "Hello!"
    }

    def "can configure a binary to use additional source sets"() {
        given:
        buildFile << """
            apply plugin: "cpp"

            sources {
                main {
                    cpp {
                        exportedHeaders.srcDir "src/shared/headers"
                    }
                }
                util {
                    cpp {
                        exportedHeaders.srcDir "src/shared/headers"
                    }
                }
            }
            executables {
                main {
                    source sources.main.cpp
                    binaries.all {
                        source sources.util.cpp
                    }
                }
            }
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/shared/headers/greeting.h") << """
            void greeting();
"""

        file("src/util/cpp/greeting.cpp") << """
            #include <iostream>
            #include "greeting.h"

            void greeting() {
                std::cout << "Hello!";
            }
        """

        file("src/main/cpp/helloworld.cpp") << """
            #include "greeting.h"

            int main() {
                greeting();
                return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/binaries/mainExecutable/main")
        executable.exec().out == "Hello!"
    }
}
