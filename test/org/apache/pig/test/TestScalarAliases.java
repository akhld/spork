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
package org.apache.pig.test;

import java.io.IOException;
import java.util.Iterator;
import java.util.Random;

import junit.framework.TestCase;

import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.io.FileLocalizer;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.apache.pig.impl.logicalLayer.validators.TypeCheckerException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

public class TestScalarAliases extends TestCase {
    static MiniCluster cluster = MiniCluster.buildCluster();
    private PigServer pigServer;

    TupleFactory mTf = TupleFactory.getInstance();
    BagFactory mBf = BagFactory.getInstance();

    @Before
    @Override
    public void setUp() throws Exception{
        FileLocalizer.setR(new Random());
        pigServer = new PigServer(ExecType.MAPREDUCE, cluster.getProperties());
    }

    @AfterClass
    public static void oneTimeTearDown() throws Exception {
        cluster.shutDown();
    }

    // See PIG-1434
    @Test
    public void testScalarAliasesBatchNobatch() throws Exception{
        String[] input = {
                "1\t5",
                "2\t10",
                "3\t20"
        };

        // Test the use of scalars in expressions
        Util.createInputFile(cluster, "table_testScalarAliasesBatch", input);
        // Test in script mode
        pigServer.setBatchOn();
        pigServer.registerQuery("A = LOAD 'table_testScalarAliasesBatch' as (a0: long, a1: double);");
        pigServer.registerQuery("B = group A all;");
        pigServer.registerQuery("C = foreach B generate COUNT(A) as count, MAX(A.$1) as max;");
        pigServer.registerQuery("Y = foreach A generate (a0 * C.count), (a1 / C.max);");
        pigServer.registerQuery("Store Y into 'table_testScalarAliasesDir';");
        pigServer.executeBatch();
        // Check output
        pigServer.registerQuery("Z = LOAD 'table_testScalarAliasesDir' as (a0: int, a1: double);");

        Iterator<Tuple> iter;
        Tuple t;
        iter = pigServer.openIterator("Z");

        t = iter.next();
        assertTrue(t.toString().equals("(3,0.25)"));

        t = iter.next();
        assertTrue(t.toString().equals("(6,0.5)"));

        t = iter.next();
        assertTrue(t.toString().equals("(9,1.0)"));

        assertFalse(iter.hasNext());

        iter = pigServer.openIterator("Y");

        t = iter.next();
        assertTrue(t.toString().equals("(3,0.25)"));

        t = iter.next();
        assertTrue(t.toString().equals("(6,0.5)"));

        t = iter.next();
        assertTrue(t.toString().equals("(9,1.0)"));

        assertFalse(iter.hasNext());
    }

    // See PIG-1434
    @Test
    public void testUseScalarMultipleTimes() throws Exception{
        String[] input = {
                "1\t5",
                "2\t10",
                "3\t20"
        };

        // Test the use of scalars in expressions
        Util.createInputFile(cluster, "table_testUseScalarMultipleTimes", input);
        pigServer.setBatchOn();
        pigServer.registerQuery("A = LOAD 'table_testUseScalarMultipleTimes' as (a0: long, a1: double);");
        pigServer.registerQuery("B = group A all;");
        pigServer.registerQuery("C = foreach B generate COUNT(A) as count, MAX(A.$1) as max;");
        pigServer.registerQuery("Y = foreach A generate (a0 * C.count), (a1 / C.max);");
        pigServer.registerQuery("Store Y into 'table_testUseScalarMultipleTimesOutY';");
        pigServer.registerQuery("Z = foreach A generate (a1 + C.count), (a0 * C.max);");
        pigServer.registerQuery("Store Z into 'table_testUseScalarMultipleTimesOutZ';");
        // Test Multiquery store
        pigServer.executeBatch();
        
        // Check output
        pigServer.registerQuery("M = LOAD 'table_testUseScalarMultipleTimesOutY' as (a0: int, a1: double);");

        Iterator<Tuple> iter;
        Tuple t;
        iter = pigServer.openIterator("M");

        t = iter.next();
        assertTrue(t.toString().equals("(3,0.25)"));

        t = iter.next();
        assertTrue(t.toString().equals("(6,0.5)"));

        t = iter.next();
        assertTrue(t.toString().equals("(9,1.0)"));

        assertFalse(iter.hasNext());
        
        // Check output
        pigServer.registerQuery("N = LOAD 'table_testUseScalarMultipleTimesOutZ' as (a0: double, a1: double);");
        
        iter = pigServer.openIterator("N");

        t = iter.next();
        assertTrue(t.toString().equals("(8.0,20.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(13.0,40.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(23.0,60.0)"));

        assertFalse(iter.hasNext());
        
        // Non batch mode
        iter = pigServer.openIterator("Y");

        t = iter.next();
        assertTrue(t.toString().equals("(3,0.25)"));

        t = iter.next();
        assertTrue(t.toString().equals("(6,0.5)"));

        t = iter.next();
        assertTrue(t.toString().equals("(9,1.0)"));

        assertFalse(iter.hasNext());

        // Check in non-batch mode        
        iter = pigServer.openIterator("Z");

        t = iter.next();
        assertTrue(t.toString().equals("(8.0,20.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(13.0,40.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(23.0,60.0)"));

        assertFalse(iter.hasNext());
    }

