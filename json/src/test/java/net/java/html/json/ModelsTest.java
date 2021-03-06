/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package net.java.html.json;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 *
 * @author Jaroslav Tulach
 */
public class ModelsTest {
    
    public ModelsTest() {
    }

    @Test public void peopleAreModel() {
        assertTrue(Models.isModel(People.class), "People are generated class");
    }
    
    @Test public void personIsModel() {
        assertTrue(Models.isModel(Person.class), "Person is generated class");
    }

    @Test public void implClassIsNotModel() {
        assertFalse(Models.isModel(PersonImpl.class), "Impl is not model");
    }

    @Test public void randomClassIsNotModel() {
        assertFalse(Models.isModel(StringBuilder.class), "JDK classes are not model");
    }
}