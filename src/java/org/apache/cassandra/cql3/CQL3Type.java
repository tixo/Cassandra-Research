/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.cql3;

import java.nio.ByteBuffer;

import org.apache.cassandra.config.Schema;
import org.apache.cassandra.db.marshal.*;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.exceptions.SyntaxException;

public interface CQL3Type
{
    public boolean isCollection();
    public boolean isCounter();
    public boolean isUserType();
    public AbstractType<?> getType();

    public enum Native implements CQL3Type
    {
        ASCII    (AsciiType.instance),
        BIGINT   (LongType.instance),
        BLOB     (BytesType.instance),
        BOOLEAN  (BooleanType.instance),
        COUNTER  (CounterColumnType.instance),
        DECIMAL  (DecimalType.instance),
        DOUBLE   (DoubleType.instance),
        FLOAT    (FloatType.instance),
        INET     (InetAddressType.instance),
        INT      (Int32Type.instance),
        TEXT     (UTF8Type.instance),
        TIMESTAMP(TimestampType.instance),
        UUID     (UUIDType.instance),
        VARCHAR  (UTF8Type.instance),
        VARINT   (IntegerType.instance), //Int32Type对应int类型，而IntegerType对应BigInteger
        TIMEUUID (TimeUUIDType.instance);

        private final AbstractType<?> type;

        private Native(AbstractType<?> type)
        {
            this.type = type;
        }

        public boolean isCollection()
        {
            return false;
        }

        public AbstractType<?> getType()
        {
            return type;
        }

        public boolean isCounter()
        {
            return this == COUNTER;
        }

        public boolean isUserType()
        {
            return false;
        }

        @Override
        public String toString()
        {
            return super.toString().toLowerCase();
        }
    }

    //可以像这样定义字段类型:
    //CREATE TABLE(f1 'org.apache.cassandra.db.marshal.UTF8Type');
    //CREATE TABLE(f1 'UTF8Type');
    //但是不能这样:
    //CREATE TABLE(f1 UTF8Type); //要加引号
    public static class Custom implements CQL3Type
    {
        private final AbstractType<?> type;

        public Custom(AbstractType<?> type)
        {
            this.type = type;
        }

        public Custom(String className) throws SyntaxException, ConfigurationException
        {
            this(TypeParser.parse(className));
        }

        public boolean isCollection()
        {
            return false;
        }

        public AbstractType<?> getType()
        {
            return type;
        }

        public boolean isCounter()
        {
            return false;
        }

        public boolean isUserType()
        {
            return false;
        }

        @Override
        public final boolean equals(Object o)
        {
            if(!(o instanceof Custom))
                return false;

            Custom that = (Custom)o;
            return type.equals(that.type);
        }

        @Override
        public final int hashCode()
        {
            return type.hashCode();
        }

        @Override
        public String toString()
        {
            return "'" + type + "'";
        }
    }

    //如: "CREATE TABLE IF NOT EXISTS test ( block_id uuid PRIMARY KEY, s set<int>, l list<int>, m map<text,int>)
    //Collection类型的元素类型不能是couter类型和Collection类型
    public static class Collection implements CQL3Type
    {
        CollectionType type;

        public Collection(CollectionType type)
        {
            this.type = type;
        }

        public static Collection map(CQL3Type t1, CQL3Type t2) throws InvalidRequestException
        {
            if (t1.isCollection() || t2.isCollection())
                throw new InvalidRequestException("map type cannot contain another collection");
            if (t1.isCounter() || t2.isCounter())
                throw new InvalidRequestException("counters are not allowed inside a collection");

            return new Collection(MapType.getInstance(t1.getType(), t2.getType()));
        }

        public static Collection list(CQL3Type t) throws InvalidRequestException
        {
            if (t.isCollection())
                throw new InvalidRequestException("list type cannot contain another collection");
            if (t.isCounter())
                throw new InvalidRequestException("counters are not allowed inside a collection");

            return new Collection(ListType.getInstance(t.getType()));
        }

        public static Collection set(CQL3Type t) throws InvalidRequestException
        {
            if (t.isCollection())
                throw new InvalidRequestException("set type cannot contain another collection");
            if (t.isCounter())
                throw new InvalidRequestException("counters are not allowed inside a collection");

            return new Collection(SetType.getInstance(t.getType()));
        }

        public boolean isCollection()
        {
            return true;
        }

        public AbstractType<?> getType()
        {
            return type;
        }

        public boolean isCounter()
        {
            return false;
        }

        public boolean isUserType()
        {
            return false;
        }

        @Override
        public final boolean equals(Object o)
        {
            if(!(o instanceof Collection))
                return false;

            Collection that = (Collection)o;
            return type.equals(that.type);
        }

        @Override
        public final int hashCode()
        {
            return type.hashCode();
        }

        @Override
        public String toString()
        {
            switch (type.kind)
            {
                case LIST:
                    return "list<" + ((ListType)type).elements.asCQL3Type() + ">";
                case SET:
                    return "set<" + ((SetType)type).elements.asCQL3Type() + ">";
                case MAP:
                    MapType mt = (MapType)type;
                    return "map<" + mt.keys.asCQL3Type() + ", " + mt.values.asCQL3Type() + ">";
            }
            throw new AssertionError();
        }
    }

    public static class UserDefined implements CQL3Type
    {
        // Keeping this separatly from type just to simplify toString()
        ColumnIdentifier name;
        UserType type;

        private UserDefined(ColumnIdentifier name, UserType type)
        {
            this.name = name;
            this.type = type;
        }

        public static UserDefined create(ByteBuffer name, UserType type)
        {
            return new UserDefined(new ColumnIdentifier(name, UTF8Type.instance), type);
        }

        public static UserDefined create(ColumnIdentifier name) throws InvalidRequestException
        {
            UserType type = Schema.instance.userTypes.getType(name);
            if (type == null)
                throw new InvalidRequestException("Unknown type " + name);

            return new UserDefined(name, type);
        }

        public boolean isUserType()
        {
            return true;
        }

        public boolean isCollection()
        {
            return false;
        }

        public boolean isCounter()
        {
            return false;
        }

        public AbstractType<?> getType()
        {
            return type;
        }

        @Override
        public final boolean equals(Object o)
        {
            if(!(o instanceof UserDefined))
                return false;

            UserDefined that = (UserDefined)o;
            return type.equals(that.type);
        }

        @Override
        public final int hashCode()
        {
            return type.hashCode();
        }

        @Override
        public String toString()
        {
            return name.toString();
        }
    }
}
