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

import org.junit.jupiter.api.extension.ExtendWith;
import org.terasology.blockGraphs.dataMovement.GraphMovementSystem;
import org.terasology.blockGraphs.graphDefinitions.GraphType;
import org.terasology.blockGraphs.testDefinitions.TestLeftDefinition;
import org.terasology.blockGraphs.testDefinitions.TestRandomDefinition;
import org.terasology.blockGraphs.testDefinitions.TestRemoveDefinition;
import org.terasology.blockGraphs.testDefinitions.TestUpwardsDefinition;
import org.terasology.engine.SimpleUri;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.internal.EngineEntityManager;
import org.terasology.moduletestingenvironment.MTEExtension;
import org.terasology.moduletestingenvironment.ModuleTestingHelper;
import org.terasology.network.NetworkSystem;
import org.terasology.network.internal.NetworkSystemImpl;
import org.terasology.registry.In;


@ExtendWith(MTEExtension.class)
public class GraphTesting {
    @In
    protected GraphMovementSystem movementSystem;
    @In
    protected BlockGraphManager graphManager;
    @In
    protected BlockGraphConstructor graphConstructor;
    @In
    protected ModuleTestingHelper helper;

    @In
    private NetworkSystem networkSystem;
    @In
    private EngineEntityManager engineEntityManager;
    @In
    private EntityManager entityManager;

    public void initialize() {

        /* Stops NPEs when adding a component to an entity (and possibly other related methods) */
        engineEntityManager.unsubscribe((NetworkSystemImpl) networkSystem);


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
        EntityBuilder builder = entityManager.newBuilder();
        builder.setPersistent(true);
        for (Component component : components) {
            builder.addComponent(component);
        }
        return builder.buildWithoutLifecycleEvents();
    }
}