    // See PIG-1434
    @Test
    public void testScalarWithNoSchema() throws Exception{
        String[] scalarInput = {
                "1\t5"
        };
        String[] input = {
                "1\t5",
                "2\t10",
                "3\t20"
        };
        Util.createInputFile(cluster, "table_testScalarWithNoSchema", input);
        Util.createInputFile(cluster, "table_testScalarWithNoSchemaScalar", scalarInput);
        // Load A as a scalar
        pigServer.registerQuery("A = LOAD 'table_testScalarWithNoSchema';");
        pigServer.registerQuery("scalar = LOAD 'table_testScalarWithNoSchemaScalar' as (count, total);");
        pigServer.registerQuery("B = foreach A generate 5 / scalar.total;");

        try {
            pigServer.openIterator("B");
            fail("We do not support no schema scalar without a cast");
        } catch (FrontendException te) {
            // In alias B, incompatible types in Division Operator left hand side:int right hand side:chararray
            assertTrue(((TypeCheckerException)te.getCause().getCause().getCause()).getErrorCode() == 1039);
        }

        pigServer.registerQuery("C = foreach A generate 5 / (int)scalar.total;");

        Iterator<Tuple> iter = pigServer.openIterator("C");

        Tuple t = iter.next();
        assertTrue(t.get(0).toString().equals("1"));

        t = iter.next();
        assertTrue(t.get(0).toString().equals("1"));

        t = iter.next();
        assertTrue(t.get(0).toString().equals("1"));

        assertFalse(iter.hasNext());

    }

    // See PIG-1434
    @Test
    public void testScalarWithTwoBranches() throws Exception{
        String[] inputA = {
                "1\t5",
                "2\t10",
                "3\t20"
        };

        String[] inputX = {
                "pig",
                "hadoop",
                "rocks"
        };

        // Test the use of scalars in expressions
        Util.createInputFile(cluster, "testScalarWithTwoBranchesA", inputA);
        Util.createInputFile(cluster, "testScalarWithTwoBranchesX", inputX);
        // Test in script mode
        pigServer.setBatchOn();
        pigServer.registerQuery("A = LOAD 'testScalarWithTwoBranchesA' as (a0: long, a1: double);");
        pigServer.registerQuery("B = group A all;");
        pigServer.registerQuery("C = foreach B generate COUNT(A) as count, MAX(A.$1) as max;");
        pigServer.registerQuery("X = LOAD 'testScalarWithTwoBranchesX' as (names: chararray);");
        pigServer.registerQuery("Y = foreach X generate names, C.max;");
        pigServer.registerQuery("Store Y into 'testScalarWithTwoBranchesDir';");
        pigServer.executeBatch();
        // Check output
        pigServer.registerQuery("Z = LOAD 'testScalarWithTwoBranchesDir' as (a0: chararray, a1: double);");

        Iterator<Tuple> iter = pigServer.openIterator("Z");

        Tuple t = iter.next();
        assertTrue(t.toString().equals("(pig,20.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(hadoop,20.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(rocks,20.0)"));

        assertFalse(iter.hasNext());

        // Check in non-batch mode        
        iter = pigServer.openIterator("Y");

        t = iter.next();
        assertTrue(t.toString().equals("(pig,20.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(hadoop,20.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(rocks,20.0)"));

        assertFalse(iter.hasNext());
    }

