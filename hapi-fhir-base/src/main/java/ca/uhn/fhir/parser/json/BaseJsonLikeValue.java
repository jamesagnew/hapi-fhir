/*
 * #%L
 * HAPI FHIR - Core Library
 * %%
 * Copyright (C) 2014 - 2025 Smile CDR, Inc.
 * %%
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
 * #L%
 */
package ca.uhn.fhir.parser.json;

/**
 * This is the generalization of anything that is a "value"
 * element in a JSON structure. This could be a JSON object,
 * a JSON array, a scalar value (number, string, boolean),
 * or a null.
 *
 */
public abstract class BaseJsonLikeValue {

	public enum ValueType {
		ARRAY,
		OBJECT,
		SCALAR,
		NULL
	};

	public enum ScalarType {
		NUMBER,
		STRING,
		BOOLEAN
	}

	public abstract ValueType getJsonType();

	public abstract ScalarType getDataType();

	public abstract Object getValue();

	public boolean isArray() {
		return this.getJsonType() == ValueType.ARRAY;
	}

	public boolean isObject() {
		return this.getJsonType() == ValueType.OBJECT;
	}

	public boolean isScalar() {
		return this.getJsonType() == ValueType.SCALAR;
	}

	public boolean isString() {
		return this.getJsonType() == ValueType.SCALAR && this.getDataType() == ScalarType.STRING;
	}

	public boolean isNumber() {
		return this.getJsonType() == ValueType.SCALAR && this.getDataType() == ScalarType.NUMBER;
	}

	public boolean isNull() {
		return this.getJsonType() == ValueType.NULL;
	}

	public BaseJsonLikeArray getAsArray() {
		return null;
	}

	public BaseJsonLikeObject getAsObject() {
		return null;
	}

	public String getAsString() {
		return this.toString();
	}

	public Number getAsNumber() {
		return this.isNumber() ? (Number) this.getValue() : null;
	}

	public boolean getAsBoolean() {
		return !isNull();
	}

	public static BaseJsonLikeArray asArray(BaseJsonLikeValue element) {
		if (element != null) {
			return element.getAsArray();
		}
		return null;
	}

	public static BaseJsonLikeObject asObject(BaseJsonLikeValue element) {
		if (element != null) {
			return element.getAsObject();
		}
		return null;
	}

	public static String asString(BaseJsonLikeValue element) {
		if (element != null) {
			return element.getAsString();
		}
		return null;
	}

	public static boolean asBoolean(BaseJsonLikeValue element) {
		if (element != null) {
			return element.getAsBoolean();
		}
		return false;
	}

	public static final BaseJsonLikeValue NULL = new BaseJsonLikeValue() {
		@Override
		public ValueType getJsonType() {
			return ValueType.NULL;
		}

		@Override
		public ScalarType getDataType() {
			return null;
		}

		@Override
		public Object getValue() {
			return null;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj instanceof BaseJsonLikeValue) {
				return getJsonType().equals(((BaseJsonLikeValue) obj).getJsonType());
			}
			return false;
		}

		@Override
		public int hashCode() {
			return "null".hashCode();
		}

		@Override
		public String toString() {
			return "null";
		}
	};

	public static final BaseJsonLikeValue TRUE = new BaseJsonLikeValue() {
		@Override
		public ValueType getJsonType() {
			return ValueType.SCALAR;
		}

		@Override
		public ScalarType getDataType() {
			return ScalarType.BOOLEAN;
		}

		@Override
		public Object getValue() {
			return Boolean.TRUE;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj instanceof BaseJsonLikeValue) {
				return getJsonType().equals(((BaseJsonLikeValue) obj).getJsonType())
						&& getDataType().equals(((BaseJsonLikeValue) obj).getDataType())
						&& toString().equals(((BaseJsonLikeValue) obj).toString());
			}
			return false;
		}

		@Override
		public int hashCode() {
			return "true".hashCode();
		}

		@Override
		public String toString() {
			return "true";
		}
	};

	public static final BaseJsonLikeValue FALSE = new BaseJsonLikeValue() {
		@Override
		public ValueType getJsonType() {
			return ValueType.SCALAR;
		}

		@Override
		public ScalarType getDataType() {
			return ScalarType.BOOLEAN;
		}

		@Override
		public Object getValue() {
			return Boolean.FALSE;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj instanceof BaseJsonLikeValue) {
				return getJsonType().equals(((BaseJsonLikeValue) obj).getJsonType())
						&& getDataType().equals(((BaseJsonLikeValue) obj).getDataType())
						&& toString().equals(((BaseJsonLikeValue) obj).toString());
			}
			return false;
		}

		@Override
		public int hashCode() {
			return "false".hashCode();
		}

		@Override
		public String toString() {
			return "false";
		}
	};
}
