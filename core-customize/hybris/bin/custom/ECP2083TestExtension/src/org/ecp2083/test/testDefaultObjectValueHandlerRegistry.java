/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved
 */
package org.ecp2083.test;

import de.hybris.platform.cockpit.cache.RequestCacheCallable;
import de.hybris.platform.cockpit.model.meta.ObjectType;
import de.hybris.platform.cockpit.services.meta.TypeService;
import de.hybris.platform.cockpit.services.values.ObjectValueHandler;
import de.hybris.platform.cockpit.services.values.ObjectValueHandlerRegistry;
import de.hybris.platform.cockpit.services.values.impl.ObjectValueHandlerMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;


/**
 * @author ag
 */
public class testDefaultObjectValueHandlerRegistry implements ObjectValueHandlerRegistry
{
	private static final String VALUE_HANDLER_CHAIN_REQUEST_CACHE = "valueHandlerChainRequestCache";

	private TypeService typeService;
	private ApplicationContext appContext;

	private final Map<String, List<ObjectValueHandler>> handlersMap = new ConcurrentHashMap<>();

	@Override
	public List<ObjectValueHandler> getValueHandlerChain(final ObjectType type)
	{
		final String qualifier = type.getCode().toLowerCase();
		return new RequestCacheCallable<String, List<ObjectValueHandler>>(VALUE_HANDLER_CHAIN_REQUEST_CACHE)
		{
			@Override
			protected List<ObjectValueHandler> call()
			{


				final List<ObjectValueHandler> ret = new ArrayList<ObjectValueHandler>();
				List<ObjectValueHandler> handlers = handlersMap.get(qualifier);
				if (handlers != null)
				{
					ret.addAll(handlers);
				}
				for (final ObjectType current : typeService.getAllSupertypes(type))
				{
					handlers = handlersMap.get(current.getCode().toLowerCase());
					if (handlers != null)
					{
						ret.addAll(handlers);
					}
				}

				if (ret.isEmpty())
				{
					throw new IllegalArgumentException("no value handler found for type " + type);
				}

				return ret;
			}
		}.get(qualifier);
	}


	@Override
	public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
	{
		this.appContext = applicationContext;
	}

	public synchronized void initHandlerMappings()
	{
		final Map<String, ObjectValueHandlerMapping> beanMap = this.appContext.getBeansOfType(ObjectValueHandlerMapping.class);
		final List<ObjectValueHandlerMapping> mappings = new ArrayList<ObjectValueHandlerMapping>(beanMap.values());
		Collections.sort(mappings, new Comparator<ObjectValueHandlerMapping>()
		{
			@Override
			public int compare(final ObjectValueHandlerMapping objectValueHandlerMapping1,
					final ObjectValueHandlerMapping objectValueHandlerMapping2)
			{
				return objectValueHandlerMapping1.getOrder() - objectValueHandlerMapping2.getOrder();
			}
		});

		this.handlersMap.clear();
		for (final ObjectValueHandlerMapping mapping : mappings)
		{
			List<ObjectValueHandler> handlers = this.handlersMap.computeIfAbsent(mapping.getTypeCode().toLowerCase(), k -> new ArrayList<ObjectValueHandler>());
			handlers.add(mapping.getValueHandler());
		}
	}

	@Required
	public void setCockpitTypeService(final TypeService typeService)
	{
		this.typeService = typeService;
	}

	public TypeService getTypeService()
	{
		return this.typeService;
	}
}