    // See PIG-1434
    @Test
    public void testFilteredScalarDollarProj() throws Exception{
        String[] input = {
                "1\t5",
                "2\t10",
                "3\t20"
        };

        // Test the use of scalars in expressions
        Util.createInputFile(cluster, "table_testFilteredScalarDollarProj", input);
        // Test in script mode
        pigServer.setBatchOn();
        pigServer.registerQuery("A = LOAD 'table_testFilteredScalarDollarProj' as (a0: long, a1: double);");
        pigServer.registerQuery("B = filter A by $1 < 8;");
        pigServer.registerQuery("Y = foreach A generate (a0 * B.$0), (a1 / B.$1);");
        pigServer.registerQuery("Store Y into 'table_testFilteredScalarDollarProjDir';");
        pigServer.executeBatch();
        // Check output
        pigServer.registerQuery("Z = LOAD 'table_testFilteredScalarDollarProjDir' as (a0: int, a1: double);");

        Iterator<Tuple> iter = pigServer.openIterator("Z");

        Tuple t = iter.next();
        assertTrue(t.toString().equals("(1,1.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(2,2.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(3,4.0)"));

        assertFalse(iter.hasNext());

        // Check in non-batch mode        
        iter = pigServer.openIterator("Y");

        t = iter.next();
        assertTrue(t.toString().equals("(1,1.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(2,2.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(3,4.0)"));

        assertFalse(iter.hasNext());

    }

    // See PIG-1434
    @Test
    public void testScalarWithNoSchemaDollarProj() throws Exception{
        String[] scalarInput = {
                "1\t5"
        };
        String[] input = {
                "1\t5",
                "2\t10",
                "3\t20"
        };
        Util.createInputFile(cluster, "table_testScalarWithNoSchemaDollarProj", input);
        Util.createInputFile(cluster, "table_testScalarWithNoSchemaDollarProjScalar", scalarInput);
        // Load A as a scalar
        pigServer.registerQuery("A = LOAD 'table_testScalarWithNoSchemaDollarProj';");
        pigServer.registerQuery("scalar = LOAD 'table_testScalarWithNoSchemaDollarProjScalar';");
        pigServer.registerQuery("B = foreach A generate 5 / scalar.$1;");

        try {
            pigServer.openIterator("B");
            fail("We do not support no schema scalar without a cast");
        } catch (FrontendException te) {
            // In alias B, incompatible types in Division Operator left hand side:int right hand side:chararray
            assertTrue(((TypeCheckerException)te.getCause().getCause().getCause()).getErrorCode() == 1039);
        }

        pigServer.registerQuery("C = foreach A generate 5 / (int)scalar.$1;");

        Iterator<Tuple> iter = pigServer.openIterator("C");

        Tuple t = iter.next();
        assertTrue(t.get(0).toString().equals("1"));

        t = iter.next();
        assertTrue(t.get(0).toString().equals("1"));

        t = iter.next();
        assertTrue(t.get(0).toString().equals("1"));

        assertFalse(iter.hasNext());

    }

