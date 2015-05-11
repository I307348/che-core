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
package org.eclipse.che.api.machine.shared;

/**
 * Command that can be used to create {@link Process} in a machine.
 *
 * @author gazarenkov
 * @author Eugene Voevodin
 */
public interface Command {

    //TODO consider: (workspaceId + name + creator) as id

    String getId(); // command12345679

    //TODO consider: name validation

    String getName(); //MVN_CLEAN_INSTALL

    String getCommandLine();//mvn clean install

    String getCreator(); //user123456789

    String getWorkspaceId();// workspace123456789

    //TODO consider: scope

    String getVisibility(); //public
}
