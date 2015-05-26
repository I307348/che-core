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
package org.eclipse.che.api.account.server;

import org.eclipse.che.api.account.server.dao.Account;
import org.eclipse.che.api.account.server.dao.AccountDao;
import org.eclipse.che.api.account.server.dao.Member;
import org.eclipse.che.api.account.server.dao.PlanDao;
import org.eclipse.che.api.account.server.dao.Subscription;
import org.eclipse.che.api.account.server.dao.SubscriptionDao;
import org.eclipse.che.api.account.shared.dto.BillingCycleType;
import org.eclipse.che.api.account.shared.dto.NewSubscription;
import org.eclipse.che.api.account.shared.dto.Plan;
import org.eclipse.che.api.account.shared.dto.SubscriptionDescriptor;
import org.eclipse.che.api.account.shared.dto.SubscriptionResourcesUsed;
import org.eclipse.che.api.account.shared.dto.SubscriptionState;
import org.eclipse.che.api.account.shared.dto.UpdateResourcesDescriptor;
import org.eclipse.che.api.account.shared.dto.UsedAccountResources;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.user.server.dao.User;
import org.eclipse.che.api.user.server.dao.UserDao;
import org.eclipse.che.commons.json.JsonHelper;
import org.eclipse.che.dto.server.DtoFactory;
import org.everrest.core.impl.ApplicationContextImpl;
import org.everrest.core.impl.ApplicationProviderBinder;
import org.everrest.core.impl.ContainerResponse;
import org.everrest.core.impl.EnvironmentContext;
import org.everrest.core.impl.EverrestConfiguration;
import org.everrest.core.impl.EverrestProcessor;
import org.everrest.core.impl.ProviderBinder;
import org.everrest.core.impl.ResourceBinderImpl;
import org.everrest.core.tools.DependencySupplierImpl;
import org.everrest.core.tools.ResourceLauncher;
import org.everrest.core.tools.SimplePrincipal;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertEqualsNoOrder;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link SubscriptionRestService}
 *
 * @author Sergii Leschenko
 * @author Alexander Garagatyi
 */
@Listeners(value = {MockitoTestNGListener.class})
public class SubscriptionRestServiceTest {
    private final String BASE_URI        = "http://localhost/service";
    private final String SERVICE_PATH    = BASE_URI + "/account";
    private final String USER_ID         = "user123abc456def";
    private final String ACCOUNT_ID      = "account0xffffffffff";
    private final String SUBSCRIPTION_ID = "subscription0xffffffffff";
    private final String ACCOUNT_NAME    = "codenvy";
    private final String SERVICE_ID      = "IDE_SERVICE";
    private final String USER_EMAIL      = "account@mail.com";
    private final String PLAN_ID         = "planId";
    private final User   user            = new User().withId(USER_ID).withEmail(USER_EMAIL);

    @Mock
    private AccountDao                  accountDao;
    @Mock
    private SubscriptionDao             subscriptionDao;
    @Mock
    private UserDao                     userDao;
    @Mock
    private PlanDao                     planDao;
    @Mock
    private ResourcesManager            resourcesManager;
    @Mock
    private SecurityContext             securityContext;
    @Mock
    private SubscriptionServiceRegistry serviceRegistry;
    @Mock
    private SubscriptionService         subscriptionService;
    @Mock
    private EnvironmentContext          environmentContext;

    private Account           account;
    private Plan              plan;
    private ArrayList<Member> memberships;
    private NewSubscription   newSubscription;

    protected ProviderBinder     providers;
    protected ResourceBinderImpl resources;
    protected ResourceLauncher   launcher;

    @BeforeMethod
    public void setUp() throws Exception {
        resources = new ResourceBinderImpl();
        providers = new ApplicationProviderBinder();

        DependencySupplierImpl dependencies = new DependencySupplierImpl();
        dependencies.addComponent(SubscriptionDao.class, subscriptionDao);
        dependencies.addComponent(PlanDao.class, planDao);
        dependencies.addComponent(AccountDao.class, accountDao);
        dependencies.addComponent(SubscriptionServiceRegistry.class, serviceRegistry);
        resources.addResource(SubscriptionRestService.class, null);
        EverrestProcessor processor = new EverrestProcessor(resources, providers, dependencies, new EverrestConfiguration(), null);
        launcher = new ResourceLauncher(processor);
        ApplicationContextImpl.setCurrent(new ApplicationContextImpl(null, null, ProviderBinder.getInstance()));
        Map<String, String> attributes = new HashMap<>();
        attributes.put("secret", "bit secret");
        account = new Account().withId(ACCOUNT_ID)
                               .withName(ACCOUNT_NAME)
                               .withAttributes(attributes);

        plan = DtoFactory.getInstance().createDto(Plan.class)
                         .withId(PLAN_ID)
                         .withPaid(true)
                         .withSalesOnly(false)
                         .withServiceId(SERVICE_ID)
                         .withProperties(Collections.singletonMap("key", "value"))
                         .withBillingContractTerm(12)
                         .withBillingCycle(1)
                         .withBillingCycleType(BillingCycleType.AutoRenew)
                         .withDescription("description")
                         .withTrialDuration(7);

        memberships = new ArrayList<>(1);
        Member ownerMembership = new Member();
        ownerMembership.setAccountId(account.getId());
        ownerMembership.setUserId(USER_ID);
        ownerMembership.setRoles(Arrays.asList("account/owner"));
        memberships.add(ownerMembership);

        newSubscription = DtoFactory.getInstance().createDto(NewSubscription.class)
                                    .withAccountId(ACCOUNT_ID)
                                    .withPlanId(PLAN_ID)
                                    .withTrialDuration(7)
                                    .withUsePaymentSystem(true);

        when(environmentContext.get(SecurityContext.class)).thenReturn(securityContext);
        when(securityContext.getUserPrincipal()).thenReturn(new SimplePrincipal(USER_EMAIL));

        org.eclipse.che.commons.env.EnvironmentContext.getCurrent().setUser(new org.eclipse.che.commons.user.User() {
            @Override
            public String getName() {
                return user.getEmail();
            }

            @Override
            public boolean isMemberOf(String role) {
                return false;
            }

            @Override
            public String getToken() {
                return "token";
            }

            @Override
            public String getId() {
                return user.getId();
            }

            @Override
            public boolean isTemporary() {
                return false;
            }
        });
    }

