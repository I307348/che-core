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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.inject.Inject;

import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.rest.annotations.GenerateLink;
import org.eclipse.che.api.machine.server.dao.CommandDao;
import org.eclipse.che.api.machine.shared.Command;
import org.eclipse.che.api.machine.shared.dto.CommandDescriptor;
import org.eclipse.che.api.machine.shared.dto.CommandUpdate;
import org.eclipse.che.api.machine.shared.dto.NewCommand;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.user.User;
import org.eclipse.che.dto.server.DtoFactory;

import javax.annotation.Nullable;
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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static org.eclipse.che.api.machine.server.Constants.LINK_REL_CREATE_COMMAND;
import static org.eclipse.che.api.machine.server.Constants.LINK_REL_REMOVE_COMMAND;

/**
 * Command API
 *
 * @author Eugene Voevodin
 */
@Path("/command")
public class CommandService extends Service {

    private static final String DEFAULT_VISIBILITY = "private";

    private final CommandDao commandDao;

    @Inject
    public CommandService(CommandDao commandDao) {
        this.commandDao = commandDao;
    }

    @POST
    @Path("/{ws-id}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @GenerateLink(rel = LINK_REL_CREATE_COMMAND)
    @RolesAllowed({"workspace/admin", "workspace/developer"})
    public Response createCommand(@PathParam("ws-id") String workspaceId, NewCommand newCommand) throws ApiException {
        if (newCommand == null) {
            throw new ForbiddenException("Command required");
        }
        if (isNullOrEmpty(newCommand.getName())) {
            throw new ForbiddenException("Command name required");
        }
        if (isNullOrEmpty(newCommand.getCommandLine())) {
            throw new ForbiddenException("Command line required");
        }
        final CommandImpl command = new CommandImpl().withId(NameGenerator.generate("command", 16))
                                                     .withName(newCommand.getName())
                                                     .withCreator(EnvironmentContext.getCurrent().getUser().getId())
                                                     .withWorkspaceId(workspaceId)
                                                     .withCommandLine(newCommand.getCommandLine())
                                                     .withType(newCommand.getType())
                                                     .withVisibility(firstNonNull(newCommand.getVisibility(), DEFAULT_VISIBILITY))
                                                     .withWorkingDir(newCommand.getWorkingDir());
        commandDao.create(command);
        return Response.status(CREATED)
                       .entity(asCommandDescriptor(command))
                       .build();
    }

    @GET
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({"user", "system/admin", "system/manager"})
    public CommandDescriptor getCommand(@PathParam("id") String id) throws ApiException {
        final Command command = commandDao.getCommand(id);
        final User user = EnvironmentContext.getCurrent().getUser();
        if (!"public".equals(command.getVisibility()) && !user.getId().equals(command.getCreator())) {
            throw new ForbiddenException(format("User '%s' doesn't have access to command '%s'", user.getId(), command.getId()));
        }
        return asCommandDescriptor(command);
    }

    //TODO consider: maxItems and skipCount query params

    @GET
    @Path("/{ws-id}/all")
    @Produces(APPLICATION_JSON)
    public List<CommandDescriptor> getCommands(@PathParam("ws-id") String workspaceId) throws ServerException {
        final List<Command> commands = commandDao.getCommands(workspaceId, EnvironmentContext.getCurrent().getUser().getId());
        return FluentIterable.from(commands)
                             .transform(new Function<Command, CommandDescriptor>() {
                                 @Nullable
                                 @Override
                                 public CommandDescriptor apply(Command command) {
                                     return asCommandDescriptor(command);
                                 }
                             }).toList();
    }

    @PUT
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @RolesAllowed("user")
    public CommandDescriptor updateCommand(CommandUpdate update) throws ApiException {
        if (update == null) {
            throw new ForbiddenException("Update required");
        }
        if (update.getId() == null) {
            throw new ForbiddenException("Command id required");
        }
        //check that current user is command creator
        final Command command = commandDao.getCommand(update.getId());
        final User user = EnvironmentContext.getCurrent().getUser();
        if (!command.getCreator().equals(user.getId())) {
            throw new ForbiddenException(format("User '%s' doesn't have access to update command '%s'", user.getId(), update.getId()));
        }

        commandDao.update(update);

        return asCommandDescriptor(commandDao.getCommand(update.getId()));
    }

    @DELETE
    @Path("/{id}")
    @GenerateLink(rel = LINK_REL_REMOVE_COMMAND)
    @RolesAllowed({"user", "system/admin", "system/manager"})
    public void removeCommand(@PathParam("id") String id) {

    }

    private CommandDescriptor asCommandDescriptor(Command command) {
        //TODO add links
        return DtoFactory.getInstance()
                         .createDto(CommandDescriptor.class)
                         .withId(command.getId())
                         .withName(command.getName())
                         .withCreator(EnvironmentContext.getCurrent().getUser().getId())
                         .withWorkspaceId(command.getWorkspaceId())
                         .withCommandLine(command.getCommandLine())
                         .withType(command.getType())
                         .withVisibility(command.getVisibility())
                         .withWorkingDir(command.getWorkingDir());
    }
}