    // See PIG-1434
    @Test
    public void testScalarAliasesJoinClause() throws Exception{
        String[] inputA = {
                "1\t5",
                "2\t10",
                "3\t20"
        };
        String[] inputB = {
                "Total3\tthree",
                "Total2\ttwo",
                "Total1\tone"
        };

        // Test the use of scalars in expressions
        Util.createInputFile(cluster, "table_testScalarAliasesJoinClauseA", inputA);
        Util.createInputFile(cluster, "table_testScalarAliasesJoinClauseB", inputB);
        // Test in script mode
        pigServer.registerQuery("A = LOAD 'table_testScalarAliasesJoinClauseA' as (a0, a1);");
        pigServer.registerQuery("G = group A all;");
        pigServer.registerQuery("C = foreach G generate COUNT(A) as count;");

        pigServer.registerQuery("B = LOAD 'table_testScalarAliasesJoinClauseB' as (b0:chararray, b1:chararray);");
        pigServer.registerQuery("Y = join A by CONCAT('Total', (chararray)C.count), B by $0;");

        Iterator<Tuple> iter = pigServer.openIterator("Y");

        Tuple t = iter.next();
        assertTrue(t.toString().equals("(1,5,Total3,three)"));

        t = iter.next();
        assertTrue(t.toString().equals("(2,10,Total3,three)"));

        t = iter.next();
        assertTrue(t.toString().equals("(3,20,Total3,three)"));

        assertFalse(iter.hasNext());
    }

    // See PIG-1434
    @Test
    public void testScalarAliasesFilterClause() throws Exception{
        String[] input = {
                "1\t5",
                "2\t10",
                "3\t20",
                "4\t12",
                "5\t8"
        };

        // Test the use of scalars in expressions
        Util.createInputFile(cluster, "table_testScalarAliasesFilterClause", input);
        // Test in script mode
        pigServer.registerQuery("A = LOAD 'table_testScalarAliasesFilterClause' as (a0, a1);");
        pigServer.registerQuery("G = group A all;");
        pigServer.registerQuery("C = foreach G generate AVG(A.$1) as average;");

        pigServer.registerQuery("Y = filter A by a1 > C.average;");

        Iterator<Tuple> iter = pigServer.openIterator("Y");

        // Average is 11
        Tuple t = iter.next();
        assertTrue(t.toString().equals("(3,20)"));

        t = iter.next();
        assertTrue(t.toString().equals("(4,12)"));

        assertFalse(iter.hasNext());
    }

    // See PIG-1434
    @Test
    public void testScalarAliasesSplitClause() throws Exception{
        String[] input = {
                "1\t5",
                "2\t10",
                "3\t20"
        };

        // Test the use of scalars in expressions
        Util.createInputFile(cluster, "table_testScalarAliasesSplitClause", input);
        // Test in script mode
        pigServer.setBatchOn();
        pigServer.registerQuery("A = LOAD 'table_testScalarAliasesSplitClause' as (a0: long, a1: double);");
        pigServer.registerQuery("B = group A all;");
        pigServer.registerQuery("C = foreach B generate COUNT(A) as count;");
        pigServer.registerQuery("split A into Y if (2 * C.count) < a1, X if a1 == 5;");
        pigServer.registerQuery("Store Y into 'table_testScalarAliasesSplitClauseDir';");
        pigServer.executeBatch();
        // Check output
        pigServer.registerQuery("Z = LOAD 'table_testScalarAliasesSplitClauseDir' as (a0: int, a1: double);");

        Iterator<Tuple> iter = pigServer.openIterator("Z");

        // Y gets only last 2 elements
        Tuple t = iter.next();
        assertTrue(t.toString().equals("(2,10.0)"));

        t = iter.next();
        assertTrue(t.toString().equals("(3,20.0)"));

        assertFalse(iter.hasNext());
    }

    // See PIG-1434
    @Test
    public void testScalarAliasesGrammarNegative() throws Exception{
        String[] input = {
                "1\t5",
                "2\t10",
                "3\t20"
        };

        Util.createInputFile(cluster, "table_testScalarAliasesGrammar", input);
        pigServer.registerQuery("A = LOAD 'table_testScalarAliasesGrammar' as (a0: long, a1: double);");
        pigServer.registerQuery("B = group A all;");
        pigServer.registerQuery("C = foreach B generate COUNT(A);");

        try {
            // Only projections of C are supported 
            pigServer.registerQuery("Y = foreach A generate C;");
            //Control should not reach here
            fail("Scalar projections are only supported");
        } catch (IOException pe){
            assertTrue(pe.getCause().getMessage().equalsIgnoreCase("Scalars can be only used with projections"));
        }
    }
}