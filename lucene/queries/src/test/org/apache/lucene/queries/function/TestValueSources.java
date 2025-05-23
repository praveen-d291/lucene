/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.queries.function;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.docvalues.FloatDocValues;
import org.apache.lucene.queries.function.valuesource.BytesRefFieldSource;
import org.apache.lucene.queries.function.valuesource.ConstValueSource;
import org.apache.lucene.queries.function.valuesource.DivFloatFunction;
import org.apache.lucene.queries.function.valuesource.DocFreqValueSource;
import org.apache.lucene.queries.function.valuesource.DoubleConstValueSource;
import org.apache.lucene.queries.function.valuesource.DoubleFieldSource;
import org.apache.lucene.queries.function.valuesource.FloatFieldSource;
import org.apache.lucene.queries.function.valuesource.IDFValueSource;
import org.apache.lucene.queries.function.valuesource.IfFunction;
import org.apache.lucene.queries.function.valuesource.IntFieldSource;
import org.apache.lucene.queries.function.valuesource.JoinDocFreqValueSource;
import org.apache.lucene.queries.function.valuesource.LinearFloatFunction;
import org.apache.lucene.queries.function.valuesource.LiteralValueSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.queries.function.valuesource.MaxDocValueSource;
import org.apache.lucene.queries.function.valuesource.MaxFloatFunction;
import org.apache.lucene.queries.function.valuesource.MinFloatFunction;
import org.apache.lucene.queries.function.valuesource.MultiBoolFunction;
import org.apache.lucene.queries.function.valuesource.MultiFloatFunction;
import org.apache.lucene.queries.function.valuesource.MultiFunction;
import org.apache.lucene.queries.function.valuesource.MultiValuedDoubleFieldSource;
import org.apache.lucene.queries.function.valuesource.MultiValuedFloatFieldSource;
import org.apache.lucene.queries.function.valuesource.MultiValuedIntFieldSource;
import org.apache.lucene.queries.function.valuesource.MultiValuedLongFieldSource;
import org.apache.lucene.queries.function.valuesource.NormValueSource;
import org.apache.lucene.queries.function.valuesource.NumDocsValueSource;
import org.apache.lucene.queries.function.valuesource.PowFloatFunction;
import org.apache.lucene.queries.function.valuesource.ProductFloatFunction;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.queries.function.valuesource.RangeMapFloatFunction;
import org.apache.lucene.queries.function.valuesource.ReciprocalFloatFunction;
import org.apache.lucene.queries.function.valuesource.ScaleFloatFunction;
import org.apache.lucene.queries.function.valuesource.SumFloatFunction;
import org.apache.lucene.queries.function.valuesource.SumTotalTermFreqValueSource;
import org.apache.lucene.queries.function.valuesource.TFValueSource;
import org.apache.lucene.queries.function.valuesource.TermFreqValueSource;
import org.apache.lucene.queries.function.valuesource.TotalTermFreqValueSource;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.FilterScorer;
import org.apache.lucene.search.FilterWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSelector.Type;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;

// TODO: add separate docvalues test
/** barebones tests for function queries. */
public class TestValueSources extends LuceneTestCase {
  static Directory dir;
  static Analyzer analyzer;
  static IndexReader reader;
  static IndexSearcher searcher;

  static final ValueSource BOGUS_FLOAT_VS = new FloatFieldSource("bogus_field");
  static final ValueSource BOGUS_DOUBLE_VS = new DoubleFieldSource("bogus_field");
  static final ValueSource BOGUS_INT_VS = new IntFieldSource("bogus_field");
  static final ValueSource BOGUS_LONG_VS = new LongFieldSource("bogus_field");

  static final List<String[]> documents =
      Arrays.asList(
          new String[][] {
            /*             id,  double, float, int,  long,   string, text,                       double MV (x3),             int MV (x3)*/
            new String[] {
              "0",
              "3.63",
              "5.2",
              "35",
              "4343",
              "test",
              "this is a test test test",
              "2.13",
              "3.69",
              "0.11",
              "1",
              "7",
              "5"
            },
            new String[] {
              "1",
              "5.65",
              "9.3",
              "54",
              "1954",
              "bar",
              "second test",
              "12.79",
              "123.456",
              "0.01",
              "12",
              "900",
              "3"
            },
          });

