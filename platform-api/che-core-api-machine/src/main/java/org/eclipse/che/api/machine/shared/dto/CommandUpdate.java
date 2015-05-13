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
package org.eclipse.che.api.machine.shared.dto;

import org.eclipse.che.dto.shared.DTO;

/**
 * @author Eugene Voevodin
 */
@DTO
public interface CommandUpdate {

    String getName();

    void setName(String name);

    CommandUpdate withName(String name);

    String getCommandLine();

    void setCommandLine(String commandLine);

    CommandUpdate withCommandLine(String commandLine);

    String getVisibility();

    void setVisibility(String visibility);

    CommandUpdate withVisibility(String visibility);

    String getType();

    void setType(String type);

    CommandUpdate withType(String type);

    String getWorkingDir();

    void setWorkingDir(String workingDir);

    CommandUpdate withWorkingDir(String workingDir);
}
