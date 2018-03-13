/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.client.registration;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.registration.AutoRegistration;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.discovery.registration.RegistrationException;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServerInstance;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.NoSuchElementException;

/**
 * Abstract class for {@link AutoRegistration} with discovery services
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class DiscoveryServiceAutoRegistration extends AutoRegistration {
    protected DiscoveryServiceAutoRegistration(RegistrationConfiguration registrationConfiguration) {
        super(registrationConfiguration);
    }

    protected void performRegistration(
            String discoveryService,
            RegistrationConfiguration registration,
            ServiceInstance instance,
            Publisher<HttpStatus> registrationObservable) {
            registrationObservable.subscribe(new Subscriber<HttpStatus>() {

                @Override
                public void onSubscribe(Subscription s) {
                    s.request(1);
                }

                @Override
                public void onNext(HttpStatus httpStatus) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("Registered service [{}] with {}", instance.getId(), discoveryService);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (LOG.isErrorEnabled()) {
                        String message = getErrorMessage(discoveryService, t);
                        LOG.error(message, t);
                    }
                    if(registration.isFailFast() && instance instanceof EmbeddedServerInstance) {
                        ((EmbeddedServerInstance) instance).getEmbeddedServer().stop();
                    }
                }

                @Override
                public void onComplete() {

                }
            });
    }

    private String getErrorMessage(String discoveryService, Throwable e) {
        String description = "Error occurred during service registration with " + discoveryService + ": ";
        return getErrorMessage(e, description);
    }

    protected String getErrorMessage(Throwable e, String description) {
        String message;
        if (e instanceof HttpClientResponseException) {
            HttpClientResponseException hcre = (HttpClientResponseException) e;
            if (hcre.getStatus() == HttpStatus.BAD_REQUEST) {
                message = description + hcre.getResponse().getBody(String.class).orElse(e.getMessage());
            } else {
                message = description + e.getMessage();
            }
        } else {
            message = description + e.getMessage();
        }
        return message;
    }

    protected void performDeregistration(String discoveryService, RegistrationConfiguration registration, Publisher<HttpStatus> deregisterPublisher, String applicationName) {
        if (registration.isFailFast()) {

            try {
                Flowable.fromPublisher(deregisterPublisher).blockingFirst();
                if (LOG.isInfoEnabled()) {
                    LOG.info("De-registered service [{}] with {}", applicationName, discoveryService);
                }
            } catch (Throwable t) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error occurred de-registering service [" + applicationName + "] with " + discoveryService + ": " + t.getMessage(), t);
                }
            }
        } else {
            deregisterPublisher.subscribe(new Subscriber<HttpStatus>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                public void onNext(HttpStatus httpStatus) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("De-registered service [{}] with {}", applicationName, discoveryService);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error occurred de-registering service [" + applicationName + "] with " + discoveryService + ": " + t.getMessage(), t);
                    }
                }

                @Override
                public void onComplete() {

                }
            });
        }
    }
}