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

import com.google.common.annotations.Beta;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import org.eclipse.che.api.account.server.dao.AccountDao;
import org.eclipse.che.api.account.server.dao.Member;
import org.eclipse.che.api.account.server.dao.PlanDao;
import org.eclipse.che.api.account.server.dao.Subscription;
import org.eclipse.che.api.account.server.dao.SubscriptionDao;
import org.eclipse.che.api.account.shared.dto.NewSubscription;
import org.eclipse.che.api.account.shared.dto.Plan;
import org.eclipse.che.api.account.shared.dto.SubscriptionDescriptor;
import org.eclipse.che.api.account.shared.dto.SubscriptionReference;
import org.eclipse.che.api.account.shared.dto.SubscriptionResourcesUsed;
import org.eclipse.che.api.account.shared.dto.SubscriptionState;
import org.eclipse.che.api.account.shared.dto.UsedAccountResources;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.rest.annotations.GenerateLink;
import org.eclipse.che.api.core.rest.annotations.Required;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.core.util.LinksHelper;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.dto.server.DtoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Sergii Leschenko
 */
@Api(value = "/account",
        description = "Subscription manager")
@Path("/account")
public class SubscriptionRestService extends Service {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionRestService.class);

    private final AccountDao                  accountDao;
    private final SubscriptionDao             subscriptionDao;
    private final SubscriptionServiceRegistry registry;
    private final PlanDao                     planDao;

    @Inject
    public SubscriptionRestService(AccountDao accountDao,
                                   SubscriptionDao subscriptionDao,
                                   SubscriptionServiceRegistry registry,
                                   PlanDao planDao) {
        this.accountDao = accountDao;
        this.subscriptionDao = subscriptionDao;
        this.registry = registry;
        this.planDao = planDao;
    }

    /**
     * Returns list of subscriptions descriptors for certain account.
     * If service identifier is provided returns subscriptions that matches provided service.
     *
     * @param accountId
     *         account identifier
     * @param serviceId
     *         service identifier
     * @return subscriptions descriptors
     * @throws NotFoundException
     *         when account with given identifier doesn't exist
     * @throws ServerException
     *         when some error occurred while retrieving subscriptions
     * @see SubscriptionDescriptor
     */
    @Beta
    @ApiOperation(value = "Get account subscriptions",
            notes = "Get information on account subscriptions. This API call requires account/owner, account/member, system/admin or system/manager role.",
            response = SubscriptionDescriptor.class,
            responseContainer = "List",
            position = 10)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Account ID not found"),
            @ApiResponse(code = 500, message = "Internal Server Error")})
    @GET
    @Path("/{accountId}/subscriptions")
    @RolesAllowed({"account/member", "account/owner", "system/admin", "system/manager"})
    @Produces(MediaType.APPLICATION_JSON)
    public List<SubscriptionDescriptor> getSubscriptions(@ApiParam(value = "Account ID", required = true)
                                                         @PathParam("accountId") String accountId,
                                                         @ApiParam(value = "Service ID", required = false)
                                                         @QueryParam("service") String serviceId,
                                                         @Context SecurityContext securityContext) throws NotFoundException,
                                                                                                          ServerException {
        final List<Subscription> subscriptions = new ArrayList<>();
        if (serviceId == null || serviceId.isEmpty()) {
            subscriptions.addAll(subscriptionDao.getActiveSubscriptions(accountId));
        } else {
            final Subscription activeSubscription = subscriptionDao.getActiveSubscription(accountId, serviceId);
            if (activeSubscription != null) {
                subscriptions.add(activeSubscription);
            }
        }
        final List<SubscriptionDescriptor> result = new ArrayList<>(subscriptions.size());
        for (Subscription subscription : subscriptions) {
            result.add(toDescriptor(subscription, securityContext, null));
        }
        return result;
    }

    /**
     * Returns {@link SubscriptionDescriptor} for subscription with given identifier.
     *
     * @param subscriptionId
     *         subscription identifier
     * @return descriptor of subscription
     * @throws NotFoundException
     *         when subscription with given identifier doesn't exist
     * @throws ForbiddenException
     *         when user hasn't access to call this method
     * @see SubscriptionDescriptor
     * @see #getSubscriptions(String, String serviceId, SecurityContext)
     * @see #removeSubscription(String, SecurityContext)
     */
    @Beta
    @ApiOperation(value = "Get subscription details",
            notes = "Get information on a particular subscription by its unique ID.",
            response = SubscriptionDescriptor.class,
            position = 11)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 403, message = "User not authorized to call this method"),
            @ApiResponse(code = 404, message = "Account ID not found"),
            @ApiResponse(code = 500, message = "Internal Server Error")})
    @GET
    @Path("/subscriptions/{subscriptionId}")
    @RolesAllowed({"user", "system/admin", "system/manager"})
    @Produces(MediaType.APPLICATION_JSON)
    public SubscriptionDescriptor getSubscriptionById(@ApiParam(value = "Subscription ID", required = true)
                                                      @PathParam("subscriptionId") String subscriptionId,
                                                      @Context SecurityContext securityContext) throws NotFoundException,
                                                                                                       ServerException,
                                                                                                       ForbiddenException {
        final Subscription subscription = subscriptionDao.getSubscriptionById(subscriptionId);
        Set<String> roles = null;
        if (securityContext.isUserInRole("user")) {
            roles = resolveRolesForSpecificAccount(subscription.getAccountId());
            if (!roles.contains("account/owner") && !roles.contains("account/member")) {
                throw new ForbiddenException("Access denied");
            }
        }
        return toDescriptor(subscription, securityContext, roles);
    }

    /**
     * <p>Creates new subscription. Returns {@link SubscriptionDescriptor}
     * when subscription has been created successfully.
     * <p>Each new subscription should contain plan id and account id </p>
     *
     * @param newSubscription
     *         new subscription
     * @return descriptor of created subscription
     * @throws ConflictException
     *         when new subscription is {@code null}
     *         or new subscription plan identifier is {@code null}
     *         or new subscription account identifier is {@code null}
     * @throws NotFoundException
     *         if plan with certain identifier is not found
     * @throws org.eclipse.che.api.core.ApiException
     * @see SubscriptionDescriptor
     * @see #getSubscriptionById(String, SecurityContext)
     * @see #removeSubscription(String, SecurityContext)
     */
    @Beta
    @ApiOperation(value = "Add new subscription",
            notes = "Add a new subscription to an account. JSON with subscription details is sent. Roles: account/owner, system/admin.",
            response = SubscriptionDescriptor.class,
            position = 12)
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "CREATED"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 404, message = "Invalid subscription parameter"),
            @ApiResponse(code = 409, message = "Unknown ServiceID is used or payment token is invalid"),
            @ApiResponse(code = 500, message = "Internal Server Error")})
    @POST
    @Path("/subscriptions")
    @GenerateLink(rel = Constants.LINK_REL_ADD_SUBSCRIPTION)
    @RolesAllowed({"user", "system/admin", "system/manager"})
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addSubscription(@ApiParam(value = "Subscription details", required = true)
                                    @Required NewSubscription newSubscription,
                                    @Context SecurityContext securityContext)
            throws ApiException {
        requiredNotNull(newSubscription, "New subscription");
        requiredNotNull(newSubscription.getAccountId(), "Account identifier");
        requiredNotNull(newSubscription.getPlanId(), "Plan identifier");
        requiredNotNull(newSubscription.getUsePaymentSystem(), "Use payment system");

        //check user has access to add subscription
        final Set<String> roles = new HashSet<>();
        if (securityContext.isUserInRole("user")) {
            roles.addAll(resolveRolesForSpecificAccount(newSubscription.getAccountId()));
            if (!roles.contains("account/owner")) {
                throw new ForbiddenException("Access denied");
            }
        }

        final Plan plan = planDao.getPlanById(newSubscription.getPlanId());

        // check service exists
        final SubscriptionService service = registry.get(plan.getServiceId());
        if (null == service) {
            throw new ConflictException("Unknown serviceId is used");
        }

        //Not admin has additional restrictions
        if (!securityContext.isUserInRole("system/admin") && !securityContext.isUserInRole("system/manager")) {
            // check that subscription is allowed for not admin
            if (plan.getSalesOnly()) {
                throw new ForbiddenException("User not authorized to add this subscription, please contact support");
            }

            // only admins are allowed to disable payment on subscription addition
            if (!newSubscription.getUsePaymentSystem().equals(plan.isPaid())) {
                throw new ConflictException("Given value of attribute usePaymentSystem is not allowed");
            }

            // check trial
            if (newSubscription.getTrialDuration() != null && newSubscription.getTrialDuration() != 0) {
                // allow regular user use subscription without trial or with trial which duration equal to duration from the plan
                if (!newSubscription.getTrialDuration().equals(plan.getTrialDuration())) {
                    throw new ConflictException("User not authorized to add this subscription, please contact support");
                }
            }

            //only admins can override properties
            if (!newSubscription.getProperties().isEmpty()) {
                throw new ForbiddenException("User not authorized to add subscription with custom properties, please contact support");
            }
        }

        // disable payment if subscription is free
        if (!plan.isPaid()) {
            newSubscription.setUsePaymentSystem(false);
        }

        //preparing properties
        Map<String, String> properties = plan.getProperties();
        Map<String, String> customProperties = newSubscription.getProperties();
        for (Map.Entry<String, String> propertyEntry : customProperties.entrySet()) {
            if (properties.containsKey(propertyEntry.getKey())) {
                properties.put(propertyEntry.getKey(), propertyEntry.getValue());
            } else {
                throw new ForbiddenException("Forbidden overriding of non-existent plan properties");
            }
        }

        //create new subscription
        Subscription subscription = new Subscription()
                .withId(NameGenerator.generate(Subscription.class.getSimpleName().toLowerCase(), Constants.ID_LENGTH))
                .withAccountId(newSubscription.getAccountId())
                .withUsePaymentSystem(newSubscription.getUsePaymentSystem())
                .withServiceId(plan.getServiceId())
                .withPlanId(plan.getId())
                .withProperties(properties)
                .withDescription(plan.getDescription())
                .withBillingCycleType(plan.getBillingCycleType())
                .withBillingCycle(plan.getBillingCycle())
                .withBillingContractTerm(plan.getBillingContractTerm())
                .withState(SubscriptionState.ACTIVE);

        if (newSubscription.getTrialDuration() != null && newSubscription.getTrialDuration() != 0) {
            Calendar calendar = Calendar.getInstance();
            subscription.setTrialStartDate(calendar.getTime());
            calendar.add(Calendar.DATE, newSubscription.getTrialDuration());
            subscription.setTrialEndDate(calendar.getTime());
        }

        service.beforeCreateSubscription(subscription);

        LOG.info("Add subscription# id#{}# userId#{}# accountId#{}# planId#{}#",
                 subscription.getId(),
                 EnvironmentContext.getCurrent().getUser().getId(),
                 subscription.getAccountId(),
                 subscription.getPlanId());

        subscriptionDao.addSubscription(subscription);

        service.afterCreateSubscription(subscription);

        LOG.info("Added subscription. Subscription ID #{}# Account ID #{}#", subscription.getId(), subscription.getAccountId());

        return Response.status(Response.Status.CREATED)
                       .entity(toDescriptor(subscription, securityContext, roles))
                       .build();
    }

    /**
     * Removes subscription by id. Actually makes it inactive.
     *
     * @param subscriptionId
     *         id of the subscription to remove
     * @throws NotFoundException
     *         if subscription with such id is not found
     * @throws ForbiddenException
     *         if user hasn't permissions
     * @throws ServerException
     *         if internal server error occurs
     * @throws org.eclipse.che.api.core.ApiException
     * @see #addSubscription(NewSubscription, SecurityContext)
     * @see #getSubscriptions(String, String, SecurityContext)
     */
    @Beta
    @ApiOperation(value = "Remove subscription",
            notes = "Remove subscription from account. Roles: account/owner, system/admin.",
            position = 13)
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "OK"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 404, message = "Invalid subscription ID"),
            @ApiResponse(code = 500, message = "Internal Server Error")})
    @DELETE
    @Path("/subscriptions/{subscriptionId}")
    @RolesAllowed({"user", "system/admin", "system/manager"})
    public void removeSubscription(@ApiParam(value = "Subscription ID", required = true)
                                   @PathParam("subscriptionId") String subscriptionId, @Context SecurityContext securityContext)
            throws ApiException {
        final Subscription toRemove = subscriptionDao.getSubscriptionById(subscriptionId);
        if (securityContext.isUserInRole("user") && !resolveRolesForSpecificAccount(toRemove.getAccountId()).contains("account/owner")) {
            throw new ForbiddenException("Access denied");
        }
        if (SubscriptionState.INACTIVE == toRemove.getState()) {
            throw new ForbiddenException("Subscription is inactive already " + subscriptionId);
        }

        LOG.info("Remove subscription# id#{}# userId#{}# accountId#{}#", subscriptionId, EnvironmentContext.getCurrent().getUser().getId(),
                 toRemove.getAccountId());

        toRemove.setState(SubscriptionState.INACTIVE);
        subscriptionDao.updateSubscription(toRemove);
        final SubscriptionService service = registry.get(toRemove.getServiceId());
        service.onRemoveSubscription(toRemove);

    }

    /**
     * Returns used resources, provided by subscriptions
     *
     * @param accountId
     *         account id
     */
    @ApiOperation(value = "Get used resources, provided by subscriptions",
            notes = "Returns used resources, provided by subscriptions. Roles: account/owner, account/member, system/manager, system/admin.",
            position = 17)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Not found"),
            @ApiResponse(code = 500, message = "Internal Server Error")})
    @GET
    @Path("/{id}/resources")
    @RolesAllowed({"account/owner", "account/member", "system/manager", "system/admin"})
    @Produces(MediaType.APPLICATION_JSON)
    public List<SubscriptionResourcesUsed> getResources(@ApiParam(value = "Account ID", required = true)
                                                        @PathParam("id") String accountId,
                                                        @QueryParam("serviceId") String serviceId)
            throws ServerException, NotFoundException, ConflictException {
        Set<SubscriptionService> subscriptionServices = new HashSet<>();
        if (serviceId == null) {
            subscriptionServices.addAll(registry.getAll());
        } else {
            final SubscriptionService subscriptionService = registry.get(serviceId);
            if (subscriptionService == null) {
                throw new ConflictException("Unknown serviceId is used");
            }
            subscriptionServices.add(subscriptionService);
        }

        List<SubscriptionResourcesUsed> result = new ArrayList<>();
        for (SubscriptionService subscriptionService : subscriptionServices) {
            Subscription activeSubscription = subscriptionDao.getActiveSubscription(accountId, subscriptionService.getServiceId());
            if (activeSubscription != null) {
                //For now account can have only one subscription for each service
                UsedAccountResources usedAccountResources = subscriptionService.getAccountResources(activeSubscription);
                result.add(DtoFactory.getInstance().createDto(SubscriptionResourcesUsed.class)
                                     .withUsed(usedAccountResources.getUsed())
                                     .withSubscriptionReference(toReference(activeSubscription)));
            }
        }

        return result;
    }

    /**
     * Can be used only in methods that is restricted with @RolesAllowed. Require "user" role.
     *
     * @param currentAccountId
     *         account id to resolve roles for
     * @return set of user roles
     */
    private Set<String> resolveRolesForSpecificAccount(String currentAccountId) {
        try {
            final String userId = EnvironmentContext.getCurrent().getUser().getId();
            for (Member membership : accountDao.getByMember(userId)) {
                if (membership.getAccountId().equals(currentAccountId)) {
                    return new HashSet<>(membership.getRoles());
                }
            }
        } catch (ApiException ignored) {
        }
        return Collections.emptySet();
    }


    /**
     * Create {@link SubscriptionDescriptor} from {@link Subscription}.
     * Set with roles should be used if account roles can't be resolved with {@link SecurityContext}
     * (If there is no id of the account in the REST path.)
     *
     * @param subscription
     *         subscription that should be converted to {@link SubscriptionDescriptor}
     * @param resolvedRoles
     *         resolved roles. Do not use if id of the account presents in REST path.
     */
    private SubscriptionDescriptor toDescriptor(Subscription subscription, SecurityContext securityContext, Set resolvedRoles) {
        List<Link> links = new ArrayList<>(0);
        // community subscriptions should not use urls
        if (!"sas-community".equals(subscription.getPlanId())) {
            final UriBuilder uriBuilder = getServiceContext().getServiceUriBuilder();
            links.add(LinksHelper.createLink(HttpMethod.GET,
                                             uriBuilder.clone()
                                                       .path(getClass(), "getSubscriptionById")
                                                       .build(subscription.getId())
                                                       .toString(),
                                             null,
                                             MediaType.APPLICATION_JSON,
                                             Constants.LINK_REL_GET_SUBSCRIPTION));
            boolean isUserPrivileged = (resolvedRoles != null && resolvedRoles.contains("account/owner")) ||
                                       securityContext.isUserInRole("account/owner") ||
                                       securityContext.isUserInRole("system/admin") ||
                                       securityContext.isUserInRole("system/manager");
            if (SubscriptionState.ACTIVE.equals(subscription.getState()) && isUserPrivileged) {
                links.add(LinksHelper.createLink(HttpMethod.DELETE,
                                                 uriBuilder.clone()
                                                           .path(getClass(), "removeSubscription")
                                                           .build(subscription.getId())
                                                           .toString(),
                                                 null,
                                                 null,
                                                 Constants.LINK_REL_REMOVE_SUBSCRIPTION));
            }
        }

        // Do not send with REST properties that starts from 'codenvy:'
        LinkedHashMap<String, String> filteredProperties = new LinkedHashMap<>();
        for (Map.Entry<String, String> property : subscription.getProperties().entrySet()) {
            if (!property.getKey().startsWith("codenvy:") || securityContext.isUserInRole("system/admin") ||
                securityContext.isUserInRole("system/manager")) {
                filteredProperties.put(property.getKey(), property.getValue());
            }
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        dateFormat.setLenient(false);

        return DtoFactory.getInstance().createDto(SubscriptionDescriptor.class)
                         .withId(subscription.getId())
                         .withAccountId(subscription.getAccountId())
                         .withServiceId(subscription.getServiceId())
                         .withProperties(filteredProperties)
                         .withPlanId(subscription.getPlanId())
                         .withState(subscription.getState())
                         .withDescription(subscription.getDescription())
                         .withStartDate(null == subscription.getStartDate() ? null : dateFormat.format(subscription.getStartDate()))
                         .withEndDate(null == subscription.getEndDate() ? null : dateFormat.format(subscription.getEndDate()))
                         .withTrialStartDate(
                                 null == subscription.getTrialStartDate() ? null : dateFormat.format(subscription.getTrialStartDate()))
                         .withTrialEndDate(
                                 null == subscription.getTrialEndDate() ? null : dateFormat.format(subscription.getTrialEndDate()))
                         .withUsePaymentSystem(subscription.getUsePaymentSystem())
                         .withBillingStartDate(
                                 null == subscription.getBillingStartDate() ? null : dateFormat.format(subscription.getBillingStartDate()))
                         .withBillingEndDate(
                                 null == subscription.getBillingEndDate() ? null : dateFormat.format(subscription.getBillingEndDate()))
                         .withNextBillingDate(
                                 null == subscription.getNextBillingDate() ? null : dateFormat.format(subscription.getNextBillingDate()))
                         .withBillingCycle(subscription.getBillingCycle())
                         .withBillingCycleType(subscription.getBillingCycleType())
                         .withBillingContractTerm(subscription.getBillingContractTerm())
                         .withLinks(links);
    }

    /**
     * Create {@link SubscriptionReference} from {@link Subscription}.
     *
     * @param subscription
     *         subscription that should be converted to {@link SubscriptionReference}
     */
    private SubscriptionReference toReference(Subscription subscription) {
        List<Link> links = new ArrayList<>(0);
        // community subscriptions should not use urls
        if (!"sas-community".equals(subscription.getPlanId())) {
            final UriBuilder uriBuilder = getServiceContext().getServiceUriBuilder();
            links.add(LinksHelper.createLink(HttpMethod.GET,
                                             uriBuilder.clone()
                                                       .path(getClass(), "getSubscriptionById")
                                                       .build(subscription.getId())
                                                       .toString(),
                                             null,
                                             MediaType.APPLICATION_JSON,
                                             Constants.LINK_REL_GET_SUBSCRIPTION));
        }

        return DtoFactory.getInstance().createDto(SubscriptionReference.class)
                         .withSubscriptionId(subscription.getId())
                         .withServiceId(subscription.getServiceId())
                         .withDescription(subscription.getDescription())
                         .withPlanId(subscription.getPlanId())
                         .withLinks(links);
    }


    /**
     * Checks object reference is not {@code null}
     *
     * @param object
     *         object reference to check
     * @param subject
     *         used as subject of exception message "{subject} required"
     * @throws ConflictException
     *         when object reference is {@code null}
     */
    private void requiredNotNull(Object object, String subject) throws ConflictException {
        if (object == null) {
            throw new ConflictException(subject + " required");
        }
    }
}
