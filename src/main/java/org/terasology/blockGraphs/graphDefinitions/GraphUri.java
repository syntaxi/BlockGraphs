/*
 * Copyright 2018 MovingBlocks
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
package org.terasology.blockGraphs.graphDefinitions;

import org.terasology.engine.SimpleUri;
import org.terasology.engine.Uri;
import org.terasology.naming.Name;

import java.util.Objects;

/**
 * Unique identifier for all graphs, even within a subtype
 * <p>
 * Graph types are distinguished by a {@link SimpleUri}
 */
public class GraphUri implements Uri {

    private static final String FIELD_SEPARATOR = ".";
    private final SimpleUri graphUri;
    private final Long instanceNo;

    //TODO: make private to ensure all instances are unique
    public GraphUri(SimpleUri graphUri, Long instanceNo) {
        this.graphUri = graphUri;
        this.instanceNo = instanceNo;
    }

    public GraphUri(String textVersion) {
        int seperatorIndex = textVersion.indexOf(FIELD_SEPARATOR, 2);

        if (seperatorIndex != -1) {
            graphUri = new SimpleUri(textVersion.substring(0, seperatorIndex));
            instanceNo = Long.parseLong(textVersion.substring(seperatorIndex + 1));
        } else {
            // create invalid uri
            instanceNo = null;
            graphUri = new SimpleUri();
        }
    }

    public Name getGraphName() {
        return graphUri.getObjectName();
    }

    @Override
    public boolean isValid() {
        return graphUri.isValid() && instanceNo != null;
    }

    @Override
    public Name getModuleName() {
        return graphUri.getModuleName();
    }

    public SimpleUri getGraphUri() {
        return graphUri;
    }

    public Long getInstanceNo() {
        return instanceNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GraphUri that = (GraphUri) o;

        return graphUri.equals(that.graphUri) && Objects.equals(instanceNo, that.instanceNo);
    }

    @Override
    public int hashCode() {
        int result = graphUri.hashCode();
        result = 31 * result + (instanceNo != null ? instanceNo.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        if (!isValid()) {
            return "";
        }
        return graphUri + FIELD_SEPARATOR + instanceNo;
    }
}
