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

import org.eclipse.che.api.machine.shared.Command;

import java.util.Objects;

/**
 * Implementation of {@link Command}
 *
 * @author Eugene Voevodin
 */
public class CommandImpl implements Command {

    private String id;
    private String name;
    private String commandLine;
    private String creator;
    private String workspaceId;
    private String visibility;
    private String type;
    private String workingDir;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public Command withId(String id) {
        this.id = id;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Command withName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String getCommandLine() {
        return commandLine;
    }

    @Override
    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    @Override
    public Command withCommandLine(String commandLine) {
        this.commandLine = commandLine;
        return this;
    }

    @Override
    public String getCreator() {
        return creator;
    }

    @Override
    public void setCreator(String creator) {
        this.creator = creator;
    }

    @Override
    public Command withCreator(String creator) {
        this.creator = creator;
        return this;
    }

    @Override
    public String getWorkspaceId() {
        return workspaceId;
    }

    @Override
    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    @Override
    public Command withWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
        return this;
    }

    @Override
    public String getVisibility() {
        return visibility;
    }

    @Override
    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    @Override
    public Command withVisibility(String visibility) {
        this.visibility = visibility;
        return this;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    @Override
    public Command withType(String type) {
        this.type = type;
        return this;
    }

    @Override
    public String getWorkingDir() {
        return workingDir;
    }

    @Override
    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    @Override
    public Command withWorkingDir(String workingDir) {
        this.workingDir = workingDir;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CommandImpl)) {
            return false;
        }
        final CommandImpl command = (CommandImpl)obj;
        return Objects.equals(id, command.id) &&
               Objects.equals(name, command.name) &&
               Objects.equals(commandLine, command.commandLine) &&
               Objects.equals(creator, command.creator) &&
               Objects.equals(workspaceId, command.workspaceId) &&
               Objects.equals(visibility, command.visibility) &&
               Objects.equals(type, command.type) &&
               Objects.equals(workingDir, command.workingDir);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(id);
        hash = 31 * hash + Objects.hashCode(name);
        hash = 31 * hash + Objects.hashCode(commandLine);
        hash = 31 * hash + Objects.hashCode(creator);
        hash = 31 * hash + Objects.hashCode(workspaceId);
        hash = 31 * hash + Objects.hashCode(visibility);
        hash = 31 * hash + Objects.hashCode(type);
        hash = 31 * hash + Objects.hashCode(workingDir);
        return hash;
    }
}