  @BeforeClass
  public static void beforeClass() throws Exception {
    dir = newDirectory();
    analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwConfig = newIndexWriterConfig(analyzer);
    iwConfig.setMergePolicy(newLogMergePolicy());
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir, iwConfig);
    for (String[] doc : documents) {
      Document document = new Document();
      document.add(new StringField("id", doc[0], Field.Store.NO));
      document.add(new SortedDocValuesField("id", new BytesRef(doc[0])));
      document.add(
          new NumericDocValuesField(
              "double", Double.doubleToRawLongBits(Double.parseDouble(doc[1]))));
      document.add(
          new NumericDocValuesField("float", Float.floatToRawIntBits(Float.parseFloat(doc[2]))));
      document.add(new NumericDocValuesField("int", Integer.parseInt(doc[3])));
      document.add(new NumericDocValuesField("long", Long.parseLong(doc[4])));
      document.add(new StringField("string", doc[5], Field.Store.NO));
      document.add(new SortedDocValuesField("string", new BytesRef(doc[5])));
      document.add(new TextField("text", doc[6], Field.Store.NO));
      document.add(
          new SortedNumericDocValuesField(
              "floatMv", NumericUtils.floatToSortableInt(Float.parseFloat(doc[7]))));
      document.add(
          new SortedNumericDocValuesField(
              "floatMv", NumericUtils.floatToSortableInt(Float.parseFloat(doc[8]))));
      document.add(
          new SortedNumericDocValuesField(
              "floatMv", NumericUtils.floatToSortableInt(Float.parseFloat(doc[9]))));
      document.add(
          new SortedNumericDocValuesField(
              "doubleMv", NumericUtils.doubleToSortableLong(Double.parseDouble(doc[7]))));
      document.add(
          new SortedNumericDocValuesField(
              "doubleMv", NumericUtils.doubleToSortableLong(Double.parseDouble(doc[8]))));
      document.add(
          new SortedNumericDocValuesField(
              "doubleMv", NumericUtils.doubleToSortableLong(Double.parseDouble(doc[9]))));
      document.add(new SortedNumericDocValuesField("intMv", Long.parseLong(doc[10])));
      document.add(new SortedNumericDocValuesField("intMv", Long.parseLong(doc[11])));
      document.add(new SortedNumericDocValuesField("intMv", Long.parseLong(doc[12])));
      document.add(new SortedNumericDocValuesField("longMv", Long.parseLong(doc[10])));
      document.add(new SortedNumericDocValuesField("longMv", Long.parseLong(doc[11])));
      document.add(new SortedNumericDocValuesField("longMv", Long.parseLong(doc[12])));
      iw.addDocument(document);
    }

