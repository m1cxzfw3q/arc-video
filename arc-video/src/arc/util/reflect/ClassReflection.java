/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package arc.util.reflect;

/** Utilities for Class reflection.
 * @author nexsoftware */
public final class ClassReflection {
	/** Returns the Class object associated with the class or interface with the supplied string name. */
	static public Class forName (String name) throws ReflectionException {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new ReflectionException("Class not found: " + name, e);
		}
	}

	/** Creates a new instance of the class represented by the supplied Class. */
	static public <T> T newInstance (Class<T> c) throws ReflectionException {
		try {
			return c.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new ReflectionException("Could not instantiate instance of class: " + c.getName(), e);
		}
    }
}
