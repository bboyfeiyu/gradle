/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Issue

class MavenParentPomResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def "includes dependencies from parent pom"() {
        given:
        server.start()

        def parentDep = mavenHttpRepo.module("org", "parent_dep", "1.2").publish()
        def childDep = mavenHttpRepo.module("org", "child_dep", "1.7").publish()

        def parent = mavenHttpRepo.module("org", "parent", "1.0")
        parent.hasPackaging('pom')
        parent.dependsOn("org", "parent_dep", "1.2")
        parent.publish()

        def child = mavenHttpRepo.module("org", "child", "1.0")
        child.dependsOn("org", "child_dep", "1.7")
        child.parent("org", "parent", "1.0")
        child.publish()

        buildFile << """
repositories {
    maven { url '${mavenHttpRepo.uri}' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        child.pom.expectGet()
        parent.pom.expectGet()

        // Will always check for a default artifact with a module with 'pom' packaging
        // TODO - should not make this request
        parent.artifact.expectHeadMissing()

        child.artifact.expectGet()

        parentDep.pom.expectGet()
        parentDep.artifact.expectGet()
        childDep.pom.expectGet()
        childDep.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar', 'parent_dep-1.2.jar', 'child_dep-1.7.jar')

        when:
        server.resetExpectations()
        file('libs').deleteDir()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar', 'parent_dep-1.2.jar', 'child_dep-1.7.jar')
    }

    @Issue("GRADLE-2641")
    def "can handle parent pom with SNAPSHOT version"() {
        given:
        server.start()

        def parent = mavenHttpRepo.module("org", "parent", "1.0-SNAPSHOT")
        parent.hasPackaging('pom')
        parent.publish()

        def child = mavenHttpRepo.module("org", "child", "1.0")
        child.parent("org", "parent", "1.0-SNAPSHOT")
        child.publish()

        buildFile << """
repositories {
    maven { url '${mavenHttpRepo.uri}' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        child.pom.expectGet()
        parent.metaData.expectGet()
        parent.pom.expectGet()

        // Will always check for a default artifact with a module with 'pom' packaging
        // TODO - should not make this request
        parent.artifact.expectHeadMissing()

        child.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar')
    }

    def "looks for parent pom in different repository"() {
        given:
        server.start()
        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")

        def parentInRepo1 = repo1.module("org", "parent")

        def parentInRepo2 = repo2.module("org", "parent")
        parentInRepo2.hasPackaging('pom')
        parentInRepo2.publish()

        def child = repo1.module("org", "child")
        child.parent("org", "parent", "1.0")
        child.publish()

        buildFile << """
repositories {
    maven { url '${repo1.uri}' }
    maven { url '${repo2.uri}' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        child.pom.expectGet()
        child.artifact.expectGet()

        parentInRepo1.pom.expectGetMissing()
        parentInRepo1.artifact.expectHeadMissing()

        parentInRepo2.pom.expectGet()
         // TODO - should not make this request
        parentInRepo2.artifact.expectHeadMissing()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar')
    }

    def "uses cached parent pom located in a different repository"() {
        given:
        server.start()

        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")

        // Parent not found in repo1
        def missingParent = repo1.module("org", "parent")
        def parent = repo2.module("org", "parent", "1.0")
        parent.dependsOn("org", "parent_dep", "1.2")
                .hasPackaging('pom')
                .publish()

        def parentDep = repo1.module("org", "parent_dep", "1.2").publish()

        def child1 = repo1.module("org", "child1", "1.0")
        child1.parent("org", "parent", "1.0").publish()
        def child2 = repo1.module("org", "child2", "1.0")
        child2.parent("org", "parent", "1.0").publish()

        buildFile << """
repositories {
    maven { url '${repo1.uri}' }
    maven { url '${repo2.uri}' }
}
configurations {
    child1
    child2
}
dependencies {
    child1 'org:child1:1.0'
    child2 'org:child2:1.0'
}
task retrieveChild1(type: Sync) {
    into 'libs/child1'
    from configurations.child1
}
task retrieveChild2(type: Sync) {
    into 'libs/child2'
    from configurations.child2
}
"""

        when:
        child1.pom.expectGet()
        missingParent.pom.expectGetMissing()
        missingParent.artifact.expectHeadMissing()
        parent.pom.expectGet()
        parent.artifact.expectHeadMissing()

        child1.artifact.expectGet()

        parentDep.pom.expectGet()
        parentDep.artifact.expectGet()


        and:
        run 'retrieveChild1'

        then:
        file('libs/child1').assertHasDescendants('child1-1.0.jar', 'parent_dep-1.2.jar')

        when:
        server.resetExpectations()
        child2.pom.expectGet()
        child2.artifact.expectGet()

        and:
        run 'retrieveChild2'

        then:
        file('libs/child2').assertHasDescendants('child2-1.0.jar', 'parent_dep-1.2.jar')
    }
}