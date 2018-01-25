/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gosololaw.elasticsearch;

import org.apache.lucene.index.*;

import org.apache.lucene.store.ByteArrayDataInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.lookup.LeafSearchLookup;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * This class is instantiated when Elasticsearch loads the plugin for the
 * first time. If you change the name of this plugin, make sure to update
 * src/main/resources/es-plugin.properties file that points to this class.
 */
public final class VectorScoringPlugin extends Plugin implements ScriptPlugin {

    private static final int DOUBLE_SIZE = 8;

    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new VectorScoreEngine();
    }

    /** An example {@link ScriptEngine} that uses Lucene segment details to implement pure document frequency scoring. */
    // tag::expert_engine
    private static class VectorScoreEngine implements ScriptEngine {

        @Override
        public String getType() {
            return "binary_vector_score";
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> params) {
            if (!context.equals(SearchScript.CONTEXT)) {
                throw new IllegalArgumentException(getType() + " scripts cannot be used for context [" + context.name + "]");
            }
            // we use the script "source" as the script identifier
            if ("vector_scoring".equals(scriptSource)) {
                SearchScript.Factory factory = (p, lookup) -> new SearchScript.LeafFactory() {
                    private final double[] inputVector;
                    final String field;
                    final String term;
                    {
                        final Object field = p.get("field");
                        term = p.get("term").toString();
                        if (field == null)
                            throw new IllegalArgumentException("binary_vector_score script requires field input");
                        this.field = field.toString();

                        // get query inputVector - convert to primitive
                        final ArrayList<Double> tmp = (ArrayList<Double>) p.get("vector");
                        this.inputVector = new double[tmp.size()];
                        for (int i = 0; i < inputVector.length; i++) {
                            inputVector[i] = tmp.get(i);
                        }
                    }

                    @Override
                    public SearchScript newInstance(LeafReaderContext context) throws IOException {

                        return new SearchScript(p, lookup, context) {
                            BinaryDocValues accessor = context.reader().getBinaryDocValues(field);
                            Boolean is_value = false;

                            @Override
                            public void setDocument(int docId) {
                                try {
                                    accessor.advanceExact(docId);
                                    is_value = true;
                                } catch (IOException e) {
                                    is_value = false;
                                }
                            }

                            @Override
                            public double runAsDouble() {
                                if (!is_value) return 0;
                                final byte[] bytes;
                                try {
                                     bytes = accessor.binaryValue().bytes;
                                } catch (IOException e) {
                                     return 0;
                                }

                                final int size = inputVector.length;

                                final ByteArrayDataInput input = new ByteArrayDataInput(bytes);
                                input.readVInt(); // returns the number of values which should be 1, MUST appear hear since it affect the next calls
                                final int len = input.readVInt(); // returns the number of bytes to read
                                if(len != size * DOUBLE_SIZE) {
                                    return 0.0;
                                }
                                final int position = input.getPosition();
                                final DoubleBuffer doubleBuffer = ByteBuffer.wrap(bytes, position, len).asDoubleBuffer();

                                final double[] docVector = new double[size];
                                doubleBuffer.get(docVector);

                                double score = 0;
                                for (int i = 0; i < size; i++) {
                                    score += docVector[i] * inputVector[i];
                                }
                                return score;
                            }
                        };
                    }

                    @Override
                    public boolean needs_score() {
                        return false;
                    }
                };
                return context.factoryClazz.cast(factory);
            }
            throw new IllegalArgumentException("Unknown script name " + scriptSource);
        }

        @Override
        public void close() {
            // optionally close resources
        }

    }
    // end::expert_engine

}