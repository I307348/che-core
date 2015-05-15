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

import com.google.common.collect.FluentIterable;
import com.jayway.restassured.response.Response;

import org.eclipse.che.api.core.rest.ApiExceptionMapper;
import org.eclipse.che.api.core.rest.shared.dto.ServiceError;
import org.eclipse.che.api.machine.server.dao.CommandDao;
import org.eclipse.che.api.machine.shared.Command;
import org.eclipse.che.api.machine.shared.dto.CommandDescriptor;
import org.eclipse.che.api.machine.shared.dto.NewCommand;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.user.UserImpl;
import org.eclipse.che.dto.server.DtoFactory;
import org.everrest.assured.EverrestJetty;
import org.everrest.core.Filter;
import org.everrest.core.GenericContainerRequest;
import org.everrest.core.RequestFilter;
import org.everrest.core.impl.uri.UriBuilderImpl;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

import static com.jayway.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_PASSWORD;
import static org.everrest.assured.JettyHttpServer.SECURE_PATH;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_NAME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * @author Eugene Voevodin
 */
@Listeners(value = {EverrestJetty.class, MockitoTestNGListener.class})
public class CommandServiceTest {

    @SuppressWarnings("unused")
    static final EnvironmentFilter  FILTER  = new EnvironmentFilter();
    @SuppressWarnings("unused")
    static final ApiExceptionMapper MAPPER  = new ApiExceptionMapper();
    static final String             USER_ID = "user123";
    static final LinkedList<String> ROLES   = new LinkedList<>(singletonList("user"));

    @Mock
    CommandDao     commandDao;
    @Mock
    UriInfo        uriInfo;
    @InjectMocks
    CommandService service;

    private static <T> T newDto(Class<T> clazz) {
        return DtoFactory.getInstance().createDto(clazz);
    }

    private static <T> T unwrapDto(Response response, Class<T> dtoClass) {
        return DtoFactory.getInstance().createDtoFromJson(response.body().print(), dtoClass);
    }

    private static <T> List<T> unwrapDtoList(Response response, Class<T> dtoClass) {
        return FluentIterable.from(DtoFactory.getInstance().createListDtoFromJson(response.body().print(), dtoClass)).toList();
    }

    @BeforeMethod
    public void setUpUriInfo() throws NoSuchFieldException, IllegalAccessException {
        when(uriInfo.getBaseUriBuilder()).thenReturn(new UriBuilderImpl());

        final Field uriField = service.getClass()
                                      .getSuperclass()
                                      .getDeclaredField("uriInfo");
        uriField.setAccessible(true);
        uriField.set(service, uriInfo);
    }

    @Test
    public void shouldThrowForbiddenExceptionOnCreateCommandWithNullBody() {
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .when()
                                         .post(SECURE_PATH + "/command/workspace123");

        assertEquals(response.getStatusCode(), 403);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Command required");
    }

    @Test
    public void shouldThrowForbiddenExceptionWithNewCommandWhichNameIsNull() {
        final NewCommand newCommand = newDto(NewCommand.class).withCommandLine("mvn clean install");

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newCommand)
                                         .when()
                                         .post(SECURE_PATH + "/command/workspace123");

        assertEquals(response.getStatusCode(), 403);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Command name required");
    }

    @Test
    public void shouldThrowForbiddenExceptionWithNewCommandWhichNameIsEmpty() {
        final NewCommand newCommand = newDto(NewCommand.class).withCommandLine("mvn clean install")
                                                              .withName("");

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newCommand)
                                         .when()
                                         .post(SECURE_PATH + "/command/workspace123");

        assertEquals(response.getStatusCode(), 403);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Command name required");
    }

    @Test
    public void shouldThrowForbiddenExceptionWithNewCommandWhichCommandLineIsNull() {
        final NewCommand newCommand = newDto(NewCommand.class).withName("MVN_CLEAN_INSTALL");

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newCommand)
                                         .when()
                                         .post(SECURE_PATH + "/command/workspace123");

        assertEquals(response.getStatusCode(), 403);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Command line required");
    }

    @Test
    public void shouldThrowForbiddenExceptionWithNewCommandWhichCommandLineIsEmpty() {
        final NewCommand newCommand = newDto(NewCommand.class).withName("MVN_CLEAN_INSTALL")
                                                              .withCommandLine("");

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newCommand)
                                         .when()
                                         .post(SECURE_PATH + "/command/workspace123");

        assertEquals(response.getStatusCode(), 403);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Command line required");
    }

    @Test
    public void shouldBeAbleToCreateNewCommand() throws Exception {
        final NewCommand newCommand = newDto(NewCommand.class).withName("MVN_CLEAN_INSTALL")
                                                              .withCommandLine("mvn clean install")
                                                              .withType("maven")
                                                              .withVisibility("public")
                                                              .withWorkingDir("working dir");

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newCommand)
                                         .when()
                                         .post(SECURE_PATH + "/command/workspace123");

        verify(commandDao).create(any(Command.class));
        final CommandDescriptor descriptor = unwrapDto(response, CommandDescriptor.class);
        assertNotNull(descriptor.getId());
        assertEquals(descriptor.getName(), newCommand.getName());
        assertEquals(descriptor.getCommandLine(), newCommand.getCommandLine());
        assertEquals(descriptor.getType(), newCommand.getType());
        assertEquals(descriptor.getVisibility(), newCommand.getVisibility());
        assertEquals(descriptor.getWorkingDir(), newCommand.getWorkingDir());
        assertEquals(descriptor.getWorkspaceId(), "workspace123");
        assertEquals(descriptor.getCreator(), USER_ID);
    }

    @Test
    public void shouldUsePrivateVisibilityAsDefaultWhenCreatingNewRecipeWithNullVisibility() throws Exception {
        final NewCommand newCommand = newDto(NewCommand.class).withName("MVN_CLEAN_INSTALL")
                                                              .withCommandLine("mvn clean install");
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newCommand)
                                         .when()
                                         .post(SECURE_PATH + "/command/workspace123");

        verify(commandDao).create(any(Command.class));
        assertEquals(unwrapDto(response, CommandDescriptor.class).getVisibility(), "private");
    }

    @Filter
    public static class EnvironmentFilter implements RequestFilter {

        public void doFilter(GenericContainerRequest request) {
            EnvironmentContext.getCurrent().setUser(new UserImpl("user", USER_ID, "token", ROLES, false));
        }
    }
}
