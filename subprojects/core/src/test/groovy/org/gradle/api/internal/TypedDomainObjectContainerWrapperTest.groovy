/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

class TypedDomainObjectContainerWrapperTest extends Specification {

    DefaultPolymorphicDomainObjectContainer<Type> parent = new DefaultPolymorphicDomainObjectContainer<Type>(Type, new DirectInstantiator())

    def setup() {
        parent.add(type("typeOne"))
        parent.add(type("typeTwo"))
        parent.add(subtype("subTypeOne"))
        parent.add(subtype("subTypeTwo"))
        parent.add(otherSubtype("otherSubType"))

        parent.registerFactory(CreatedSubType, { String name ->
            return new DefaultCreatedSubType(name)
        })
    }

    def "test returns subtype elements"() {
        when:
        def container = parent.containerWithType(SubType)

        then:
        containerHas container, "subTypeOne", "subTypeTwo"
    }

    def "returns all elements when filtered by parent type"() {
        when:
        def container = parent.containerWithType(Type)

        then:
        containerHas container, "otherSubType", "subTypeOne", "subTypeTwo", "typeOne", "typeTwo"
    }

    def "created elements are typed"() {
        when:
        def container = parent.containerWithType(CreatedSubType)
        def action = { CreatedSubType subType ->
            subType.value = "actioned"
        } as Action<CreatedSubType>

        and:
        container.create("created1")
        container.create("created2", {
            it.value = "changed"
        })
        container.create("created3", action)

        then:
        containerHas container, "created1", "created2", "created3"
        parent.collect({it.name}).containsAll(["created1", "created2", "created3"])

        and:
        container.getByName("created1").value == "original"
        container.getByName("created2").value == "changed"
        container.getByName("created3").value == "actioned"
    }

    def "can create and configure via DSL"() {
        when:
        def container = parent.containerWithType(CreatedSubType)

        and:
        container.configure {
            createdOne
            createdTwo {
                value = "changed"
            }
        }

        then:
        containerHas container, "createdOne", "createdTwo"
        parent.collect({it.name}).containsAll(["createdOne", "createdTwo"])

        and:
        container.getByName("createdOne").value == "original"
        container.getByName("createdTwo").value == "changed"
    }

    def "can configure existing via DSL"() {
        given:
        def container = parent.containerWithType(CreatedSubType)
        def created = container.create("createdOne")
        assert container.asList() == [created]

        when:
        container.configure {
            createdOne {
                value = "changed"
            }
        }

        then:
        container.asList() == [created]
        created.value == "changed"
    }

    def containerHas(def container, String... names) {
        assert container.toList().collect {it.name} == names as List
        true
    }

    def Type type(def name) {
        Stub(Type) {
            getName() >> name
        }
    }

    def SubType subtype(def name) {
        Stub(SubType) {
            getName() >> name
        }
    }

    def OtherSubType otherSubtype(def name) {
        Stub(OtherSubType) {
            getName() >> name
        }
    }
    interface Type extends Named {}
    interface SubType extends Type {}
    interface OtherSubType extends Type {}
    interface CreatedSubType extends Type {
        String getValue()
        void setValue(String value)
    }

    class DefaultCreatedSubType implements CreatedSubType {
        final String name
        String value = "original";

        DefaultCreatedSubType(String name) {
            this.name = name
        }
    }
}
