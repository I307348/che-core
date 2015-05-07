/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.machine.gwt.client;

import org.eclipse.che.api.machine.shared.dto.MachineDescriptor;
import org.eclipse.che.api.machine.shared.dto.ProcessDescriptor;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.collections.Array;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Client for Machine service.
 *
 * @author Artem Zatsarynnyy
 */
public interface MachineServiceClient {

    /**
     * Create and start machine from scratch using recipe.
     *
     * @param workspaceId
     *         ID of a workspace machine should be bound to
     * @param machineType
     *         type of machine
     * @param recipeType
     *         type of recipe
     * @param recipeScript
     *         recipe script
     * @param outputChannel
     *         websocket chanel where machine logs should be put
     */
    Promise<MachineDescriptor> createMachineFromRecipe(@Nonnull String workspaceId,
                                                       @Nonnull String machineType,
                                                       @Nonnull String recipeType,
                                                       @Nonnull String recipeScript,
                                                       @Nullable String outputChannel);

    /**
     * Restore and start machine from snapshot.
     *
     * @param snapshotId
     *         ID of snapshot machine should be restored from
     * @param outputChannel
     *         websocket chanel where machine logs should be put
     */
    Promise<MachineDescriptor> createMachineFromSnapshot(@Nonnull String snapshotId, @Nullable String outputChannel);

    /**
     * Find machines connected with specific workspace/project.
     *
     * @param workspaceId
     *         workspace binding
     * @param projectPath
     *         project binding
     */
    Promise<Array<MachineDescriptor>> getMachines(@Nonnull String workspaceId, @Nullable String projectPath);

    /**
     * Destroy machine with the specified ID.
     *
     * @param machineId
     *         ID of machine that should be destroyed
     */
    Promise<Void> destroyMachine(@Nonnull String machineId);

    /**
     * Execute a command in machine.
     *
     * @param machineId
     *         ID of the machine where command should be executed
     * @param commandLine
     *         command line that should be executed in the machine
     * @param outputChannel
     *         websocket chanel for execution logs
     */
    Promise<ProcessDescriptor> executeCommand(@Nonnull String machineId, @Nonnull String commandLine, @Nullable String outputChannel);

    /**
     * Bind project to machine.
     *
     * @param machineId
     *         machine where project should be bound
     * @param projectPath
     *         project that should be bound
     */
    Promise<Void> bindProject(@Nonnull String machineId, @Nonnull String projectPath);

    /**
     * Unbind project from machine.
     *
     * @param machineId
     *         machine where project should be unbound
     * @param projectPath
     *         project that should be unbound
     */
    Promise<Void> unbindProject(@Nonnull String machineId, @Nonnull String projectPath);
}
