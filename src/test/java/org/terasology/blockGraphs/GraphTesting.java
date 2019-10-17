/*
 * Copyright 2019 MovingBlocks
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
package org.terasology.blockGraphs;

import com.google.common.collect.Sets;
import org.terasology.blockGraphs.dataMovement.GraphMovementSystem;
import org.terasology.blockGraphs.graphDefinitions.GraphType;
import org.terasology.engine.SimpleUri;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.internal.EngineEntityManager;
import org.terasology.moduletestingenvironment.ModuleTestingEnvironment;
import org.terasology.network.NetworkSystem;
import org.terasology.network.internal.NetworkSystemImpl;

import java.util.Set;

public class GraphTesting extends ModuleTestingEnvironment {

    protected GraphMovementSystem movementSystem;
    protected BlockGraphManager graphManager;
    protected BlockGraphConstructor graphConstructor;

    @Override
    public Set<String> getDependencies() {
        return Sets.newHashSet("BlockGraphs");
    }

    public void initialize() {

        /* Stops NPEs when adding a component to an entity (and possibly other related methods) */
        ((EngineEntityManager) getHostContext().get(EntityManager.class)).unsubscribe((NetworkSystemImpl) getHostContext().get(NetworkSystem.class));

        movementSystem = getHostContext().get(GraphMovementSystem.class);
        graphManager = getHostContext().get(BlockGraphManager.class);
        graphConstructor = getHostContext().get(BlockGraphConstructor.class);

        GraphType graphType = new GraphType(new SimpleUri("BlockGraphs:TestGraph"));
        graphType.addNodeType(new TestUpwardsDefinition());
        graphType.addNodeType(new TestRandomDefinition());
        graphType.addNodeType(new TestLeftDefinition());
        graphType.addNodeType(new TestRemoveDefinition());

        graphManager.addGraphType(graphType);
    }

    /**
     * Builds an entity data packet, already with appropriate components and values set
     *
     * @param components Any components to add to the entity
     * @return A data packet to be passed around the graphs
     */
    protected EntityRef buildData(Component... components) {
        EntityBuilder builder = getHostContext().get(EntityManager.class).newBuilder();
        builder.setPersistent(true);
        for (Component component : components) {
            builder.addComponent(component);
        }
        return builder.buildWithoutLifecycleEvents();
    }
}
