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

package org.gradle.language.base.internal

import spock.lang.Specification

class DefaultBinaryNamingSchemeTest extends Specification {
    def "generates task names for native binaries"() {
        expect:
        def namingScheme = createNamingScheme(baseName, type, dimensions)
        namingScheme.getTaskName(verb, target) == taskName

        where:
        baseName | type   | dimensions     | verb       | target    | taskName
        "test"   | ""     | []             | null       | null      | "test"
        "test"   | "type" | []             | null       | null      | "testType"
        "test"   | "type" | []             | null       | "classes" | "testTypeClasses"
        "test"   | ""     | []             | null       | "classes" | "testClasses"
        "test"   | "type" | []             | "assemble" | null      | "assembleTestType"
        "test"   | "type" | []             | "compile"  | "java"    | "compileTestTypeJava"
        "test"   | "type" | ["one", "two"] | null       | null      | "oneTwoTestType"
        "test"   | "type" | ["one", "two"] | null       | "classes" | "oneTwoTestTypeClasses"
        "test"   | "type" | ["one", "two"] | "assemble" | null      | "assembleOneTwoTestType"
        "test"   | "type" | ["one", "two"] | "compile"  | "java"    | "compileOneTwoTestTypeJava"
    }

    def "generates task name with extended inputs"() {
        expect:
        def namingScheme = createNamingScheme("theBinary", "theType", ['firstDimension', 'secondDimension'])
        namingScheme.getTaskName("theVerb", "theTarget") == "theVerbFirstDimensionSecondDimensionTheBinaryTheTypeTheTarget"
    }

    def "generates base name and output directory"() {
        def namingScheme = createNamingScheme(baseName, "", dimensions)

        expect:
        namingScheme.getLifecycleTaskName() == lifecycleName
        namingScheme.getOutputDirectoryBase() == outputDir

        where:
        baseName      | dimensions                                 | lifecycleName                               | outputDir
        "test"        | []                                         | "test"                                      | "test"
        "test"        | ["one", "two"]                             | "oneTwoTest"                                | "test/oneTwo"
        "mainLibrary" | ["enterpriseEdition", "osx_x64", "static"] | "enterpriseEditionOsx_x64StaticMainLibrary" | "mainLibrary/enterpriseEditionOsx_x64Static"
        "mainLibrary" | ["EnterpriseEdition", "Osx_x64", "Static"] | "enterpriseEditionOsx_x64StaticMainLibrary" | "mainLibrary/enterpriseEditionOsx_x64Static"
    }

    private DefaultBinaryNamingScheme createNamingScheme(def baseName, def type, def dimensions) {
        def namingScheme = new DefaultBinaryNamingScheme(baseName).withTypeString(type)
        for (String dimension : dimensions) {
            namingScheme = namingScheme.withVariantDimension(dimension)
        }
        return namingScheme
    }
}
