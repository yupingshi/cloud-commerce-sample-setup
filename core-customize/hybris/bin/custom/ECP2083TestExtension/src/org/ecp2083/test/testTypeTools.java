/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved
 */
package org.ecp2083.test;

import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.catalog.model.classification.ClassificationAttributeValueModel;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.classification.features.FeatureValue;
import de.hybris.platform.cockpit.model.listview.ValueHandler;
import de.hybris.platform.cockpit.model.listview.impl.DefaultValueHandler;
import de.hybris.platform.cockpit.model.meta.BaseType;
import de.hybris.platform.cockpit.model.meta.ObjectTemplate;
import de.hybris.platform.cockpit.model.meta.ObjectType;
import de.hybris.platform.cockpit.model.meta.PropertyDescriptor;
import de.hybris.platform.cockpit.model.meta.TypedObject;
import de.hybris.platform.cockpit.model.meta.impl.ItemAttributePropertyDescriptor;
import de.hybris.platform.cockpit.services.SystemService;
import de.hybris.platform.cockpit.services.config.BaseConfiguration;
import de.hybris.platform.cockpit.services.config.InitialPropertyConfiguration;
import de.hybris.platform.cockpit.services.config.UIConfigurationService;
import de.hybris.platform.cockpit.services.label.LabelService;
import de.hybris.platform.cockpit.services.meta.PropertyService;
import de.hybris.platform.cockpit.services.meta.TypeService;
import de.hybris.platform.cockpit.services.security.UIAccessRightService;
import de.hybris.platform.cockpit.services.values.ObjectValueContainer;
import de.hybris.platform.cockpit.services.values.ObjectValueContainer.ObjectValueHolder;
import de.hybris.platform.cockpit.services.values.ObjectValueHandler;
import de.hybris.platform.cockpit.services.values.ObjectValueHandlerRegistry;
import de.hybris.platform.cockpit.services.values.ObjectValueLazyLoader;
import de.hybris.platform.cockpit.services.values.ValueHandlerException;
import de.hybris.platform.cockpit.services.values.ValueHandlerPermissionException;
import de.hybris.platform.cockpit.services.values.ValueService;
import de.hybris.platform.cockpit.session.UISessionUtils;
import de.hybris.platform.core.HybrisEnumValue;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.type.AttributeDescriptorModel;
import de.hybris.platform.core.model.type.ComposedTypeModel;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.hmc.jalo.AccessManager;
import de.hybris.platform.jalo.Item;
import de.hybris.platform.jalo.JaloInvalidParameterException;
import de.hybris.platform.jalo.JaloSession;
import de.hybris.platform.jalo.SessionContext;
import de.hybris.platform.jalo.c2l.C2LManager;
import de.hybris.platform.jalo.c2l.Language;
import de.hybris.platform.jalo.enumeration.EnumerationValue;
import de.hybris.platform.jalo.security.JaloSecurityException;
import de.hybris.platform.jalo.type.ComposedType;
import de.hybris.platform.jalo.type.Type;
import de.hybris.platform.jalo.type.TypeManager;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.variants.model.VariantTypeModel;
import de.hybris.platform.cockpit.util.UITools;

import java.lang.reflect.Constructor;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.spring.SpringUtil;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Executions;


/**
 * Usefull utility methods for dealing with the cockpit type system.
 */
public class testTypeTools
{
	private static final String DEFAULT_DISPLAY_MAXCOLLECTIONENTRIES = "default.display.maxCollectionEntries";
	private static final Logger LOG = LoggerFactory.getLogger(testTypeTools.class);

	/**
	 * Returns a string representation of a value, where value can be a TypedObject, a collection of TypedObject (the
	 * LabelService is used to get the labels) or any other Object (toString() is used).
	 *
	 * @param labelService
	 *           the label service
	 * @param value
	 *           the value
	 * @return the string representation
	 */
	// TODO: move to  LabelService and mark it deprecated
	public static String getValueAsString(final LabelService labelService, final Object value)
	{
		int collectionMaxEntries = 1;
		final String rawParameter = UITools.getCockpitParameter(DEFAULT_DISPLAY_MAXCOLLECTIONENTRIES, Executions.getCurrent());
		if (rawParameter != null)
		{
			collectionMaxEntries = Integer.parseInt(rawParameter);
		}
		return getValueAsString(labelService, value, collectionMaxEntries);
	}

	public static String getValueAsString(final LabelService labelService, final Object value, final int size)
	{
		String text = "";
		if (value instanceof Collection)
		{
			int index = 1;
			final StringBuilder stringBuilder = new StringBuilder();
			final List rawCollection = new ArrayList((Collection) value);
			if (CollectionUtils.isNotEmpty(rawCollection))
			{
				stringBuilder.append(getLabel(rawCollection.get(0), labelService));

				final int maxIndex = size > 0 ? Math.min(rawCollection.size(), size) : rawCollection.size();

				for (; index < maxIndex; index++)
				{
					stringBuilder.append(", ");
					stringBuilder.append(getLabel(rawCollection.get(index), labelService));
				}

				if (index < rawCollection.size())
				{
					stringBuilder.append(", ...");
				}
			}
			text = stringBuilder.toString();
		}
		else
		{
			text = getLabel(value, labelService);
		}
		return text;
	}

	/**
	 * Get the type name in the current session language.
	 *
	 * @deprecated since 6.3, use {@link BaseType#getName()} instead.
	 * @param type
	 *           the type
	 * @param typeService
	 *           the typeservice
	 * @return the type name
	 */
	@Deprecated
	public static String getTypeName(final BaseType type, final TypeService typeService)
	{
		return type.getName();
	}

	/**
	 * Get the type description in the current session language.
	 *
	 * @deprecated since 6.3, use {@link BaseType#getDescription()} instead.
	 * @param type
	 *           the type
	 * @param typeService
	 *           the typeservice
	 * @return the type description
	 */
	@Deprecated
	public static String getTypeDescription(final BaseType type, final TypeService typeService)
	{
		return type.getDescription();
	}