    @AfterMethod
    public void tearDown() throws Exception {
        org.eclipse.che.commons.env.EnvironmentContext.reset();
    }


    @Test
    public void shouldBeAbleToGetSubscriptionsOfSpecificAccount() throws Exception {
        Subscription expectedSubscription = createSubscription();
        when(subscriptionDao.getActiveSubscriptions(ACCOUNT_ID)).thenReturn(Arrays.asList(expectedSubscription));
        prepareSecurityContext("system/admin");

        ContainerResponse response = makeRequest(HttpMethod.GET, SERVICE_PATH + "/" + ACCOUNT_ID + "/subscriptions", null, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        @SuppressWarnings("unchecked") List<SubscriptionDescriptor> subscriptions = (List<SubscriptionDescriptor>)response.getEntity();
        for (SubscriptionDescriptor subscription : subscriptions) {
            subscription.setLinks(null);
        }
        assertEquals(subscriptions, Collections.singletonList(convertToDescriptor(expectedSubscription)));
        verify(subscriptionDao).getActiveSubscriptions(ACCOUNT_ID);
    }

    @Test
    public void shouldBeAbleToGetSubscriptionsOfSpecificAccountWithSpecifiedServiceId() throws Exception {
        Subscription subscription = createSubscription();
        when(subscriptionDao.getActiveSubscription(ACCOUNT_ID, SERVICE_ID)).thenReturn(subscription);
        prepareSecurityContext("system/admin");

        ContainerResponse response =
                makeRequest(HttpMethod.GET, SERVICE_PATH + "/" + ACCOUNT_ID + "/subscriptions?service=" + SERVICE_ID, null, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        @SuppressWarnings("unchecked") List<SubscriptionDescriptor> subscriptions = (List<SubscriptionDescriptor>)response.getEntity();
        for (SubscriptionDescriptor subscriptionDescriptor : subscriptions) {
            subscriptionDescriptor.setLinks(null);
        }
        assertEquals(subscriptions, Collections.singletonList(convertToDescriptor(subscription)));
        verify(subscriptionDao).getActiveSubscription(ACCOUNT_ID, SERVICE_ID);
    }

    @Test
    public void shouldReturnNoSubscriptionIfThereIsNoSubscriptionWithGivenServiceIdOnGetSubscriptions() throws Exception {
        when(subscriptionDao.getActiveSubscription(ACCOUNT_ID, SERVICE_ID)).thenReturn(null);
        prepareSecurityContext("system/admin");

        ContainerResponse response =
                makeRequest(HttpMethod.GET, SERVICE_PATH + "/" + ACCOUNT_ID + "/subscriptions?service=" + SERVICE_ID, null, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        @SuppressWarnings("unchecked") List<SubscriptionDescriptor> subscriptions = (List<SubscriptionDescriptor>)response.getEntity();
        assertEquals(subscriptions.size(), 0);
        verify(subscriptionDao).getActiveSubscription(ACCOUNT_ID, SERVICE_ID);
    }

    @Test
    public void shouldBeAbleToGetSpecificSubscriptionBySystemAdmin() throws Exception {
        Subscription expectedSubscription = createSubscription();
        when(subscriptionDao.getSubscriptionById(SUBSCRIPTION_ID)).thenReturn(expectedSubscription);
        prepareSecurityContext("system/admin");

        ContainerResponse response = makeRequest(HttpMethod.GET, SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID, null, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        SubscriptionDescriptor subscription = (SubscriptionDescriptor)response.getEntity();
        assertEquals(subscription.withLinks(null), convertToDescriptor(expectedSubscription));
        verify(subscriptionDao).getSubscriptionById(SUBSCRIPTION_ID);
    }

    @Test
    public void shouldBeAbleToGetSpecificSubscriptionByAccountOwner() throws Exception {
        Subscription expectedSubscription = createSubscription().withAccountId("ANOTHER_ACCOUNT_ID");
        when(subscriptionDao.getSubscriptionById(SUBSCRIPTION_ID)).thenReturn(expectedSubscription);
        when(accountDao.getByMember(USER_ID)).thenReturn(Arrays.asList(new Member().withRoles(Arrays.asList("account/owner"))
                                                                                   .withAccountId("ANOTHER_ACCOUNT_ID")
                                                                                   .withUserId(USER_ID)));
        prepareSecurityContext("user");

        ContainerResponse response = makeRequest(HttpMethod.GET, SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID, null, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        SubscriptionDescriptor subscription = (SubscriptionDescriptor)response.getEntity();
        assertEquals(subscription.withLinks(null), convertToDescriptor(expectedSubscription));
        verify(subscriptionDao).getSubscriptionById(SUBSCRIPTION_ID);
    }

    @Test
    public void shouldBeAbleToGetSpecificSubscriptionByAccountMember() throws Exception {
        Subscription expectedSubscription = createSubscription().withAccountId("ANOTHER_ACCOUNT_ID");
        when(subscriptionDao.getSubscriptionById(SUBSCRIPTION_ID)).thenReturn(expectedSubscription);
        when(accountDao.getByMember(USER_ID)).thenReturn(Arrays.asList(new Member().withRoles(Arrays.asList("account/member"))
                                                                                   .withAccountId("ANOTHER_ACCOUNT_ID")
                                                                                   .withUserId(USER_ID)));
        prepareSecurityContext("user");

        ContainerResponse response = makeRequest(HttpMethod.GET, SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID, null, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        SubscriptionDescriptor subscription = (SubscriptionDescriptor)response.getEntity();
        assertEquals(subscription.withLinks(null), convertToDescriptor(expectedSubscription));
        verify(subscriptionDao).getSubscriptionById(SUBSCRIPTION_ID);
    }

    @Test
    public void shouldRespondForbiddenIfUserIsNotMemberOrOwnerOfAccountOnGetSubscriptionById() throws Exception {
        ArrayList<Member> memberships = new ArrayList<>();
        Member am = new Member();
        am.withRoles(Arrays.asList("account/owner")).withAccountId("fake_id");
        memberships.add(am);

        when(accountDao.getByMember(USER_ID)).thenReturn(memberships);
        when(subscriptionDao.getSubscriptionById(SUBSCRIPTION_ID)).thenReturn(createSubscription());
        prepareSecurityContext("user");

        ContainerResponse response = makeRequest(HttpMethod.GET, SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID, null, null);

        assertNotEquals(Response.Status.OK, response.getStatus());
        assertEquals(response.getEntity(), "Access denied");
    }

    @Test
    public void shouldNotAddSubscriptionIfBeforeAddSubscriptionValidationFails() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        doThrow(new ConflictException("conflict")).when(subscriptionService).beforeCreateSubscription(any(Subscription.class));

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertNotEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertEquals(response.getEntity(), "conflict");
        verify(subscriptionDao, never()).addSubscription(any(Subscription.class));
        verify(subscriptionService, never()).afterCreateSubscription(any(Subscription.class));
    }

    @Test
    public void shouldBeAbleToAddSubscriptionWithoutTrialAndCharge() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        newSubscription.setTrialDuration(0);

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        SubscriptionDescriptor subscription = (SubscriptionDescriptor)response.getEntity();
        assertEquals(subscription.getAccountId(), ACCOUNT_ID);
        assertEquals(subscription.getPlanId(), PLAN_ID);
        assertEquals(subscription.getServiceId(), SERVICE_ID);
        assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(subscription.getBillingCycleType(), plan.getBillingCycleType());
        assertEquals(subscription.getBillingCycle(), plan.getBillingCycle());
        assertEquals(subscription.getBillingContractTerm(), plan.getBillingContractTerm());
        assertEquals(subscription.getDescription(), plan.getDescription());
        assertEquals(subscription.getProperties(), plan.getProperties());
        assertTrue(subscription.getUsePaymentSystem());

        assertNotNull(subscription.getId());
        assertNull(subscription.getTrialStartDate());
        assertNull(subscription.getTrialEndDate());

        verify(subscriptionDao).addSubscription(argThat(new ArgumentMatcher<Subscription>() {
            @Override
            public boolean matches(Object argument) {
                Subscription actual = (Subscription)argument;

                assertEquals(actual.getAccountId(), ACCOUNT_ID);
                assertEquals(actual.getPlanId(), PLAN_ID);
                assertEquals(actual.getServiceId(), SERVICE_ID);
                assertEquals(actual.getState(), SubscriptionState.ACTIVE);
                assertEquals(actual.getBillingCycleType(), plan.getBillingCycleType());
                assertEquals(actual.getBillingCycle(), plan.getBillingCycle());
                assertEquals(actual.getBillingContractTerm(), plan.getBillingContractTerm());
                assertEquals(actual.getDescription(), plan.getDescription());
                assertEquals(actual.getProperties(), plan.getProperties());
                assertTrue(actual.getUsePaymentSystem());

                assertNotNull(actual.getId());
                assertNull(actual.getTrialStartDate());
                assertNull(actual.getTrialEndDate());

                return true;
            }
        }));
        verify(serviceRegistry).get(SERVICE_ID);
        verify(subscriptionService).beforeCreateSubscription(any(Subscription.class));
        verify(subscriptionService).afterCreateSubscription(any(Subscription.class));
    }

    @Test
    public void shouldBeAbleToAddSubscriptionWithTrial() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        SubscriptionDescriptor subscription = (SubscriptionDescriptor)response.getEntity();
        assertEquals(subscription.getAccountId(), ACCOUNT_ID);
        assertEquals(subscription.getPlanId(), PLAN_ID);
        assertEquals(subscription.getServiceId(), SERVICE_ID);
        assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
        assertEquals(subscription.getBillingCycleType(), plan.getBillingCycleType());
        assertEquals(subscription.getBillingCycle(), plan.getBillingCycle());
        assertEquals(subscription.getBillingContractTerm(), plan.getBillingContractTerm());
        assertEquals(subscription.getDescription(), plan.getDescription());
        assertEquals(subscription.getProperties(), plan.getProperties());
        assertTrue(subscription.getUsePaymentSystem());

        assertNotNull(subscription.getId());
        assertNull(subscription.getStartDate());
        assertNull(subscription.getEndDate());
        assertNull(subscription.getBillingStartDate());
        assertNull(subscription.getBillingEndDate());
        assertNull(subscription.getNextBillingDate());
        assertNotNull(subscription.getTrialStartDate());
        assertNotNull(subscription.getTrialEndDate());

        verify(subscriptionDao).addSubscription(argThat(new ArgumentMatcher<Subscription>() {
            @Override
            public boolean matches(Object argument) {
                Subscription actual = (Subscription)argument;

                assertEquals(actual.getAccountId(), ACCOUNT_ID);
                assertEquals(actual.getPlanId(), PLAN_ID);
                assertEquals(actual.getServiceId(), SERVICE_ID);
                assertEquals(actual.getState(), SubscriptionState.ACTIVE);
                assertEquals(actual.getBillingCycleType(), plan.getBillingCycleType());
                assertEquals(actual.getBillingCycle(), plan.getBillingCycle());
                assertEquals(actual.getBillingContractTerm(), plan.getBillingContractTerm());
                assertEquals(actual.getDescription(), plan.getDescription());
                assertEquals(actual.getProperties(), plan.getProperties());
                assertTrue(actual.getUsePaymentSystem());

                assertNotNull(actual.getId());
                assertNull(actual.getStartDate());
                assertNull(actual.getEndDate());
                assertNull(actual.getBillingStartDate());
                assertNull(actual.getBillingEndDate());
                assertNull(actual.getNextBillingDate());
                assertNotNull(actual.getTrialStartDate());
                assertNotNull(actual.getTrialEndDate());

                return true;
            }
        }));
        verify(serviceRegistry).get(SERVICE_ID);
        verify(subscriptionService).beforeCreateSubscription(any(Subscription.class));
        verify(subscriptionService).afterCreateSubscription(any(Subscription.class));
    }

    @Test
    public void shouldBeAbleToAddSubscriptionWithoutChargingIfUsePaymentSystemSetToFalseAndUserIsSystemAdmin() throws Exception {
        prepareSuccessfulSubscriptionAddition();
        newSubscription.setTrialDuration(0);

        newSubscription.setUsePaymentSystem(false);

        prepareSecurityContext("system/admin");

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        verify(subscriptionDao).addSubscription(any(Subscription.class));
    }

    @Test
    public void shouldBeAbleToAddSubscriptionWithoutChargingIfSubscriptionIsNotPaid() throws Exception {
        prepareSuccessfulSubscriptionAddition();
        plan.setPaid(false);
        newSubscription.setUsePaymentSystem(false);

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        verify(subscriptionDao).addSubscription(any(Subscription.class));
    }

    @Test
    public void shouldRespondNotFoundIfSubscriptionIsNotFoundOnRemoveSubscription() throws Exception {
        when(subscriptionDao.getSubscriptionById(SUBSCRIPTION_ID)).thenThrow(new NotFoundException("subscription not found"));

        prepareSecurityContext("system/admin");

        ContainerResponse response =
                makeRequest(HttpMethod.DELETE, SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID, null, null);

        assertNotEquals(response.getStatus(), Response.Status.OK);
        assertEquals(response.getEntity(), "subscription not found");
        verify(subscriptionDao, never()).removeSubscription(anyString());
        verify(subscriptionDao, never()).updateSubscription(any(Subscription.class));
    }

    @Test
    public void shouldRespondAccessDeniedIfUserIsNotAccountOwnerOnRemoveSubscription() throws Exception {
        ArrayList<Member> memberships = new ArrayList<>(2);
        Member am = new Member().withRoles(Arrays.asList("account/owner"))
                                .withAccountId("fake_id");
        memberships.add(am);
        Member am2 = new Member().withRoles(Arrays.asList("account/member"))
                                 .withAccountId(ACCOUNT_ID);
        memberships.add(am2);

        when(accountDao.getByMember(USER_ID)).thenReturn(memberships);
        when(serviceRegistry.get(SERVICE_ID)).thenReturn(subscriptionService);
        when(subscriptionDao.getSubscriptionById(SUBSCRIPTION_ID)).thenReturn(createSubscription());
        prepareSecurityContext("user");

        ContainerResponse response =
                makeRequest(HttpMethod.DELETE, SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID, null, null);

        assertNotEquals(response.getStatus(), Response.Status.OK);
        assertEquals(response.getEntity(), "Access denied");
        verify(subscriptionDao, never()).removeSubscription(anyString());
        verify(subscriptionDao, never()).updateSubscription(any(Subscription.class));
    }

    @Test
    public void shouldBeAbleToRemoveSubscriptionBySystemAdmin() throws Exception {
        when(serviceRegistry.get(SERVICE_ID)).thenReturn(subscriptionService);
        final Subscription subscription = createSubscription();
        when(subscriptionDao.getSubscriptionById(SUBSCRIPTION_ID)).thenReturn(subscription);
        prepareSecurityContext("system/admin");

        ContainerResponse response =
                makeRequest(HttpMethod.DELETE, SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID, null, null);

        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        verify(serviceRegistry).get(SERVICE_ID);
        verify(subscriptionService).onRemoveSubscription(any(Subscription.class));
        verify(subscriptionDao, never()).removeSubscription(anyString());
        verify(subscriptionDao).updateSubscription(argThat(new ArgumentMatcher<Subscription>() {
            @Override
            public boolean matches(Object argument) {
                Subscription actual = (Subscription)argument;

                assertEquals(actual, new Subscription(subscription).withState(SubscriptionState.INACTIVE));
                return true;
            }
        }));
    }

    @Test
    public void shouldBeAbleToRemoveSubscriptionByAccountOwner() throws Exception {
        when(accountDao.getByMember(USER_ID)).thenReturn(memberships);
        when(serviceRegistry.get(SERVICE_ID)).thenReturn(subscriptionService);
        final Subscription subscription = createSubscription();
        when(subscriptionDao.getSubscriptionById(SUBSCRIPTION_ID)).thenReturn(subscription);
        prepareSecurityContext("user");

        ContainerResponse response =
                makeRequest(HttpMethod.DELETE, SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID, null, null);

        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        verify(serviceRegistry).get(SERVICE_ID);
        verify(subscriptionService).onRemoveSubscription(any(Subscription.class));
        verify(subscriptionDao, never()).removeSubscription(anyString());
        verify(subscriptionDao).updateSubscription(argThat(new ArgumentMatcher<Subscription>() {
            @Override
            public boolean matches(Object argument) {
                Subscription actual = (Subscription)argument;

                assertEquals(actual, new Subscription(subscription).withState(SubscriptionState.INACTIVE));
                return true;
            }
        }));
    }

    @Test
    public void shouldRespondForbiddenIfSubscriptionIsInactiveOnRemoveSubscription() throws Exception {
        when(accountDao.getByMember(USER_ID)).thenReturn(memberships);
        when(serviceRegistry.get(SERVICE_ID)).thenReturn(subscriptionService);
        Subscription subscription = createSubscription().withState(SubscriptionState.INACTIVE);

        when(subscriptionDao.getSubscriptionById(SUBSCRIPTION_ID)).thenReturn(subscription);
        prepareSecurityContext("user");

        ContainerResponse response =
                makeRequest(HttpMethod.DELETE, SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID, null, null);

        assertNotEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        assertEquals(response.getEntity(), "Subscription is inactive already " + subscription.getId());

        verify(subscriptionDao, never()).updateSubscription(any(Subscription.class));
        verify(subscriptionDao, never()).removeSubscription(anyString());
    }

    @Test
    public void shouldBeAbleToConvertSubscriptionToDescriptor() throws Exception {
        Map<String, String> properties = new HashMap<>();
        properties.put("codenvy:property", "value");
        properties.put("someproperty", "value");
        properties.put("codenvyProperty", "value");
        properties.put("codenvy:", "value");
        Subscription subscription = createSubscription().withProperties(properties);
        SubscriptionDescriptor expectedDescriptor = convertToDescriptor(subscription);
        Link[] expectedLinks = new Link[2];
        expectedLinks[0] = (DtoFactory.getInstance().createDto(Link.class)
                                      .withRel(Constants.LINK_REL_REMOVE_SUBSCRIPTION)
                                      .withMethod(HttpMethod.DELETE)
                                      .withHref(SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID));
        expectedLinks[1] = (DtoFactory.getInstance().createDto(Link.class)
                                      .withRel(Constants.LINK_REL_GET_SUBSCRIPTION)
                                      .withMethod(HttpMethod.GET)
                                      .withHref(SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID)
                                      .withProduces(MediaType.APPLICATION_JSON));

        prepareSecurityContext("system/admin");

        SubscriptionDescriptor descriptor = getDescriptor(subscription);

        assertEqualsNoOrder(descriptor.getLinks().toArray(), expectedLinks);
        assertEquals(descriptor.withLinks(null), expectedDescriptor);
    }

    @Test
    public void shouldBeAbleToGetAccountResources() throws Exception {
        when(subscriptionService.getAccountResources((Subscription)anyObject()))
                .thenReturn(DtoFactory.getInstance().createDto(UsedAccountResources.class));
        when(serviceRegistry.getAll()).thenReturn(new HashSet<>(Arrays.asList(subscriptionService)));
        when(subscriptionDao.getActiveSubscription(anyString(), anyString()))
                .thenReturn(new Subscription().withId("subscriptionId"));

        ContainerResponse response = makeRequest(HttpMethod.GET, SERVICE_PATH + "/" + account.getId() + "/resources", null, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        @SuppressWarnings("unchecked")
        List<SubscriptionResourcesUsed> result = (List<SubscriptionResourcesUsed>)response.getEntity();

        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getSubscriptionReference().getSubscriptionId(), "subscriptionId");
        verify(subscriptionDao).getActiveSubscription(anyString(), anyString());
        verify(subscriptionService).getAccountResources((Subscription)anyObject());
    }

    @Test
    public void shouldBeAbleToGetAccountResourcesByServiceId() throws Exception {
        when(subscriptionService.getAccountResources((Subscription)anyObject()))
                .thenReturn(DtoFactory.getInstance().createDto(UsedAccountResources.class));

        when(serviceRegistry.get(anyString())).thenReturn(subscriptionService);

        when(subscriptionDao.getActiveSubscription(anyString(), anyString()))
                .thenReturn(new Subscription().withId("subscriptionId"));

        ContainerResponse response =
                makeRequest(HttpMethod.GET, SERVICE_PATH + "/" + account.getId() + "/resources?serviceId=Saas", null, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        @SuppressWarnings("unchecked")
        List<SubscriptionResourcesUsed> result = (List<SubscriptionResourcesUsed>)response.getEntity();

        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getSubscriptionReference().getSubscriptionId(), "subscriptionId");
        verify(subscriptionDao).getActiveSubscription(anyString(), anyString());
        verify(subscriptionService).getAccountResources((Subscription)anyObject());
    }


    @Test
    public void shouldNotBeAbleToAddSubscriptionIfNoDataSent() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, null);

        assertEquals(response.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        assertEquals(response.getEntity().toString(), "New subscription required");
    }

    @Test
    public void shouldNotBeAbleToAddSubscriptionIfAccountIdIsNotSent() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON,
                            newSubscription.withAccountId(null));

        assertEquals(response.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        assertEquals(response.getEntity().toString(), "Account identifier required");
    }

    @Test
    public void shouldNotBeAbleToAddSubscriptionIfPlanIdIsNotSent() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription.withPlanId(null));

