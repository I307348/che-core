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
package org.eclipse.che.api.machine.server.command;

import com.google.inject.Inject;

import org.eclipse.che.api.core.rest.annotations.GenerateLink;
import org.eclipse.che.api.machine.server.dao.CommandDao;
import org.eclipse.che.api.machine.shared.dto.CommandDescriptor;
import org.eclipse.che.api.machine.shared.dto.CommandUpdate;
import org.eclipse.che.api.machine.shared.dto.NewCommand;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.che.api.machine.server.Constants.LINK_REL_CREATE_COMMAND;
import static org.eclipse.che.api.machine.server.Constants.LINK_REL_REMOVE_COMMAND;

/**
 * Command API
 *
 * @author Eugene Voevodin
 */
@Path("/command")
public class CommandService {

    private final CommandDao commandDao;

    @Inject
    public CommandService(CommandDao commandDao) {
        this.commandDao = commandDao;
    }

    @POST
    @Path("/{ws-id}")
    @Consumes(APPLICATION_JSON)
    @GenerateLink(rel = LINK_REL_CREATE_COMMAND)
    @RolesAllowed({"workspace/admin", "workspace/developer", "system/admin", "system/manager"})
    public Response createCommand(@PathParam("ws-id") String workspaceId, NewCommand command) {
        return null;
    }

    @GET
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({"user", "system/admin", "system/manager"})
    public CommandDescriptor getCommand(@PathParam("id") String id) {
        return null;
    }

    @GET
    @Path("/{ws-id}/all") // TODO consider /{ws-id}/available or /{ws-id}/list
    @Produces(APPLICATION_JSON)
    public List<CommandDescriptor> getAvailableCommands(@PathParam("ws-id") String workspaceId) {
        return null;
    }

    @PUT
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @RolesAllowed({"user", "system/admin", "system/manager"})
    public CommandDescriptor updateCommand(@PathParam("id") String commandId, CommandUpdate update) {
        return null;
    }

    @DELETE
    @Path("/{id}")
    @GenerateLink(rel = LINK_REL_REMOVE_COMMAND)
    @RolesAllowed({"user", "system/admin", "system/manager"})
    public void removeCommand(@PathParam("id") String id) {

    }
}