	/**
	 * Gets the initial values.
	 *
	 * @param type
	 *           the type
	 * @param baseItem
	 *           the base item
	 * @param typeService
	 *           the type service
	 * @param configurationService
	 *           the configuration service
	 * @return the initial values
	 */
	// TODO: move to  ValueService and mark as Deprecated
	public static Map<String, Object> getInitialValues(final ObjectType type, final TypedObject baseItem,
			final TypeService typeService, final UIConfigurationService configurationService)
	{
		Map<String, Object> initialValues = new HashMap<String, Object>();
		final ObjectTemplate template = typeService.getObjectTemplate(type.getCode());


		final BaseConfiguration baseConfiguration = configurationService.getComponentConfiguration(template, "base",
				BaseConfiguration.class);
		final InitialPropertyConfiguration initialPropertyConfig = baseConfiguration
				.getInitialPropertyConfiguration(typeService.getObjectTemplate(baseItem.getType().getCode()), typeService);

		if (initialPropertyConfig != null)
		{
			initialValues = initialPropertyConfig.getInitialProperties(baseItem, typeService);
		}

		return initialValues;
	}

	private static String getLabel(final Object value, final LabelService labelService)
	{
		String ret = null;
		if (value instanceof TypedObject)
		{
			ret = labelService.getObjectTextLabel((TypedObject) value);
		}
		else if (value instanceof Boolean)
		{
			final String locBoolean = "booleaneditor." + value.toString() + ".name";
			ret = Labels.getLabel(locBoolean, value.toString());
		}
		else if (value instanceof ClassificationAttributeValueModel)
		{
			ret = ((ClassificationAttributeValueModel) value).getName();
			if (ret == null)
			{
				ret = ((ClassificationAttributeValueModel) value).getCode();
			}
		}
		else if (value instanceof FeatureValue)
		{
			final FeatureValue featureValue = (FeatureValue) value;
			String valueString = null;
			if (featureValue.getValue() != null)
			{
				if (featureValue.getValue() instanceof ClassificationAttributeValueModel)
				{
					valueString = getLabel(featureValue.getValue(), labelService);
				}
				else
				{
					valueString = featureValue.getValue().toString();
				}
			}

			final String unitString = featureValue.getUnit() == null ? null : featureValue.getUnit().getName();

			final StringBuilder builder = new StringBuilder();
			if (valueString != null)
			{
				builder.append(valueString);
			}
			if (unitString != null)
			{
				builder.append(" " + unitString);
			}
			ret = builder.toString();
		}
		else if (value instanceof Double)
		{
			final NumberFormat instance = NumberFormat.getInstance(UISessionUtils.getCurrentSession().getLocale());
			instance.setMaximumFractionDigits(10);
			ret = instance.format(((Double) value).doubleValue());
		}
		else if (value instanceof Float)
		{
			ret = NumberFormat.getInstance(UISessionUtils.getCurrentSession().getLocale()).format(((Float) value).floatValue());
		}
		else if (value instanceof Date)
		{
			ret = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, UISessionUtils.getCurrentSession().getLocale())
					.format((Date) value);
		}
		else if (value instanceof HybrisEnumValue)
		{
			ret = getEnumName((HybrisEnumValue) value);
			if (ret == null)
			{
				ret = value.toString();
			}
		}
		else if (value != null)
		{
			ret = value.toString();
		}