    reader = iw.getReader();
    searcher = newSearcher(reader);
    iw.close();
  }

  @AfterClass
  public static void afterClass() throws Exception {
    searcher = null;
    reader.close();
    reader = null;
    dir.close();
    dir = null;
    analyzer.close();
    analyzer = null;
  }

  public void testConst() throws Exception {
    ValueSource vs = new ConstValueSource(0.3f);
    assertHits(new FunctionQuery(vs), new float[] {0.3f, 0.3f});
    assertAllExist(vs);
  }

  public void testDiv() throws Exception {
    ValueSource vs = new DivFloatFunction(new ConstValueSource(10f), new ConstValueSource(5f));
    assertHits(new FunctionQuery(vs), new float[] {2f, 2f});
    assertAllExist(vs);
    vs = new DivFloatFunction(new ConstValueSource(10f), BOGUS_FLOAT_VS);
    assertNoneExist(vs);
    vs = new DivFloatFunction(BOGUS_FLOAT_VS, new ConstValueSource(10f));
    assertNoneExist(vs);
  }

  public void testDocFreq() throws Exception {
    ValueSource vs = new DocFreqValueSource("bogus", "bogus", "text", new BytesRef("test"));
    assertHits(new FunctionQuery(vs), new float[] {2f, 2f});
    assertAllExist(vs);
  }

  public void testDoubleConst() throws Exception {
    ValueSource vs = new DoubleConstValueSource(0.3d);
    assertHits(new FunctionQuery(vs), new float[] {0.3f, 0.3f});
    assertAllExist(vs);
  }

  public void testDouble() throws Exception {
    ValueSource vs = new DoubleFieldSource("double");
    assertHits(new FunctionQuery(vs), new float[] {3.63f, 5.65f});
    assertAllExist(vs);
    assertNoneExist(BOGUS_DOUBLE_VS);
  }

  public void testDoubleMultiValued() throws Exception {
    ValueSource vs = new MultiValuedDoubleFieldSource("doubleMv", Type.MAX);
    assertHits(new FunctionQuery(vs), new float[] {3.69f, 123.456f});
    assertAllExist(vs);

    vs = new MultiValuedDoubleFieldSource("doubleMv", Type.MIN);
    assertHits(new FunctionQuery(vs), new float[] {0.11f, 0.01f});
    assertAllExist(vs);
  }

  public void testFloat() throws Exception {
    ValueSource vs = new FloatFieldSource("float");
    assertHits(new FunctionQuery(vs), new float[] {5.2f, 9.3f});
    assertAllExist(vs);
    assertNoneExist(BOGUS_FLOAT_VS);
  }

  public void testFloatMultiValued() throws Exception {
    ValueSource vs = new MultiValuedFloatFieldSource("floatMv", Type.MAX);
    assertHits(new FunctionQuery(vs), new float[] {3.69f, 123.456f});
    assertAllExist(vs);

    vs = new MultiValuedFloatFieldSource("floatMv", Type.MIN);
    assertHits(new FunctionQuery(vs), new float[] {0.11f, 0.01f});
    assertAllExist(vs);
  }

  public void testIDF() throws Exception {
    Similarity saved = searcher.getSimilarity();
    try {
      searcher.setSimilarity(new ClassicSimilarity());
      ValueSource vs = new IDFValueSource("bogus", "bogus", "text", new BytesRef("test"));
      assertHits(new FunctionQuery(vs), new float[] {1.0f, 1.0f});
      assertAllExist(vs);
    } finally {
      searcher.setSimilarity(saved);
    }
  }

  public void testIf() throws Exception {
    ValueSource vs =
        new IfFunction(
            new BytesRefFieldSource("id"),
            new ConstValueSource(1.0f), // match
            new ConstValueSource(2.0f));
    assertHits(new FunctionQuery(vs), new float[] {1f, 1f});
    assertAllExist(vs);

    // true just if a test value exists...
    vs =
        new IfFunction(
            new LiteralValueSource("false"),
            new ConstValueSource(1.0f), // match
            new ConstValueSource(2.0f));
    assertHits(new FunctionQuery(vs), new float[] {1f, 1f});
    assertAllExist(vs);

    // false value if tests value does not exist
    vs =
        new IfFunction(
            BOGUS_FLOAT_VS, new ConstValueSource(1.0f), new ConstValueSource(2.0f)); // match
    assertHits(new FunctionQuery(vs), new float[] {2F, 2F});
    assertAllExist(vs);

    // final value may still not exist
    vs =
        new IfFunction(
            new BytesRefFieldSource("id"),
            BOGUS_FLOAT_VS, // match
            new ConstValueSource(1.0f));
    assertNoneExist(vs);
  }

  public void testInt() throws Exception {
    ValueSource vs = new IntFieldSource("int");
    assertHits(new FunctionQuery(vs), new float[] {35f, 54f});
    assertAllExist(vs);
    assertNoneExist(BOGUS_INT_VS);
  }

  public void testIntMultiValued() throws Exception {
    ValueSource vs = new MultiValuedIntFieldSource("intMv", Type.MAX);
    assertHits(new FunctionQuery(vs), new float[] {7f, 900f});
    assertAllExist(vs);

    vs = new MultiValuedIntFieldSource("intMv", Type.MIN);
    assertHits(new FunctionQuery(vs), new float[] {1f, 3f});
    assertAllExist(vs);
  }

  public void testJoinDocFreq() throws Exception {
    assertHits(
        new FunctionQuery(new JoinDocFreqValueSource("string", "text")), new float[] {2f, 0f});

    // TODO: what *should* the rules be for exist() ?
  }

  public void testLinearFloat() throws Exception {
    ValueSource vs = new LinearFloatFunction(new ConstValueSource(2.0f), 3, 1);
    assertHits(new FunctionQuery(vs), new float[] {7f, 7f});
    assertAllExist(vs);
    vs = new LinearFloatFunction(BOGUS_FLOAT_VS, 3, 1);
    assertNoneExist(vs);
  }

  public void testLong() throws Exception {
    ValueSource vs = new LongFieldSource("long");
    assertHits(new FunctionQuery(vs), new float[] {4343f, 1954f});
    assertAllExist(vs);
    assertNoneExist(BOGUS_LONG_VS);
  }

  public void testLongMultiValued() throws Exception {
    ValueSource vs = new MultiValuedLongFieldSource("longMv", Type.MAX);
    assertHits(new FunctionQuery(vs), new float[] {7f, 900f});
    assertAllExist(vs);

    vs = new MultiValuedLongFieldSource("longMv", Type.MIN);
    assertHits(new FunctionQuery(vs), new float[] {1f, 3f});
    assertAllExist(vs);
  }

  public void testMaxDoc() throws Exception {
    ValueSource vs = new MaxDocValueSource();
    assertHits(new FunctionQuery(vs), new float[] {2f, 2f});
    assertAllExist(vs);
  }

  public void testMaxFloat() throws Exception {
    ValueSource vs =
        new MaxFloatFunction(
            new ValueSource[] {new ConstValueSource(1f), new ConstValueSource(2f)});
    assertAllExist(vs);
    assertHits(new FunctionQuery(vs), new float[] {2f, 2f});

    // as long as one value exists, then max exists
    vs = new MaxFloatFunction(new ValueSource[] {BOGUS_FLOAT_VS, new ConstValueSource(2f)});
    assertAllExist(vs);
    assertHits(new FunctionQuery(vs), new float[] {2f, 2f});

    vs =
        new MaxFloatFunction(
            new ValueSource[] {BOGUS_FLOAT_VS, new ConstValueSource(2f), BOGUS_DOUBLE_VS});
    assertAllExist(vs);
    assertHits(new FunctionQuery(vs), new float[] {2f, 2f});

    // if none exist, then max doesn't exist
    vs = new MaxFloatFunction(new ValueSource[] {BOGUS_FLOAT_VS, BOGUS_INT_VS, BOGUS_DOUBLE_VS});
    assertNoneExist(vs);
    assertHits(new FunctionQuery(vs), new float[] {0f, 0f});

    // if no values exist should return 0f even if value returned is non zero
    vs =
        new MaxFloatFunction(
            new ValueSource[] {
              new SumFloatFunction(new ValueSource[] {BOGUS_FLOAT_VS, new ConstValueSource(42)})
            });
    assertNoneExist(vs);
    assertHits(new FunctionQuery(vs), new float[] {0f, 0f});
  }

  public void testMinFloat() throws Exception {
    ValueSource vs =
        new MinFloatFunction(
            new ValueSource[] {new ConstValueSource(1f), new ConstValueSource(2f)});
    assertAllExist(vs);
    assertHits(new FunctionQuery(vs), new float[] {1f, 1f});

    // as long as one value exists, then min exists
    vs = new MinFloatFunction(new ValueSource[] {BOGUS_FLOAT_VS, new ConstValueSource(2f)});
    assertAllExist(vs);
    assertHits(new FunctionQuery(vs), new float[] {2f, 2f});

    vs =
        new MinFloatFunction(
            new ValueSource[] {BOGUS_FLOAT_VS, new ConstValueSource(2f), BOGUS_DOUBLE_VS});
    assertAllExist(vs);
    assertHits(new FunctionQuery(vs), new float[] {2f, 2f});

    // if none exist, then min doesn't exist
    vs = new MinFloatFunction(new ValueSource[] {BOGUS_FLOAT_VS, BOGUS_INT_VS, BOGUS_DOUBLE_VS});
    assertNoneExist(vs);
    assertHits(new FunctionQuery(vs), new float[] {0f, 0f});

    // if no values exist should return 0f even if value returned is non zero
    vs =
        new MinFloatFunction(
            new ValueSource[] {
              new SumFloatFunction(new ValueSource[] {BOGUS_FLOAT_VS, new ConstValueSource(42)})
            });
    assertNoneExist(vs);
    assertHits(new FunctionQuery(vs), new float[] {0f, 0f});
  }

  public void testNorm() throws Exception {
    Similarity saved = searcher.getSimilarity();
    try {
      // no norm field (so agnostic to indexed similarity)
      searcher.setSimilarity(new ClassicSimilarity());
      ValueSource vs = new NormValueSource("byte");
      assertHits(new FunctionQuery(vs), new float[] {1f, 1f});

      // regardless of whether norms exist, value source exists == 0
      assertAllExist(vs);

      vs = new NormValueSource("text");
      assertAllExist(vs);

    } finally {
      searcher.setSimilarity(saved);
    }
  }

  public void testNumDocs() throws Exception {
    ValueSource vs = new NumDocsValueSource();
    assertHits(new FunctionQuery(vs), new float[] {2f, 2f});
    assertAllExist(vs);
  }

  public void testPow() throws Exception {
    ValueSource vs = new PowFloatFunction(new ConstValueSource(2f), new ConstValueSource(3f));
    assertHits(new FunctionQuery(vs), new float[] {8f, 8f});
    assertAllExist(vs);
    vs = new PowFloatFunction(BOGUS_FLOAT_VS, new ConstValueSource(3f));
    assertNoneExist(vs);
    vs = new PowFloatFunction(new ConstValueSource(3f), BOGUS_FLOAT_VS);
    assertNoneExist(vs);
  }

  public void testProduct() throws Exception {
    ValueSource vs =
        new ProductFloatFunction(
            new ValueSource[] {new ConstValueSource(2f), new ConstValueSource(3f)});
    assertHits(new FunctionQuery(vs), new float[] {6f, 6f});
    assertAllExist(vs);

    vs = new ProductFloatFunction(new ValueSource[] {BOGUS_FLOAT_VS, new ConstValueSource(3f)});
    assertNoneExist(vs);
  }

  public void testQueryWrapedFuncWrapedQuery() throws Exception {
    ValueSource vs = new QueryValueSource(new FunctionQuery(new ConstValueSource(2f)), 0f);
    assertHits(new FunctionQuery(vs), new float[] {2f, 2f});
    assertAllExist(vs);

    vs =
        new QueryValueSource(
            new FunctionRangeQuery(
                new IntFieldSource("int"), Integer.MIN_VALUE, Integer.MAX_VALUE, true, true),
            0f);
    assertHits(new FunctionQuery(vs), new float[] {35f, 54f});
    assertAllExist(vs);
  }

  public void testQuery() throws Exception {
    Similarity saved = searcher.getSimilarity();

    try {
      searcher.setSimilarity(new ClassicSimilarity());

      ValueSource vs = new QueryValueSource(new TermQuery(new Term("string", "bar")), 42F);
      assertHits(new FunctionQuery(vs), new float[] {42F, 1.4054651F});

      // valuesource should exist only for things matching the term query
      // sanity check via quick & dirty wrapper arround tf
      ValueSource expected =
          new MultiFloatFunction(
              new ValueSource[] {
                new TFValueSource("bogus", "bogus", "string", new BytesRef("bar"))
              }) {

            @Override
            protected String name() {
              return "tf_based_exists";
            }

            @Override
            protected float func(int doc, FunctionValues[] valsArr) throws IOException {
              return valsArr[0].floatVal(doc);
            }

            @Override
            protected boolean exists(int doc, FunctionValues[] valsArr) throws IOException {
              // if tf > 0, then it should exist
              return 0 < func(doc, valsArr);
            }
          };

      assertExists(expected, vs);

      // Query matches all docs, func exists for all docs
      vs = new QueryValueSource(new TermQuery(new Term("text", "test")), 0F);
      assertAllExist(vs);

      // Query matches no docs, func exists for no docs
      vs = new QueryValueSource(new TermQuery(new Term("bogus", "does not exist")), 0F);
      assertNoneExist(vs);

      // doc doesn't match the query, so default value should be returned
      vs = new QueryValueSource(new MatchNoDocsQuery(), 5.0f);
      final LeafReaderContext leaf = searcher.getIndexReader().leaves().get(0);
      FunctionValues fv = vs.getValues(ValueSource.newContext(searcher), leaf);
      assertEquals(5.0f, fv.objectVal(1));

      // test with def value but doc matches the query, so def value shouldn't be returned
      vs = new QueryValueSource(new TermQuery(new Term("text", "test")), 2F);
      fv = vs.getValues(ValueSource.newContext(searcher), leaf);
      assertNotEquals(2f, fv.objectVal(1));
    } finally {
      searcher.setSimilarity(saved);
    }
  }

  public void testRangeMap() throws Exception {
    assertHits(
        new FunctionQuery(new RangeMapFloatFunction(new FloatFieldSource("float"), 5, 6, 1, 0f)),
        new float[] {1f, 0f});
    assertHits(
        new FunctionQuery(
            new RangeMapFloatFunction(
                new FloatFieldSource("float"),
                5,
                6,
                new SumFloatFunction(
                    new ValueSource[] {new ConstValueSource(1f), new ConstValueSource(2f)}),
                new ConstValueSource(11f))),
        new float[] {3f, 11f});

    // TODO: what *should* the rules be for exist() ?
    // ((source exists && source in range && target exists) OR (source not in range && default
    // exists)) ?
  }

  public void testReciprocal() throws Exception {
    ValueSource vs = new ReciprocalFloatFunction(new ConstValueSource(2f), 3, 1, 4);
    assertHits(new FunctionQuery(vs), new float[] {0.1f, 0.1f});
    assertAllExist(vs);

    vs = new ReciprocalFloatFunction(BOGUS_FLOAT_VS, 3, 1, 4);
    assertNoneExist(vs);
  }

  public void testScale() throws Exception {
    ValueSource vs = new ScaleFloatFunction(new IntFieldSource("int"), 0, 1);
    assertHits(new FunctionQuery(vs), new float[] {0.0f, 1.0f});
    assertAllExist(vs);

    vs = new ScaleFloatFunction(BOGUS_INT_VS, 0, 1);
    assertNoneExist(vs);
  }

  public void testSumFloat() throws Exception {
    ValueSource vs =
        new SumFloatFunction(
            new ValueSource[] {new ConstValueSource(1f), new ConstValueSource(2f)});
    assertHits(new FunctionQuery(vs), new float[] {3f, 3f});
    assertAllExist(vs);

    vs = new SumFloatFunction(new ValueSource[] {BOGUS_FLOAT_VS, new ConstValueSource(2f)});
    assertNoneExist(vs);
  }

  public void testSumTotalTermFreq() throws Exception {
    ValueSource vs = new SumTotalTermFreqValueSource("text");
    assertHits(new FunctionQuery(vs), new float[] {8f, 8f});
    assertAllExist(vs);
  }

  public void testTermFreq() throws Exception {
    ValueSource vs = new TermFreqValueSource("bogus", "bogus", "text", new BytesRef("test"));
    assertHits(new FunctionQuery(vs), new float[] {3f, 1f});
    assertAllExist(vs);

    vs = new TermFreqValueSource("bogus", "bogus", "string", new BytesRef("bar"));
    assertHits(new FunctionQuery(vs), new float[] {0f, 1f});
    assertAllExist(vs);

    // regardless of whether norms exist, value source exists == 0
    vs = new TermFreqValueSource("bogus", "bogus", "bogus", new BytesRef("bogus"));
    assertHits(new FunctionQuery(vs), new float[] {0F, 0F});
    assertAllExist(vs);
  }

  public void testMultiBoolFunction() throws Exception {
    // verify toString and description
    List<ValueSource> valueSources =
        new ArrayList<>(
            Arrays.asList(
                new ConstValueSource(4.1f),
                new ConstValueSource(1.2f),
                new DoubleFieldSource("some_double")));
    ValueSource vs =
        new MultiBoolFunction(valueSources) {
          @Override
          protected String name() {
            return "test";
          }

          @Override
          protected boolean func(int doc, FunctionValues[] vals) throws IOException {
            return false;
          }
        };
    assertEquals("test(const(4.1),const(1.2),double(some_double))", vs.description());

    final LeafReaderContext leaf = searcher.getIndexReader().leaves().get(0);
    FunctionValues fv = vs.getValues(ValueSource.newContext(searcher), leaf);
    // doesn't matter what is the docId, verify toString
    assertEquals("test(const(4.1),const(1.2),double(some_double)=0.0)", fv.toString(1));
  }

  public void testTF() throws Exception {
    Similarity saved = searcher.getSimilarity();
    try {
      // no norm field (so agnostic to indexed similarity)
      searcher.setSimilarity(new ClassicSimilarity());

      ValueSource vs = new TFValueSource("bogus", "bogus", "text", new BytesRef("test"));
      assertHits(new FunctionQuery(vs), new float[] {(float) Math.sqrt(3d), (float) Math.sqrt(1d)});
      assertAllExist(vs);

      vs = new TFValueSource("bogus", "bogus", "string", new BytesRef("bar"));
      assertHits(new FunctionQuery(vs), new float[] {0f, 1f});
      assertAllExist(vs);

      // regardless of whether norms exist, value source exists == 0
      vs = new TFValueSource("bogus", "bogus", "bogus", new BytesRef("bogus"));
      assertHits(new FunctionQuery(vs), new float[] {0F, 0F});
      assertAllExist(vs);

    } finally {
      searcher.setSimilarity(saved);
    }
  }

  public void testTotalTermFreq() throws Exception {
    ValueSource vs = new TotalTermFreqValueSource("bogus", "bogus", "text", new BytesRef("test"));
    assertHits(new FunctionQuery(vs), new float[] {4f, 4f});
    assertAllExist(vs);
  }

  public void testMultiFunctionHelperEquivilence() throws IOException {
    // the 2 arg versions of these methods should return the exact same results as
    // the multi arg versions with a 2 element array

    // actual doc / index is not relevant for this test
    final LeafReaderContext leaf = searcher.getIndexReader().leaves().get(0);
    final Map<Object, Object> context = ValueSource.newContext(searcher);

    ALL_EXIST_VS.createWeight(context, searcher);
    NONE_EXIST_VS.createWeight(context, searcher);

    final FunctionValues ALL = ALL_EXIST_VS.getValues(context, leaf);
    final FunctionValues NONE = NONE_EXIST_VS.getValues(context, leaf);

    // quick sanity checks of explicit results
    assertTrue(MultiFunction.allExists(1, ALL, ALL));
    assertTrue(MultiFunction.allExists(1, new FunctionValues[] {ALL, ALL}));
    assertTrue(MultiFunction.anyExists(1, ALL, NONE));
    assertTrue(MultiFunction.anyExists(1, new FunctionValues[] {ALL, NONE}));
    //
    assertFalse(MultiFunction.allExists(1, ALL, NONE));
    assertFalse(MultiFunction.allExists(1, new FunctionValues[] {ALL, NONE}));
    assertFalse(MultiFunction.anyExists(1, NONE, NONE));
    assertFalse(MultiFunction.anyExists(1, new FunctionValues[] {NONE, NONE}));

    // iterate all permutations and verify equivilence
    for (FunctionValues firstArg : new FunctionValues[] {ALL, NONE}) {
      for (FunctionValues secondArg : new FunctionValues[] {ALL, NONE}) {
        assertEquals(
            "allExists(" + firstArg + "," + secondArg + ")",
            MultiFunction.allExists(1, firstArg, secondArg),
            MultiFunction.allExists(1, new FunctionValues[] {firstArg, secondArg}));
        assertEquals(
            "anyExists(" + firstArg + "," + secondArg + ")",
            MultiFunction.anyExists(1, firstArg, secondArg),
            MultiFunction.anyExists(1, new FunctionValues[] {firstArg, secondArg}));

        // future proof against posibility of someone "optimizing" the array method
        // if .length==2 ... redundent third arg should give same results as well...
        assertEquals(
            "allExists(" + firstArg + "," + secondArg + "," + secondArg + ")",
            MultiFunction.allExists(1, firstArg, secondArg),
            MultiFunction.allExists(1, new FunctionValues[] {firstArg, secondArg, secondArg}));
        assertEquals(
            "anyExists(" + firstArg + "," + secondArg + "," + secondArg + ")",
            MultiFunction.anyExists(1, firstArg, secondArg),
            MultiFunction.anyExists(1, new FunctionValues[] {firstArg, secondArg, secondArg}));
      }
    }
  }

  public void testWrappingAsDoubleValues() throws Exception {

    class AssertScoreComputedOnceQuery extends Query {

      private final Query in;

      public AssertScoreComputedOnceQuery(Query query) {
        in = query;
      }

      @Override
      public String toString(String field) {
        return in.toString(field);
      }

      @Override
      public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
          throws IOException {
        return new FilterWeight(in.createWeight(searcher, scoreMode, boost)) {
          @Override
          public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
            final var subScorer = in.scorer(context);
            if (subScorer == null) {
              return null;
            }
            final var scorer =
                new FilterScorer(subScorer) {
                  int lastDocId = -1;

                  @Override
                  public float score() throws IOException {
                    assertTrue("shouldn't re-compute score", lastDocId != docID());
                    this.lastDocId = docID();
                    return in.score();
                  }

                  @Override
                  public float getMaxScore(int upTo) throws IOException {
                    return in.getMaxScore(upTo);
                  }
                };
            return new DefaultScorerSupplier(scorer);
          }
        };
      }

      @Override
      public Query rewrite(IndexSearcher indexSearcher) throws IOException {
        var rewrite = in.rewrite(indexSearcher);
        return rewrite == in ? this : new AssertScoreComputedOnceQuery(rewrite);
      }

      @Override
      public void visit(QueryVisitor visitor) {
        in.visit(visitor);
      }

      @Override
      public boolean equals(Object obj) {
        throw new UnsupportedOperationException();
      }

      @Override
      public int hashCode() {
        return in.hashCode();
      }
    }

    FunctionScoreQuery q =
        FunctionScoreQuery.boostByValue(
            new AssertScoreComputedOnceQuery(new TermQuery(new Term("text", "test"))),
            new DoubleFieldSource("double").asDoubleValuesSource());

    var topFieldDocs = searcher.search(q, 1);
    assertTrue(topFieldDocs.scoreDocs.length > 0);

    // assert that the query has not cached a reference to the IndexSearcher
    FunctionScoreQuery.MultiplicativeBoostValuesSource source1 =
        (FunctionScoreQuery.MultiplicativeBoostValuesSource) q.getSource();
    ValueSource.WrappedDoubleValuesSource source2 =
        (ValueSource.WrappedDoubleValuesSource) source1.boost;
    assertNull(source2.searcher);
  }

  /** Tests "scorer" key-value inside the Map context to ValueSource */
  public void testScorerContext() throws IOException {
    // a VS that yields the score
    class ScoreValueSource extends ValueSource {
      @Override
      public FunctionValues getValues(Map<Object, Object> context, LeafReaderContext readerContext)
          throws IOException {
        var scorer = (Scorable) context.get("scorer");
        assertNotNull(scorer);
        return new FloatDocValues(this) {
          @Override
          public float floatVal(int doc) throws IOException {
            return scorer.score();
          }
        };
      }

      @Override
      public boolean equals(Object o) {
        return this == o; // just for a test
      }

      @Override
      public int hashCode() {
        return 0; // just for a test
      }

      @Override
      public String description() {
        return "score";
      }
    }

    var plainQ = new TermQuery(new Term("text", "test"));
    float origScore = searcher.search(plainQ, 1).scoreDocs[0].score;

    // boosts the score by the value source (which is the score), thus score^2
    var scoreSquaredQ =
        FunctionScoreQuery.boostByValue(plainQ, new ScoreValueSource().asDoubleValuesSource());
    var topFieldDocs = searcher.search(scoreSquaredQ, 1);
    assertTrue(topFieldDocs.scoreDocs.length > 0);
    assertEquals(origScore * origScore, topFieldDocs.scoreDocs[0].score, 0.00001);
  }

  public void testBuildingFromDoubleValues() throws Exception {
    DoubleValuesSource dvs =
        ValueSource.fromDoubleValuesSource(DoubleValuesSource.fromDoubleField("double"))
            .asDoubleValuesSource();
    assertHits(
        new FunctionQuery(ValueSource.fromDoubleValuesSource(dvs)), new float[] {3.63f, 5.65f});
  }

  public void testFromDoubleValuesSortWithScore() throws Exception {
    // Create a query that matches all documents
    var query = new TermQuery(new Term("text", "test"));

    // Get the original scores
    TopDocs originalDocs = searcher.search(query, documents.size());
    ScoreDoc[] originalScoreDocs = originalDocs.scoreDocs;

    // Use a DoubleValuesSource that references the score and convert it to a ValueSource
    ValueSource scoreSource = ValueSource.fromDoubleValuesSource(DoubleValuesSource.SCORES);
    SortField sortField = scoreSource.getSortField(true); // true for reverse (descending)
    Sort sort = new Sort(sortField);

    // Search with the sort.
    //   note: used to fail with an assertion before fromDoubleValuesSource's impl was improved
    TopDocs sortedDocs = searcher.search(query, documents.size(), sort);
    ScoreDoc[] sortedScoreDocs = sortedDocs.scoreDocs;

    // Verify we got the same docs in-order but don't check the scores
    assertEquals(originalScoreDocs.length, sortedScoreDocs.length);
    for (int i = 0; i < originalScoreDocs.length; i++) {
      assertEquals(originalScoreDocs[i].doc, sortedScoreDocs[i].doc);
    }
  }

  /**
   * Asserts that for every doc, the {@link FunctionValues#exists} value from the {@link
   * ValueSource} is <b>true</b>.
   */
  void assertAllExist(ValueSource vs) {
    assertExists(ALL_EXIST_VS, vs);
  }

  /**
   * Asserts that for every doc, the {@link FunctionValues#exists} value from the {@link
   * ValueSource} is <b>false</b>.
   */
  void assertNoneExist(ValueSource vs) {
    assertExists(NONE_EXIST_VS, vs);
  }

  /**
   * Asserts that for every doc, the {@link FunctionValues#exists} value from the <code>actual
   * </code> {@link ValueSource} matches the {@link FunctionValues#exists} value from the <code>
   * expected</code> {@link ValueSource}
   */
  void assertExists(ValueSource expected, ValueSource actual) {
    Map<Object, Object> context = ValueSource.newContext(searcher);
    try {
      expected.createWeight(context, searcher);
      actual.createWeight(context, searcher);

      for (org.apache.lucene.index.LeafReaderContext leaf : searcher.getIndexReader().leaves()) {
        final FunctionValues expectedVals = expected.getValues(context, leaf);
        final FunctionValues actualVals = actual.getValues(context, leaf);

        String msg = expected.toString() + " ?= " + actual.toString() + " -> ";
        for (int i = 0; i < leaf.reader().maxDoc(); ++i) {
          assertEquals(msg + i, expectedVals.exists(i), actualVals.exists(i));
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(actual.toString(), e);
    }
  }

  void assertHits(Query q, float[] scores) throws Exception {
    ScoreDoc[] expected = new ScoreDoc[scores.length];
    int[] expectedDocs = new int[scores.length];
    for (int i = 0; i < expected.length; i++) {
      expectedDocs[i] = i;
      expected[i] = new ScoreDoc(i, scores[i]);
    }
    TopDocs docs =
        searcher.search(
            q, documents.size(), new Sort(new SortField("id", SortField.Type.STRING)), true);
    CheckHits.checkHits(random(), q, "", searcher, expectedDocs);
    CheckHits.checkHitsQuery(q, expected, docs.scoreDocs, expectedDocs);
    CheckHits.checkExplanations(q, "", searcher);
  }

  /**
   * Simple test value source that implements {@link FunctionValues#exists} as a constant
   *
   * @see #ALL_EXIST_VS
   * @see #NONE_EXIST_VS
   */
  private static final class ExistsValueSource extends ValueSource {

    final boolean expected;
    final int value;

    public ExistsValueSource(boolean expected) {
      this.expected = expected;
      this.value = expected ? 1 : 0;
    }

    @Override
    public boolean equals(Object o) {
      return o == this;
    }

    @Override
    public int hashCode() {
      return value;
    }

    @Override
    public String description() {
      return expected ? "ALL_EXIST" : "NONE_EXIST";
    }

    @Override
    public FunctionValues getValues(Map<Object, Object> context, LeafReaderContext readerContext) {
      return new FloatDocValues(this) {
        @Override
        public float floatVal(int doc) {
          return (float) value;
        }

        @Override
        public boolean exists(int doc) {
          return expected;
        }
      };
    }
  }

  /**
   * @see ExistsValueSource
   */
  private static final ValueSource ALL_EXIST_VS = new ExistsValueSource(true);

  /**
   * @see ExistsValueSource
   */
  private static final ValueSource NONE_EXIST_VS = new ExistsValueSource(false);
}