        assertEquals(response.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        assertEquals(response.getEntity().toString(), "Plan identifier required");
    }

    @Test
    public void shouldRespondAccessDeniedIfUserIsNotAccountOwnerOnAddSubscription() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        ArrayList<Member> memberships = new ArrayList<>(2);
        Member am = new Member();
        am.withRoles(Arrays.asList("account/owner")).withAccountId("fake_id");
        memberships.add(am);
        Member am2 = new Member();
        am2.withRoles(Arrays.asList("account/member")).withAccountId(ACCOUNT_ID);
        memberships.add(am2);

        when(accountDao.getByMember(USER_ID)).thenReturn(memberships);

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertNotEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertEquals(response.getEntity(), "Access denied");
    }

    @Test
    public void shouldRespondForbiddenIfUserTryOverrideSubscriptionAttributes() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        newSubscription.getProperties().put("key", "123");
        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertNotEquals(response.getStatus(), Response.Status.FORBIDDEN.getStatusCode());
        assertEquals(response.getEntity(), "User not authorized to add subscription with custom properties, please contact support");
    }

    @Test
    public void shouldRespondForbiddenIfAdminTryOverrideNonExistentSubscriptionAttributes() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        prepareSecurityContext("system/admin");

        newSubscription.getProperties().put("nonExistentKey", "123");
        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertNotEquals(response.getStatus(), Response.Status.FORBIDDEN.getStatusCode());
        assertEquals(response.getEntity(), "Forbidden overriding of non-existent plan properties");
    }

    @Test
    public void shouldBeAbleToAddSubscriptionWithCustomAttributes() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        prepareSecurityContext("system/admin");

        Map<String, String> properties = new HashMap<>();
        properties.put("custom", "0");
        plan.setProperties(properties);
        when(planDao.getPlanById(PLAN_ID)).thenReturn(plan);

        newSubscription.getProperties().put("custom", "123");
        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        SubscriptionDescriptor subscription = (SubscriptionDescriptor)response.getEntity();
        assertEquals(subscription.getProperties().get("custom"), "123");
    }

    @Test
    public void shouldRespondNotFoundIfPlanNotFoundOnAddSubscription() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        when(planDao.getPlanById(PLAN_ID)).thenThrow(new NotFoundException("Plan not found"));

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertNotEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertEquals(response.getEntity(), "Plan not found");
    }

    @Test
    public void shouldRespondForbiddenIfUserTriesToAddSubsWithTrialNotEqualToTheTrialInPlan() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        newSubscription.setTrialDuration(5);

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertNotEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertEquals(response.getEntity(), "User not authorized to add this subscription, please contact support");
    }

    @Test
    public void shouldAllowUseZeroLengthTrialIfTrialInPlanInNotZeroOnAddSubscription() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        newSubscription.setTrialDuration(0);

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertNotEquals(response.getEntity(), "User not authorized to add this subscription, please contact support");
        verify(planDao).getPlanById(PLAN_ID);
    }

    @Test
    public void shouldRespondConflictIfServiceIdIsUnknownOnAddSubscription() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        when(serviceRegistry.get(SERVICE_ID)).thenReturn(null);

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertNotEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertEquals(response.getEntity(), "Unknown serviceId is used");
    }

    @Test
    public void shouldRespondConflictIfIfUsePaymentSystemSetToFalseAndUserIsNotSystemAdminOnAddSubscription() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        newSubscription.setUsePaymentSystem(false);

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertNotEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertEquals(response.getEntity(), "Given value of attribute usePaymentSystem is not allowed");
        verify(accountDao).getByMember(USER_ID);
        verifyNoMoreInteractions(accountDao);
    }

    @Test
    public void shouldRespondConflictIfPlanIsForSalesOnlyAndUserIsNotSystemAdminOnAddSubscription() throws Exception {
        prepareSuccessfulSubscriptionAddition();

        plan.setSalesOnly(true);

        ContainerResponse response =
                makeRequest(HttpMethod.POST, SERVICE_PATH + "/subscriptions", MediaType.APPLICATION_JSON, newSubscription);

        assertNotEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertEquals(response.getEntity(), "User not authorized to add this subscription, please contact support");
        verify(accountDao).getByMember(USER_ID);
        verifyNoMoreInteractions(accountDao);
    }

    @Test
    public void shouldNotAddLinksToSubscriptionDescriptorIfSubscriptionIsCommunity() throws Exception {
        Subscription subscription = createSubscription().withPlanId("sas-community");
        prepareSecurityContext("system/admin");

        SubscriptionDescriptor descriptor = getDescriptor(subscription);

        assertTrue(descriptor.getLinks().isEmpty());
    }

    @Test
    public void shouldAddGetByIdLinkOnlyToSubscriptionDescriptorIfSubscriptionIsInactive() throws Exception {
        List<Link> expectedLinks = new ArrayList<>();
        expectedLinks.add(DtoFactory.getInstance().createDto(Link.class)
                                    .withRel(Constants.LINK_REL_GET_SUBSCRIPTION)
                                    .withMethod(HttpMethod.GET)
                                    .withHref(SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID)
                                    .withProduces(MediaType.APPLICATION_JSON));
        Subscription subscription = createSubscription().withState(SubscriptionState.INACTIVE);

        prepareSecurityContext("system/admin");

        SubscriptionDescriptor descriptor = getDescriptor(subscription);

        assertEquals(descriptor.getLinks(), expectedLinks);
    }

    @Test
    public void shouldNotAddDeleteLinkToSubscriptionDescriptorIfUserHasNotRights() throws Exception {
        List<Link> expectedLinks = new ArrayList<>();
        expectedLinks.add(DtoFactory.getInstance().createDto(Link.class)
                                    .withRel(Constants.LINK_REL_GET_SUBSCRIPTION)
                                    .withMethod(HttpMethod.GET)
                                    .withHref(SERVICE_PATH + "/subscriptions/" + SUBSCRIPTION_ID)
                                    .withProduces(MediaType.APPLICATION_JSON));

        prepareSecurityContext("user");

        SubscriptionDescriptor descriptor = getDescriptor(createSubscription());

        assertEquals(descriptor.getLinks(), expectedLinks);
    }

    @Test
    public void shouldNotAddCodenvyPropertiesInSubscriptionDescriptorIfUserIsNotSystemAdminOrManager() throws Exception {
        Map<String, String> properties = new HashMap<>();
        properties.put("codenvy:property", "value");
        properties.put("someproperty", "value");
        properties.put("codenvyProperty", "value");
        properties.put("codenvy:", "value");
        Subscription subscription = createSubscription().withProperties(properties);

        prepareSecurityContext("user");

        SubscriptionDescriptor descriptor = getDescriptor(subscription);

        for (String property : descriptor.getProperties().keySet()) {
            assertFalse(property.startsWith("codenvy:"));
        }
    }

    @Test
    public void shouldBeAbleToReturnDescriptorWithNullDates() throws Exception {
        Subscription subscription = createSubscription()
                .withStartDate(null)
                .withEndDate(null)
                .withTrialStartDate(null)
                .withTrialEndDate(null)
                .withBillingStartDate(null)
                .withBillingEndDate(null)
                .withNextBillingDate(null);

        SubscriptionDescriptor descriptor = getDescriptor(subscription);

        assertEquals(descriptor.withLinks(null), convertToDescriptor(subscription));
    }

    @Test
    public void shouldThrowExceptionWhenServiceWithRequiredIdNotFound() throws Exception {
        ContainerResponse response =
                makeRequest(HttpMethod.GET, SERVICE_PATH + "/" + ACCOUNT_ID + "/resources?serviceId=invalidId", null, null);

        assertEquals(response.getStatus(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        assertEquals(response.getEntity().toString(), "Unknown serviceId is used");
    }

    private Subscription createSubscription() {
        final Map<String, String> properties = new HashMap<>();
        properties.put("RAM", "2048");
        properties.put("Package", "Developer");
        return new Subscription()
                .withId(SUBSCRIPTION_ID)
                .withAccountId(ACCOUNT_ID)
                .withPlanId(PLAN_ID)
                .withServiceId(SERVICE_ID)
                .withProperties(properties)
                .withState(SubscriptionState.ACTIVE)
                .withDescription("description")
                .withUsePaymentSystem(true)
                .withStartDate(new Date())
                .withEndDate(new Date())
                .withTrialStartDate(new Date())
                .withTrialEndDate(new Date())
                .withBillingStartDate(new Date())
                .withNextBillingDate(new Date())
                .withBillingEndDate(new Date())
                .withBillingContractTerm(12)
                .withBillingCycleType(BillingCycleType.AutoRenew)
                .withBillingCycle(1);
    }

    private void prepareSuccessfulSubscriptionAddition() throws NotFoundException, ServerException {
        when(serviceRegistry.get(SERVICE_ID)).thenReturn(subscriptionService);
        when(planDao.getPlanById(PLAN_ID)).thenReturn(plan);
        when(accountDao.getByMember(USER_ID)).thenReturn(Arrays.asList(new Member().withRoles(Arrays.asList("account/owner"))
                                                                                   .withAccountId(ACCOUNT_ID)
                                                                                   .withUserId(USER_ID)));
        prepareSecurityContext("user");
    }

    private SubscriptionDescriptor getDescriptor(Subscription subscription) throws Exception {
        when(subscriptionDao.getActiveSubscriptions(ACCOUNT_ID)).thenReturn(Arrays.asList(subscription));

        ContainerResponse response = makeRequest(HttpMethod.GET, SERVICE_PATH + "/" + ACCOUNT_ID + "/subscriptions", null, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        @SuppressWarnings("unchecked") List<SubscriptionDescriptor> subscriptionDescriptors =
                (List<SubscriptionDescriptor>)response.getEntity();
        assertEquals(subscriptionDescriptors.size(), 1);
        return subscriptionDescriptors.get(0);
    }

    protected void prepareSecurityContext(String role) {
        when(securityContext.isUserInRole(anyString())).thenReturn(false);
        if (!role.equals("system/admin") && !role.equals("system/manager")) {
            when(securityContext.isUserInRole("user")).thenReturn(true);
        }
        when(securityContext.isUserInRole(role)).thenReturn(true);
    }

    private SubscriptionDescriptor convertToDescriptor(Subscription subscription) {
        return DtoFactory.getInstance().createDto(SubscriptionDescriptor.class)
                         .withId(subscription.getId())
                         .withAccountId(subscription.getAccountId())
                         .withServiceId(subscription.getServiceId())
                         .withPlanId(subscription.getPlanId())
                         .withProperties(subscription.getProperties())
                         .withState(subscription.getState())
                         .withDescription(subscription.getDescription())
                         .withUsePaymentSystem(subscription.getUsePaymentSystem())
                         .withStartDate(dateToString(subscription.getStartDate()))
                         .withEndDate(dateToString(subscription.getEndDate()))
                         .withTrialStartDate(dateToString(subscription.getTrialStartDate()))
                         .withTrialEndDate(dateToString(subscription.getTrialEndDate()))
                         .withBillingStartDate(dateToString(subscription.getBillingStartDate()))
                         .withNextBillingDate(dateToString(subscription.getNextBillingDate()))
                         .withBillingEndDate(dateToString(subscription.getBillingEndDate()))
                         .withBillingContractTerm(subscription.getBillingContractTerm())
                         .withBillingCycleType(subscription.getBillingCycleType())
                         .withBillingCycle(subscription.getBillingCycle());
    }

    protected ContainerResponse makeRequest(String method, String path, String contentType, Object toSend) throws Exception {
        Map<String, List<String>> headers = null;
        if (contentType != null) {
            headers = new HashMap<>();
            headers.put("Content-Type", Arrays.asList(contentType));
        }
        byte[] data = null;
        if (toSend != null) {
            data = JsonHelper.toJson(toSend).getBytes();
        }
        return launcher.service(method, path, BASE_URI, headers, data, null, environmentContext);
    }

    private String dateToString(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        dateFormat.setLenient(false);

        return null == date ? null : dateFormat.format(date);
    }
}