		return ret;
	}

	/**
	 * Gets the multiplicity string.
	 *
	 * @deprecated since 6.3, use {@link PropertyService#getMultiplicityString(PropertyDescriptor)} instead.
	 * @param propertyDescriptor
	 *           the property descriptor
	 * @return the multiplicity string
	 */
	@Deprecated
	public static String getMultiplicityString(final PropertyDescriptor propertyDescriptor)
	{
		return getPropertyService().getMultiplicityString(propertyDescriptor);
	}

	/**
	 * Gets the value type as object template.
	 *
	 * @param propertyDescriptor
	 *           the property descriptor
	 * @param typeService
	 *           the type service
	 * @return the value type as object template
	 */
	public static ObjectTemplate getValueTypeAsObjectTemplate(final PropertyDescriptor propertyDescriptor,
			final TypeService typeService)
	{
		final String typeCode = typeService.getValueTypeCode(propertyDescriptor);
		final Type type = typeCode == null ? null : TypeManager.getInstance().getType(typeCode);

		return (type instanceof ComposedType) ? typeService.getObjectTemplate(typeCode) : null;
	}

	/**
	 * Gets the object attribute value.
	 *
	 * @deprecated since 6.3, use {@link ValueService#getValue(TypedObject, PropertyDescriptor)} instead.
	 *
	 * @param object
	 *           the object
	 * @param attributeQualifier
	 *           the attribute qualifier
	 * @param typeService
	 *           the type service
	 * @return the object attribute value
	 */
	@Deprecated
	// TODO: delegate to ValueService
	public static Object getObjectAttributeValue(final TypedObject object, final String attributeQualifier,
			final TypeService typeService)
	{
		//
		//TODO use value handlers here
		//
		Object ret = null;
		if (object != null)
		{
			if (!getModelService().isNew(object.getObject()))
			{
				final Object realObject = getModelService().getSource(object.getObject());
				try
				{
					ret = (realObject instanceof Item) ? ((Item) realObject).getAttribute(attributeQualifier) : null;
					if (ret instanceof EnumerationValue)
					{
						ret = getModelService().get(((EnumerationValue) ret).getPK());
					}
					else if (ret instanceof Item)
					{
						ret = typeService.wrapItem(ret);
					}
					else if (ret instanceof Collection)
					{
						final Collection tmp = (Collection) ret;
						if (!tmp.isEmpty())
						{
							final Object first = tmp.iterator().next();
							if (first instanceof Item)
							{
								ret = typeService.wrapItems(tmp);
							}
						}
					}
				}
				catch (final JaloInvalidParameterException e)
				{
					LOG.error(e.getMessage());
				}
				catch (final JaloSecurityException e)
				{
					LOG.error(e.getMessage());
				}
			}
			else
			{
				ret = getModelService().getAttributeValue(object.getObject(), attributeQualifier);
			}
		}
		return ret;
	}

	/**
	 * Creates the value container.
	 *
	 * @param typedObject
	 *           the typed object
	 * @param propertyDescriptors
	 *           the property descriptors
	 * @param langIsos
	 *           the lang isos
	 * @return the object value container
	 */
	public static ObjectValueContainer createValueContainer(final TypedObject typedObject,
			final Set<PropertyDescriptor> propertyDescriptors, final Set<String> langIsos)
	{
		return createValueContainer(typedObject, propertyDescriptors, langIsos, false);
	}


	/**
	 * Creates the value container.
	 *
	 * @param typedObject
	 *           the typed object
	 * @param propertyDescriptors
	 *           the property descriptors
	 * @param langIsos
	 *           the lang isos
	 * @param lazyLoad
	 *           the lazy load
	 * @return the object value container
	 */
	public static ObjectValueContainer createValueContainer(final ObjectValueHandlerRegistry valueHandlerRegistry,
			final TypedObject typedObject, final Set<PropertyDescriptor> propertyDescriptors, final Set<String> langIsos,
			final boolean lazyLoad)
	{
		final ObjectValueContainer ret;
		if (lazyLoad)
		{
			ret = new ObjectValueContainer(typedObject.getType(), typedObject.getObject(), propertyDescriptors, langIsos,
					new ObjectValueLazyLoader()
					{
						@Override
						public void loadValue(final ObjectValueContainer valueContainer, final PropertyDescriptor propertyDescriptor,
								final String languageIso)
						{
							for (final ObjectValueHandler valueHandler : valueHandlerRegistry
									.getValueHandlerChain(typedObject.getType()))
							{
								try
								{
									valueHandler.loadValues(valueContainer, typedObject.getType(), typedObject,
											Collections.singleton(propertyDescriptor), Collections.singleton(languageIso));
								}
								catch (final ValueHandlerPermissionException e)
								{
                                    LOG.error("error permission when loading object values when lazy load");
									//do nothing
								}
								catch (final ValueHandlerException e2)
								{
									LOG.error("error loading object values", e2);
								}
							}
						}
					});
		}
		else
		{
			ret = new ObjectValueContainer(typedObject.getType(), typedObject.getObject());

			for (final ObjectValueHandler valueHandler : valueHandlerRegistry.getValueHandlerChain(typedObject.getType()))
			{
				try
				{
					valueHandler.loadValues(ret, typedObject.getType(), typedObject, propertyDescriptors, langIsos);
				}
				catch (final ValueHandlerPermissionException e)
				{
					LOG.error("error permission when loading object values");
					//do nothing
				}
				catch (final ValueHandlerException e)
				{
					LOG.error("error loading object values", e);
				}
			}
		}

		return ret;
	}

	/**
	 * egacy API relying on ZK dependency injection
	 *
	 * @param typedObject
	 * @param propertyDescriptors
	 * @param langIsos
	 * @param lazyLoad
	 * @return
	 */
	public static ObjectValueContainer createValueContainer(final TypedObject typedObject,
			final Set<PropertyDescriptor> propertyDescriptors, final Set<String> langIsos, final boolean lazyLoad)
	{
		final ObjectValueHandlerRegistry valueHandlerRegistry = UISessionUtils.getCurrentSession().getValueHandlerRegistry();
		return createValueContainer(valueHandlerRegistry, typedObject, propertyDescriptors, langIsos, lazyLoad);
	}

	/**
	 * Gets the all supertypes.
	 *
	 * @param type
	 *           the type
	 * @return the all supertypes
	 */
	public static List<ObjectType> getAllSupertypes(final ObjectType type)
	{
		List<ObjectType> ret = null;

		Set<ObjectType> current = new HashSet<ObjectType>(type.getSupertypes());
		do
		{
			Set<ObjectType> nextLevel = null;
			for (final ObjectType st : current)
			{
				if (ret == null)
				{
					ret = new ArrayList<ObjectType>();
				}

				if (ret.add(st))
				{
					final Set<ObjectType> superTypes = st.getSupertypes();
					if (!superTypes.isEmpty())
					{
						if (nextLevel == null)
						{
							nextLevel = new LinkedHashSet<ObjectType>();
						}
						nextLevel.addAll(superTypes);
					}
				}
			}
			current = nextLevel;
		}
		while (current != null && !current.isEmpty());

		if (ret != null)
		{
			Collections.reverse(ret);
		}
		return ret != null ? ret : Collections.EMPTY_LIST;
	}

	/**
	 * Gets the all subtypes.
	 *
	 * @param type
	 *           the type
	 * @return the all subtypes
	 */
	public static Set<ObjectType> getAllSubtypes(final ObjectType type)
	{
		final Set<ObjectType> ret = new LinkedHashSet<ObjectType>();
		for (Collection<ObjectType> types = Collections.singleton(type), next = new LinkedHashSet<ObjectType>(); types != null
				&& !types.isEmpty(); types = next, next = new LinkedHashSet<ObjectType>())
		{
			for (final ObjectType tt : types)
			{
				ret.add(tt);
				next.addAll(tt.getSubtypes());
			}
		}
		return ret;
	}

	/**
	 * Checks for supertype.
	 *
	 * @param type
	 *           the type
	 * @param supertype
	 *           the supertype
	 * @return true, if successful
	 */
	public static boolean hasSupertype(final ObjectType type, final ObjectType supertype)
	{
		Set<? extends ObjectType> current = type.getSupertypes();
		do
		{
			Set<ObjectType> nextLevel = null;
			for (final ObjectType st : current)
			{
				if (st.equals(supertype))
				{
					return true;
				}

				final Set<ObjectType> superTypes = st.getSupertypes();
				if (!superTypes.isEmpty())
				{
					if (nextLevel == null)
					{
						nextLevel = new LinkedHashSet<ObjectType>();
					}
					nextLevel.addAll(superTypes);
				}
			}
			current = nextLevel;
		}
		while (current != null && !current.isEmpty());

		return false;
	}

	/**
	 * Item to pk list.
	 *
	 * @deprecated since 6.3, will be removed without replacement.
	 *
	 * @param items
	 *           the items
	 * @return the list < p k>
	 */
	@Deprecated
	public static List<PK> itemToPkList(final Collection<? extends Item> items)
	{
		if (items == null || items.isEmpty())
		{
			return Collections.EMPTY_LIST;
		}
		final List<PK> pkList = new ArrayList<PK>(items.size());
		for (final Item item : items)
		{
			pkList.add(item.getPK());
		}
		return pkList;
	}


	/**
	 * Gets the templates for creation.
	 *
	 * @param typeService
	 *           the type service
	 * @param type
	 *           the type
	 * @return the templates for creation
	 */
	public static List<ObjectTemplate> getTemplatesForCreation(final TypeService typeService, final BaseType type)
	{
		final List<ObjectTemplate> ret = new ArrayList<ObjectTemplate>();

		final List<Map<ObjectTemplate, Integer>> templatesForCreationWithDepth = getTemplatesForCreationWithDepth(typeService, type,
				0, false);
		for (final Map<ObjectTemplate, Integer> map : templatesForCreationWithDepth)
		{
			ret.add(map.entrySet().iterator().next().getKey());
		}
		return ret;
	}


	/**
	 * Gets the templates for creation with depth.
	 *
	 * @param typeService
	 *           the type service
	 * @param type
	 *           the type
	 * @param initialDepth
	 *           the initial depth
	 * @param showAbstract
	 *           the show abstract
	 * @return the templates for creation with depth
	 */
	public static List<Map<ObjectTemplate, Integer>> getTemplatesForCreationWithDepth(final TypeService typeService,
			final BaseType type, final int initialDepth, final boolean showAbstract)
	{
		final List<Map<ObjectTemplate, Integer>> templates = new ArrayList<Map<ObjectTemplate, Integer>>();
		for (final ObjectTemplate templ : typeService.getObjectTemplates(type))
		{
			if (showAbstract || !templ.isAbstract())
			{
				templates.add(
						Collections.singletonMap(templ, Integer.valueOf(templ.isDefaultTemplate() ? initialDepth : initialDepth + 1)));
			}
		}
		for (final ObjectType subtype : type.getSubtypes())
		{
			templates.addAll(getTemplatesForCreationWithDepth(typeService, (BaseType) subtype, initialDepth + 1, showAbstract));
		}

		return templates;
	}

	/**
	 * Checks if is editable.
	 *
	 * @param systemService
	 *           the system service
	 * @param type
	 *           object type
	 * @param propertyDescriptor
	 *           property descriptor
	 * @param creationMode
	 *           the creation mode
	 * @return true, if is editable
	 * @deprecated since 6.3, use {@link UIAccessRightService} isWritable methods instead.
	 */
	@Deprecated
	public static boolean isEditable(final SystemService systemService, final ObjectType type,
			final PropertyDescriptor propertyDescriptor, final boolean creationMode)
	{
		boolean writable = (checkAccessRights(systemService, type, propertyDescriptor, creationMode, AccessManager.CHANGE)
				&& checkAccessRights(systemService, type, propertyDescriptor, creationMode, AccessManager.READ));
		if (writable)
		{
			// property flags check
			writable = (propertyDescriptor.isReadable() || (creationMode && isInitial(propertyDescriptor)));
		}
		return writable;
	}

	/**
	 * Checks if is readable.
	 *
	 * @param systemService
	 *           the system service
	 * @param type
	 *           object type
	 * @param propertyDescriptor
	 *           property descriptor
	 * @param creationMode
	 *           the creation mode
	 * @return true, if is readable
	 * @deprecated since 6.3, use {@link UIAccessRightService} isReadable methods instead.
	 */
	@Deprecated
	public static boolean isReadable(final SystemService systemService, final ObjectType type,
			final PropertyDescriptor propertyDescriptor, final boolean creationMode)
	{
		boolean readable = checkAccessRights(systemService, type, propertyDescriptor, creationMode, AccessManager.READ);
		if (readable)
		{
			// property flags check
			readable = (propertyDescriptor.isReadable() || (creationMode && isInitial(propertyDescriptor)));
		}
		return readable;
	}

	/**
	 * Check access rights.
	 *
	 * @param systemService
	 *           the system service
	 * @param type
	 *           object type
	 * @param propertyDescriptor
	 *           property descriptor
	 * @param creationMode
	 *           the creation mode
	 * @param permissionCode
	 *           the permission code
	 * @return true, if successful
	 * @deprecated since 6.3, use {@link UIAccessRightService}'s isReadable and isWritable methods instead.
	 */
	@Deprecated
	private static boolean checkAccessRights(final SystemService systemService, final ObjectType type,
			final PropertyDescriptor propertyDescriptor, final boolean creationMode, final String permissionCode)
	{
		boolean hasRight = true;

		// user rights check
		if (propertyDescriptor instanceof ItemAttributePropertyDescriptor)
		{
			String baseTypeCode = null;
			if (((ItemAttributePropertyDescriptor) propertyDescriptor).getEnclosingType() instanceof VariantTypeModel)
			{
				baseTypeCode = ((ItemAttributePropertyDescriptor) propertyDescriptor).getTypeCode();
			}
			else if (type instanceof BaseType)
			{
				baseTypeCode = ((BaseType) type).getCode();
			}
			else if (type instanceof ObjectTemplate)
			{
				baseTypeCode = ((ObjectTemplate) type).getBaseType().getCode();
			}

			final ItemAttributePropertyDescriptor iapd = (ItemAttributePropertyDescriptor) propertyDescriptor;
			final List<AttributeDescriptorModel> ads = iapd.getAttributeDescriptors();
			if (ads.size() == 1)
			{
				hasRight = systemService.checkAttributePermissionOn(baseTypeCode, iapd.getAttributeQualifier(), permissionCode);
			}
			else
			{
				final AttributeDescriptorModel attributeDescriptorModel = iapd.getLastAttributeDescriptor();
				final ComposedTypeModel composedTypeModel = attributeDescriptorModel.getEnclosingType();
				hasRight = systemService.checkAttributePermissionOn(composedTypeModel.getCode(),
						attributeDescriptorModel.getQualifier(), permissionCode);
			}
		}

		return hasRight;
	}

	/**
	 * Checks if is initial.
	 *
	 * @deprecated since 6.3, use {@link PropertyService#isInitial(PropertyDescriptor)} instead.
	 * @param propertyDescriptor
	 *           the pd
	 * @return true, if is initial
	 */
	@Deprecated
	public static boolean isInitial(final PropertyDescriptor propertyDescriptor)
	{
		return getPropertyService().isInitial(propertyDescriptor);
	}


	/**
	 * @deprecated since 6.3, use {@link PropertyService#isPartof(PropertyDescriptor)} instead.
	 */
	@Deprecated
	public static boolean isPartof(final PropertyDescriptor propertyDescriptor)
	{
		return getPropertyService().isPartof(propertyDescriptor);
	}

	/**
	 * Converts item or collection of items to typed objects (or collection of them). If the value is no item/collection
	 * of items the unprocessed value is returned.
	 *
	 * @param typeService
	 *           the TypeService
	 * @param value
	 *           the value
	 * @return converted value
	 */
	public static Object item2Container(final TypeService typeService, final Object value)
	{
		Object wrappedObject = value;
		if (value instanceof Collection && !((Collection) value).isEmpty())
		{
			boolean wrapped = false;
			final int size = ((Collection) value).size();
			final Collection wrappedColl = (value instanceof Set ? new HashSet(size) : new ArrayList(size));
			for (final Object o : ((Collection) value))
			{
				if (o instanceof ItemModel)
				{
					wrapped = true;
					wrappedColl.add(typeService.wrapItem(o));
				}
				else
				{
					wrappedColl.add(o);
				}
			}
			if (wrapped)
			{
				wrappedObject = wrappedColl;
			}
		}
		else if (value instanceof ItemModel)
		{
			wrappedObject = typeService.wrapItem(value);
		}

		return wrappedObject;
	}

	/**
	 * Converts typed object or collection of typed objects to item (or collection of them. If the value is no typed
	 * object/collection of typed objects the unprocessed value is returned.
	 *
	 * @param typeService
	 *           the TypeService
	 * @param containerValue
	 *           the value
	 * @return converted value
	 */
	public static Object container2Item(final TypeService typeService, final Object containerValue)
	{
		Object unwrappedItem = null;
		if (containerValue instanceof Collection)
		{
			final Iterator<Object> iterator = ((Collection) containerValue).iterator();
			final int size = ((Collection) containerValue).size();
			final Collection unwrappedColl = (containerValue instanceof Set ? new HashSet(size) : new ArrayList(size));
			while (iterator.hasNext())
			{
				final Object item = iterator.next();
				if (item instanceof TypedObject)
				{
					unwrappedColl.add(((TypedObject) item).getObject());
				}
				else
				{
					unwrappedColl.add(item);
				}
			}
			unwrappedItem = unwrappedColl;
		}
		else if (containerValue instanceof TypedObject)
		{
			unwrappedItem = ((TypedObject) containerValue).getObject();
		}
		else
		{
			unwrappedItem = containerValue;
		}

		return unwrappedItem;
	}

	/**
	 * Check instance of category.
	 *
	 * @param typeService
	 *           the type service
	 * @param object
	 *           the object
	 * @return true, if successful
	 */
	public static boolean checkInstanceOfCategory(final TypeService typeService, final TypedObject object)
	{
		return typeService.getBaseType(CategoryModel._TYPECODE).isAssignableFrom(object.getType());
	}

	/**
	 * Check instance of catalog version.
	 *
	 * @param typeService
	 *           the type service
	 * @param object
	 *           the object
	 * @return true, if successful
	 */
	public static boolean checkInstanceOfCatalogVersion(final TypeService typeService, final TypedObject object)
	{
		return typeService.getBaseType(CatalogVersionModel._TYPECODE).isAssignableFrom(object.getType());
	}

	/**
	 * Check instance of product.
	 *
	 * @param typeService
	 *           the type service
	 * @param object
	 *           the object
	 * @return true, if successful
	 */
	public static boolean checkInstanceOfProduct(final TypeService typeService, final TypedObject object)
	{
		return typeService.getBaseType("Product").isAssignableFrom(object.getType());
	}

	/**
	 * Check instance of media.
	 *
	 * @param typeService
	 *           the type service
	 * @param object
	 *           the object
	 * @return true, if successful
	 */
	public static boolean checkInstanceOfMedia(final TypeService typeService, final TypedObject object)
	{
		return typeService.getBaseType("Media").isAssignableFrom(object.getType());
	}

	/**
	 * Multi edit.
	 *
	 * @param propertyDesc
	 *           the property desc
	 * @param languageIso
	 *           the language iso
	 * @param items
	 *           the items
	 * @param value
	 *           the value
	 * @return the list< string>
	 */
	//TODO: move to   ValueService and mark Deprecated
	public static List<String> multiEdit(final PropertyDescriptor propertyDesc, final String languageIso,
			final List<TypedObject> items, final Object value)
	{
		final List<String> errors = new ArrayList<String>();
		for (final TypedObject item : items)
		{
			final DefaultValueHandler valueHandler = new DefaultValueHandler(propertyDesc);
			try
			{
				valueHandler.setValue(item, value, languageIso);
			}
			catch (final ValueHandlerException e)
			{
				if (LOG.isDebugEnabled())
				{
					LOG.error("Could not set the value '" + value + "' for the property '" + propertyDesc.getName() + "' of object '"
							+ item + "' (Reason: " + e.getMessage() + ").", e);
				}
				if (item.getObject() instanceof ItemModel && !getModelService().isNew(item.getObject()))
				{
					errors.add(e.getMessage());
				}
			}
		}

		return errors;
	}

	/**
	 * Primitive value.
	 *
	 * @param value
	 *           the value
	 * @return true, if successful
	 *
	 * @deprecated since 6.3, use {@link BooleanUtils#toBoolean(Boolean)} instead.
	 */
	@Deprecated
	public static boolean primitiveValue(final Boolean value)
	{
		return value == null ? false : value.booleanValue();
	}

	/**
	 * Returns the primitive value for the given integer or 0 if null.
	 */
	public static int primitiveValue(final Integer value)
	{
		return value == null ? 0 : value.intValue();
	}

	/**
	 * Gets the core type service.
	 *
	 * @deprecated since 6.3, will be removed without replacement. Use proper dependency injection to get the
	 *             typeService.
	 *
	 * @return the core type service
	 */
	@Deprecated
	public static de.hybris.platform.servicelayer.type.TypeService getCoreTypeService()
	{
		return (de.hybris.platform.servicelayer.type.TypeService) SpringUtil.getBean("typeService");
	}

	/**
	 * Gets the model service.
	 *
	 * @deprecated since 6.3, will be removed without replacement. Use proper dependency injection to get the
	 *             modelService.
	 *
	 * @return the model service
	 */
	@Deprecated
	public static ModelService getModelService()
	{
		return (ModelService) SpringUtil.getBean("modelService");
	}

	private static PropertyService getPropertyService()
	{
		return (PropertyService) SpringUtil.getBean("cockpitPropertyService");
	}

	/**
	 * Gets the omitted properties.
	 *
	 * @param valueContainer
	 *           the value container
	 * @param template
	 *           the template
	 * @param creationMode
	 *           the creation mode
	 * @return the omitted properties
	 */
	//TODO: move to   ValueService and mark Deprecated
	public static Set<PropertyDescriptor> getOmittedProperties(final ObjectValueContainer valueContainer,
			final ObjectTemplate template, final boolean creationMode)
	{
		final Set<PropertyDescriptor> mandatorySet = getMandatoryAttributes(template, creationMode);
		final Set<PropertyDescriptor> omittedSet = new HashSet<PropertyDescriptor>();
		String dataLangIso = UISessionUtils.getCurrentSession().getGlobalDataLanguageIso();
		if (dataLangIso == null)
		{
			final Language lang = JaloSession.getCurrentSession().getSessionContext().getLanguage();
			if (lang != null)
			{
				dataLangIso = lang.getIsoCode();
			}
		}

		for (final PropertyDescriptor prop : mandatorySet)
		{
			boolean omitted = false;
			if (valueContainer.hasProperty(prop))
			{
				final ObjectValueHolder valueHolder = valueContainer.getValue(prop, prop.isLocalized() ? dataLangIso : null);
				final Object value = (valueHolder == null ? null : valueHolder.getCurrentValue());
				if (value == null)
				{
					omitted = true;
				}
				else if (PropertyDescriptor.Multiplicity.LIST.equals(prop.getMultiplicity())
						|| PropertyDescriptor.Multiplicity.SET.equals(prop.getMultiplicity()))
				{
					if (value instanceof Collection && ((Collection) value).isEmpty())
					{
						omitted = true;
					}
				}
				else if (PropertyDescriptor.TEXT.equals(prop.getEditorType()) && value.toString().length() == 0)
				{
					omitted = true;
				}
			}
			else
			{
				omitted = true;
			}

			if (omitted)
			{
				omittedSet.add(prop);
			}
		}

		return omittedSet;
	}

	/**
	 * Gets the omitted properties basing on given set of property descriptors. The descriptors are checked if mandatory
	 * and if true - added to an appropriate collection. After that a check is done if the required values are not empty.
	 * It allows to collect a list of omited properties this way.
	 *
	 * @param valueContainer
	 *           the value container
	 * @param descriptors
	 *           the descriptors
	 * @param creationMode
	 *           the creation mode
	 * @return the omitted properties
	 */
	//TODO: move to   ValueService and mark Deprecated
	public static Set<PropertyDescriptor> getOmittedProperties(final ObjectValueContainer valueContainer,
			final Set<PropertyDescriptor> descriptors, final boolean creationMode)
	{
		final Set<PropertyDescriptor> mandatoryDescriptors = new HashSet<PropertyDescriptor>();
		//Every given property descriptor is checked if mandatory. A set of mandatory descriptors will be built here this way.
		for (final PropertyDescriptor pd : descriptors)
		{
			if (LOG.isDebugEnabled())
			{
				LOG.info("Check if mandatory: " + pd.getQualifier());
			}

			if (isMandatory(pd, creationMode))
			{
				mandatoryDescriptors.add(pd);
				if (LOG.isDebugEnabled())
				{
					LOG.info("Attribute is mandatory: " + pd.getQualifier());
				}
			}
		}

		final Set<PropertyDescriptor> omittedSet = new HashSet<PropertyDescriptor>();
		String dataLangIso = UISessionUtils.getCurrentSession().getGlobalDataLanguageIso();
		if (dataLangIso == null)
		{
			final Language lang = JaloSession.getCurrentSession().getSessionContext().getLanguage();
			if (lang != null)
			{
				dataLangIso = lang.getIsoCode();
			}
		}

		for (final PropertyDescriptor prop : mandatoryDescriptors)
		{
			boolean omitted = false;
			if (valueContainer.hasProperty(prop))
			{
				final ObjectValueHolder valueHolder = valueContainer.getValue(prop, prop.isLocalized() ? dataLangIso : null);
				final Object value = (valueHolder == null ? null : valueHolder.getCurrentValue());
				if (value == null)
				{
					omitted = true;
				}
				else if (PropertyDescriptor.Multiplicity.LIST.equals(prop.getMultiplicity())
						|| PropertyDescriptor.Multiplicity.SET.equals(prop.getMultiplicity()))
				{
					if (value instanceof Collection && ((Collection) value).isEmpty())
					{
						omitted = true;
					}
				}
				else if (PropertyDescriptor.TEXT.equals(prop.getEditorType()) && value.toString().length() == 0)
				{
					omitted = true;
				}
				else if (PropertyDescriptor.FEATURE.equals(prop.getEditorType())
						&& (value instanceof FeatureValue && ((FeatureValue) value).getValue().toString().length() == 0))
				{
					omitted = true;
				}
			}
			else
			{
				omitted = true;
			}

			if (omitted)
			{
				omittedSet.add(prop);
			}
		}

		return omittedSet;
	}

	/**
	 * Gets the mandatory attributes. Can only be used in a zk environment. Use
	 * {@link #getMandatoryAttributes(ObjectType, boolean, PropertyService)} from outside zk.
	 *
	 * @param type
	 *           the type
	 * @param creationMode
	 *           the creation mode
	 * @return the mandatory attributes
	 */
	public static Set<PropertyDescriptor> getMandatoryAttributes(final ObjectType type, final boolean creationMode)
	{
		final Set<PropertyDescriptor> descriptors = new HashSet<PropertyDescriptor>();
		for (final PropertyDescriptor pd : type.getPropertyDescriptors())
		{
			if (isMandatory(pd, creationMode))
			{
				descriptors.add(pd);
			}
		}
		return descriptors;
	}


	/**
	 * Same as {@link #getMandatoryAttributes(ObjectType, boolean)} but uses {@link PropertyService} to check if
	 * mandatory.
	 */
	public static Set<PropertyDescriptor> getMandatoryAttributes(final ObjectType type, final boolean creationMode,
			final PropertyService propertyService)
	{
		final Set<PropertyDescriptor> descriptors = new HashSet<PropertyDescriptor>();
		for (final PropertyDescriptor pd : type.getPropertyDescriptors())
		{
			if (propertyService.isMandatory(pd, creationMode))
			{
				descriptors.add(pd);
			}
		}
		return descriptors;
	}

	/**
	 * Checks if is mandatory.
	 *
	 * @deprecated since 6.3, use {@link PropertyService#isMandatory(PropertyDescriptor, boolean)} instead.
	 * @param propertyDescriptor
	 *           the pd
	 * @param creationMode
	 *           the creation mode
	 * @return true, if is mandatory
	 */
	@Deprecated
	public static boolean isMandatory(final PropertyDescriptor propertyDescriptor, final boolean creationMode)
	{
		return getPropertyService().isMandatory(propertyDescriptor, creationMode);
	}

	/**
	 * Property belongs to.
	 *
	 * @param typeService
	 *           the type service
	 * @param types
	 *           the types
	 * @param propertyDescriptor
	 *           the pd
	 * @return true, if successful
	 */
	public static boolean propertyBelongsTo(final TypeService typeService, final Set<ObjectType> types,
			final PropertyDescriptor propertyDescriptor)
	{
		final ObjectType enclosingType = typeService
				.getObjectType(typeService.getTypeCodeFromPropertyQualifier(propertyDescriptor.getQualifier()));
		for (final ObjectType type : types)
		{
			if (enclosingType.isAssignableFrom(type))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the all default values.
	 *
	 * @param typeService
	 *           the type service
	 * @param template
	 *           the template
	 * @param languageIsos
	 *           the language isos
	 * @return the all default values
	 */
	// TODO: move to  ValueService and mark deprecated
	public static Map<PropertyDescriptor, Object> getAllDefaultValues(final TypeService typeService, final ObjectTemplate template,
			final Set<String> languageIsos)
	{
		final String typeCode = template.getBaseType().getCode();
		final ComposedType type = TypeManager.getInstance().getComposedType(typeCode);

		Map coreDefaultValues = Collections.EMPTY_MAP;
		SessionContext localCtx = null;
		try
		{
			localCtx = JaloSession.getCurrentSession().createLocalSessionContext();
			localCtx.setLanguage(null);
			localCtx.setAttribute(Item.INITIAL_CREATION_FLAG, Boolean.TRUE);
			coreDefaultValues = type.getAllDefaultValues();
		}
		finally
		{
			if (localCtx != null)
			{
				JaloSession.getCurrentSession().removeLocalSessionContext();
			}
		}

		Map<PropertyDescriptor, Object> defaultValues = Collections.EMPTY_MAP;
		if (!coreDefaultValues.isEmpty())
		{
			defaultValues = new HashMap<PropertyDescriptor, Object>();
			for (final Iterator it = coreDefaultValues.entrySet().iterator(); it.hasNext();)
			{
				final Map.Entry entry = (Map.Entry) it.next();
				final PropertyDescriptor propertyDescriptor = typeService
						.getPropertyDescriptor(typeCode + ItemAttributePropertyDescriptor.QUALIFIER_DELIM + (String) entry.getKey());
				if (propertyDescriptor != null)
				{
					final Object rawValue = entry.getValue();
					if (propertyDescriptor.isLocalized())
					{
						final C2LManager c2l = C2LManager.getInstance();
						final Map<Language, Object> rawLocalizedMap = (Map<Language, Object>) rawValue;
						final Map<String, Object> localizedMap = new HashMap<String, Object>(languageIsos.size());
						for (final String langIso : languageIsos)
						{
							final Language lang = c2l.getLanguageByIsoCode(langIso);
							final Object rawLocalizedValue = (rawLocalizedMap == null ? null : rawLocalizedMap.get(lang));
							localizedMap.put(langIso,
									testTypeTools.item2Container(typeService, getModelService().toModelLayer(rawLocalizedValue)));
						}
						if (!isEmptyLocalizedValue(localizedMap))
						{
							defaultValues.put(propertyDescriptor, localizedMap);
						}
					}
					else
					{
						if (!isEmptyValue(rawValue))
						{
							defaultValues.put(propertyDescriptor,
									testTypeTools.item2Container(typeService, getModelService().toModelLayer(rawValue)));
						}
					}
				}
			}
		}
		return defaultValues;
	}

	/**
	 * Checks if is empty value.
	 *
	 * @param value
	 *           the value
	 * @return true, if is empty value
	 */
	protected static boolean isEmptyValue(final Object value)
	{
		if (value == null)
		{
			return true;
		}
		else if (value instanceof Collection)
		{
			return ((Collection) value).isEmpty();
		}
		else if (value instanceof Map)
		{
			return ((Map) value).isEmpty();
		}
		return false;
	}

	/**
	 * Checks if is empty localized value.
	 *
	 * @param value
	 *           the value
	 * @return true, if is empty localized value
	 */
	protected static boolean isEmptyLocalizedValue(final Map<String, Object> value)
	{
		if (value == null)
		{
			return true;
		}
		else if (value.isEmpty())
		{
			return true;
		}
		else
		{
			boolean empty = true;
			for (final Map.Entry<String, Object> entry : value.entrySet())
			{
				if (!isEmptyValue(entry.getValue()))
				{
					empty = false;
					break;
				}
			}
			return empty;
		}
	}

	/**
	 * Gets all attributes and their possible values from TypedObject.
	 *
	 * @param typedObject
	 *           the typed object
	 * @return the all attributes map
	 */
	// TODO: move to  ValueService and mark deprecated, investigate if needed at all
	public static Map<String, Object> getAllAttributes(final TypedObject typedObject)
	{
		final HashMap<String, Object> result = new HashMap<String, Object>();
		final Set<PropertyDescriptor> propertyDescriptors = typedObject.getType().getPropertyDescriptors();

		final ObjectValueContainer valueContainer = createValueContainer(typedObject, propertyDescriptors,
				UISessionUtils.getCurrentSession().getSystemService().getAvailableLanguageIsos(), false);

		for (final ObjectValueHolder valueHolder : valueContainer.getAllValues())
		{
			final PropertyDescriptor propertyDescriptor = valueHolder.getPropertyDescriptor();
			if (propertyDescriptor.isLocalized())
			{
				Map<String, Object> locMap = (Map<String, Object>) result.get(propertyDescriptor.getQualifier());
				if (locMap == null)
				{
					locMap = new HashMap<String, Object>();
				}
				locMap.put(valueHolder.getLanguageIso(), valueHolder.getCurrentValue());
				result.put(propertyDescriptor.getQualifier(), locMap);
			}
			else
			{
				result.put(propertyDescriptor.getQualifier(), valueHolder.getCurrentValue());
			}
		}

		return result;
	}

	/**
	 * Get the localized name of an hybris enum. If there is no name set, the code will be returned.
	 *
	 * @param hybrisEnum
	 *           the hybris enum
	 * @return the localized name of the enum or the code if the enum is no EnumerationValue
	 */
	public static String getEnumName(final HybrisEnumValue hybrisEnum)
	{
		final EnumerationService enumerationService = getEnumerationService();
		final String name = enumerationService.getEnumerationName(hybrisEnum,
				UISessionUtils.getCurrentSession().getGlobalDataLocale());
		return StringUtils.isEmpty(name) ? hybrisEnum.getCode() : name;
	}

	private static EnumerationService getEnumerationService()
	{
		return (EnumerationService) SpringUtil.getBean("enumerationService");
	}

	/**
	 * Filters out removed items.
	 *
	 * @param items
	 *           the items
	 */
	public static void filterOutRemovedItems(final List<? extends TypedObject> items)
	{
		CollectionUtils.filter(items, new Predicate()
		{

			@Override
			public boolean evaluate(final Object object)
			{
				return ((TypedObject) object).getObject() != null;
			}
		});
	}

	/**
	 * Reads the value of the given pd and retuns is as a String. If no readable,
	 * ValueHandler.NOT_READABLE_VALUE.toString() will be returned. If some problem occurs, an empty string will be
	 * returned.
	 */
	public static String getPropertyValueAsString(final ValueService valueService, final TypedObject object,
			final PropertyDescriptor propertyDescriptor)
	{
		try
		{
			final Object value = valueService.getValue(object, propertyDescriptor);
			return value == null ? "" : value.toString();
		}
		catch (final ValueHandlerPermissionException e)
		{
			return ValueHandler.NOT_READABLE_VALUE.toString();
		}
		catch (final ValueHandlerException e)
		{
			return "";
		}
	}

	/**
	 * Reads the value of the given pd. If no readable, ValueHandler.NOT_READABLE_VALUE will be returned. If some problem
	 * occurs, null will be returned.
	 */
	public static Object getPropertyValue(final ValueService valueService, final TypedObject object,
			final PropertyDescriptor propertyDescriptor)
	{
		try
		{
			return valueService.getValue(object, propertyDescriptor);
		}
		catch (final ValueHandlerPermissionException e)
		{
			return ValueHandler.NOT_READABLE_VALUE;
		}
		catch (final ValueHandlerException e)
		{
			return null;
		}
	}

	/**
	 * <p>
	 * Creates a new collection with the same or similar* type to {@code clazz}. The created collection will contains
	 * elements stored by {@code items} collection.
	 * </p>
	 * <p>
	 * *If cannot create a new instance with the same type, then it will creates:
	 * </p>
	 * <ul>
	 * <li>{@link HashSet} for sets</li>
	 * <li>{@link ArrayList} for other collections</li>
	 * </ul>
	 *
	 * @param clazz
	 *           the requested type for new collection.
	 * @param items
	 *           the collection whose elements are to be placed into this collection.
	 * @return the new collection which contains
	 */
	public static <E> Collection<E> createCollection(final Class<? extends Collection> clazz, final Collection<E> items)
	{
		try
		{
			final Constructor<? extends Collection> constructor = clazz.getConstructor(Collection.class);
			return constructor.newInstance(items);
		}
		catch (final Exception e)
		{
			LOG.error("Cannot instantiate " + clazz, e);
		}

		if (Set.class.isAssignableFrom(clazz))
		{
			return new HashSet(items);
		}
		else
		{
			return new ArrayList(items);
		}
	}
}