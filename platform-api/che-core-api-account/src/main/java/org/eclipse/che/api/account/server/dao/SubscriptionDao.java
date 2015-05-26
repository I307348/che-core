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
package org.eclipse.che.api.account.server.dao;

import com.google.common.annotations.Beta;

import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;

import java.util.List;

/**
 * @author Sergii Leschenko
 */
public interface SubscriptionDao {
    /**
     * Adds new subscription to account that already exists in persistent layer
     *
     * @param subscription
     *         subscription POJO
     */
    @Beta
    void addSubscription(Subscription subscription) throws NotFoundException, ConflictException, ServerException;

    /**
     * Get subscription from persistent layer
     *
     * @param subscriptionId
     *         subscription identifier
     * @return Subscription POJO
     * @throws org.eclipse.che.api.core.NotFoundException
     *         when subscription doesn't exist
     */
    @Beta
    Subscription getSubscriptionById(String subscriptionId) throws NotFoundException, ServerException;

    /**
     * Gets list of active subscriptions related to given account.
     *
     * @param accountId
     *         account id
     * @return list of subscriptions, or empty list if no subscriptions found
     */
    @Beta
    List<Subscription> getActiveSubscriptions(String accountId) throws NotFoundException, ServerException;

    /**
     * Gets active subscription with given service related to given account.
     *
     * @param accountId
     *         account id
     * @param serviceId
     *         service id
     * @return subscription or {@code null} if no subscription found
     */
    @Beta
    Subscription getActiveSubscription(String accountId, String serviceId) throws ServerException, NotFoundException;

    /**
     * Update existing subscription.
     *
     * @param subscription
     *         new subscription
     */
    @Beta
    void updateSubscription(Subscription subscription) throws NotFoundException, ServerException;

    /**
     * Remove subscription related to existing account
     *
     * @param subscriptionId
     *         subscription identifier for removal
     */
    @Beta
    void removeSubscription(String subscriptionId) throws NotFoundException, ServerException;
}
