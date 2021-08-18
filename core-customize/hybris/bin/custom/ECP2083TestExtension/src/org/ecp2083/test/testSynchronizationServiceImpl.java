/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved
 */
package org.ecp2083.test;

import de.hybris.platform.catalog.daos.ItemSyncTimestampDao;
import de.hybris.platform.catalog.jalo.Catalog;
import de.hybris.platform.catalog.jalo.CatalogManager;
import de.hybris.platform.catalog.jalo.CatalogVersion;
import de.hybris.platform.catalog.jalo.ItemSyncTimestamp;
import de.hybris.platform.catalog.jalo.SyncItemCronJob;
import de.hybris.platform.catalog.jalo.SyncItemJob;
import de.hybris.platform.catalog.jalo.SyncItemJob.CompletionInfo;
import de.hybris.platform.catalog.jalo.SyncItemJob.SyncItemCopyContext;
import de.hybris.platform.catalog.jalo.synchronization.CatalogVersionSyncCronJob;
import de.hybris.platform.catalog.model.CatalogVersionModel;
import de.hybris.platform.catalog.model.ItemSyncTimestampModel;
import de.hybris.platform.catalog.model.SyncItemJobModel;
import de.hybris.platform.category.jalo.Category;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.cockpit.daos.SynchronizationServiceDao;
import de.hybris.platform.cockpit.jalo.CockpitManager;
import de.hybris.platform.cockpit.model.meta.BaseType;
import de.hybris.platform.cockpit.model.meta.ObjectType;
import de.hybris.platform.cockpit.model.meta.PropertyDescriptor;
import de.hybris.platform.cockpit.model.meta.TypedObject;
import de.hybris.platform.cockpit.services.SystemService;
import de.hybris.platform.cockpit.services.impl.AbstractServiceImpl;
import de.hybris.platform.cockpit.services.sync.SynchronizationService;
import de.hybris.platform.cockpit.services.values.ObjectValueContainer;
import de.hybris.platform.cockpit.services.values.ObjectValueContainer.ObjectValueHolder;
import de.hybris.platform.cockpit.services.values.ObjectValueHandlerRegistry;
import de.hybris.platform.cockpit.session.UISessionUtils;
import de.hybris.platform.cockpit.util.TypeTools;
import de.hybris.platform.core.PK;
import de.hybris.platform.core.model.ItemModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.cronjob.jalo.Job;
import de.hybris.platform.jalo.Item;
import de.hybris.platform.jalo.JaloSession;
import de.hybris.platform.jalo.SearchResult;
import de.hybris.platform.jalo.SessionContext;
import de.hybris.platform.jalo.product.Product;
import de.hybris.platform.jalo.type.ComposedType;
import de.hybris.platform.jalo.type.TypeManager;
import de.hybris.platform.jalo.user.User;
import de.hybris.platform.util.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;


/**
 * Contains all necessary method responsible for synchronization activity
 *
 * @spring.bean synchronizationService
 */
public class testSynchronizationServiceImpl extends AbstractServiceImpl implements SynchronizationService
{
	private static final Logger LOG = LoggerFactory.getLogger(testSynchronizationServiceImpl.class);
	public static final String DISABLE_RESTRICTION = "disableRestrictions";
	private static final char RIGHT_ARROW = '\u2192';
	private static final String LEFT_ROUND_BRACKET = " (";
	private static final String RIGHT_ROUND_BRACKET = ") ";
	private static final String LEFT_ANGLE_BRACKET = "< ";
	private static final String RIGHT_ANGLE_BRACKET = "> ";
	private static final String INIT_SYNCHRONIZATION_CHECK_METHOD = "catalog.synchronization.initialinit.check.timestamps";

    private SystemService systemService;
    private ObjectValueHandlerRegistry valueHandlerRegistry;
    private final CatalogManager catalogManager = CatalogManager.getInstance();
	private final TypeManager typeManager = TypeManager.getInstance();
	private SynchronizationServiceDao synchronizationServiceDao;
	private Map<String, List<String>> relatedReferencesTypesMap = new HashMap<String, List<String>>();
	private int relatedReferencesMaxDepth = -1;
	private Map<ObjectType, Set<PropertyDescriptor>> relatedProperties = new ConcurrentHashMap<>();
	private ItemSyncTimestampDao itemSyncTimestampDao;
	private Boolean disabledSearchRestrictions = null;

	protected Map<String, List<String>> getRelatedReferencesTypesMap()
	{
		return relatedReferencesTypesMap;
	}

	public void setRelatedReferencesTypesMap(final Map<String, List<String>> relatedReferencesTypesMap)
	{
		this.relatedReferencesTypesMap = relatedReferencesTypesMap;
	}

	protected int getRelatedReferencesMaxDepth()
	{
		return relatedReferencesMaxDepth;
	}

	public void setRelatedReferencesMaxDepth(final int relatedReferencesMaxDepth)
	{
		this.relatedReferencesMaxDepth = relatedReferencesMaxDepth;
	}

	public TypeManager getTypeManager()
	{
		return typeManager;
	}


	public CatalogManager getCatalogManager()
	{
		return catalogManager;
	}


	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * de.hybris.platform.cockpit.services.sync.SynchronizationService#getSyncSources(de.hybris.platform.cockpit.model
	 * .meta.TypedObject)
	 */
	@Override
	public Collection<TypedObject> getSyncSources(final TypedObject object)
	{
		return getTypeService().wrapItems(synchronizationServiceDao.getSyncSources((ItemModel) object.getObject()).getResult());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * de.hybris.platform.cockpit.services.sync.SynchronizationService#getSyncTargets(de.hybris.platform.cockpit.model
	 * .meta.TypedObject)
	 */
	@Override
	public Collection<TypedObject> getSyncTargets(final TypedObject object)
	{
		return getTypeService().wrapItems(synchronizationServiceDao.getSyncTargets((ItemModel) object.getObject()).getResult());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * de.hybris.platform.cockpit.services.sync.SynchronizationService#getSyncSourcesAndTargets(de.hybris.platform.cockpit
	 * .model.meta.TypedObject)
	 */
	@Override
	public Collection<TypedObject> getSyncSourcesAndTargets(final TypedObject object)
	{
		return getTypeService().wrapItems(
				synchronizationServiceDao.getSyncSourcesAndTargets((ItemModel) object.getObject()).getResult());
	}


	@Override
	public void performPullSynchronization(final List<TypedObject> targetItems)
	{
		final Map<CatalogVersionModel, Set<TypedObject>> syncMap = new HashMap<CatalogVersionModel, Set<TypedObject>>();
		for (final TypedObject typedObject : targetItems)
		{
			final SyncContext syncContext = createPullSyncStatusContext(typedObject);


			//we don't support multiple sources atm
			if (syncContext.getSourceItemModels().size() != 1)
			{
				continue;
			}

			final CatalogVersionModel targetCatalogVersion = getCatalogVersionForItem(typedObject);
			Set<TypedObject> tcProductSet = null;
			if (syncMap.containsKey(targetCatalogVersion))
			{
				tcProductSet = syncMap.get(targetCatalogVersion);
			}
			else
			{
				tcProductSet = new HashSet<TypedObject>();
				syncMap.put(targetCatalogVersion, tcProductSet);
			}
			try
			{
				final ItemModel sourceModel = syncContext.getSourceItemModels().iterator().next();
				final TypedObject sourceProduct = getTypeService().wrapItem(sourceModel);
				tcProductSet.add(sourceProduct);
			}
			catch (final NoSuchElementException e)
			{
				//fine with that
			}
		}

		for (final Entry<CatalogVersionModel, Set<TypedObject>> objectSetEntry : syncMap.entrySet())
		{
			final CatalogVersionModel version = objectSetEntry.getKey();
			final Set<TypedObject> items = objectSetEntry.getValue();
			final List<TypedObject> itemsList = new ArrayList<TypedObject>(items);

			processSingleRuleSync(version, itemsList, null);
		}

	}

	@Override
	public Collection<TypedObject> performSynchronization(final Collection<? extends Object> items,
			final List<String> syncJobPkList, final CatalogVersionModel targetCatalogVersion, final String qualifier)
	{
		if (items == null || items.isEmpty())
		{
			return Collections.EMPTY_LIST;
		}
		final List<TypedObject> ret = new ArrayList<TypedObject>();
		for (final Object object : items)
		{
			TypedObject wrappedItem = null;
			if (object instanceof Item)
			{
				wrappedItem = getTypeService().wrapItem(((Item) object).getPK());
			}
			else if (object instanceof ItemModel)
			{
				wrappedItem = getTypeService().wrapItem(((ItemModel) object).getPk());
			}
			else if (object instanceof TypedObject)
			{
				wrappedItem = (TypedObject) object;
			}

			if (wrappedItem != null)
			{
				ret.add(wrappedItem);
				ret.addAll(getRelatedReferences(wrappedItem));
			}
			else
			{
				LOG.error("Couldn't wrap item '" + object + "' into a typed object.");
			}
		}

		if (syncJobPkList == null)
		{
			processSingleRuleSync(targetCatalogVersion, ret, qualifier);
		}
		else
		{
			processMultiRuleSync(syncJobPkList, ret);
		}
		return ret;

	}

	/**
	 * Responsible for synchronizing that product which have only one sync rule.
	 *
	 * @param target
	 *           - target catalog version
	 * @param products
	 *           - product which will be synchronizing
	 */
	@SuppressWarnings(
	{ "deprecation", "unchecked" })
	private void processSingleRuleSync(final CatalogVersionModel target, final List<TypedObject> products, final String qualifier)
	{
		//the synchronization rule wasn't specified
		final Map<Object, List<TypedObject>> syncPool = new HashMap<Object, List<TypedObject>>();

		if (products == null || products.isEmpty())
		{
			return;
		}

		final boolean isCategory = products.get(0).getObject() instanceof CategoryModel;

		SyncItemJob existingSyncJob = null;

		for (final TypedObject product : products)
		{
			final CatalogVersion sourceCatalogVersion = retrieveCatalogVersion(product);
			/*
			 * sourceCatalogVersion.isActive().booleanValue()?
			 */
			if (!isVersionSynchronizedAtLeastOnce(sourceCatalogVersion.getSynchronizations()))
			{
				continue;
			}

			if (target == null)
			{
				existingSyncJob = catalogManager.getSyncJobFromSource(sourceCatalogVersion);
			}
			else
			{
				if (qualifier != null)
				{
					existingSyncJob = catalogManager.getSyncJob(sourceCatalogVersion, (CatalogVersion) modelService.getSource(target),
							qualifier);
				}
				else
				{
					existingSyncJob = catalogManager.getSyncJob(sourceCatalogVersion, (CatalogVersion) modelService.getSource(target));
				}
			}

			if (existingSyncJob != null)
			{
				if (checkUserRightToTargetVersion(existingSyncJob))
				{
					addToPool(syncPool, existingSyncJob, product);
				}
			}
		}
		for (final Entry<Object, List<TypedObject>> entry : syncPool.entrySet())
		{
			existingSyncJob = (SyncItemJob) entry.getKey();
			final SyncItemCronJob synchronizeJob = existingSyncJob.newExecution();

			if (!isCategory)
			{
				final List<PK[]> pkArrayList = new ArrayList<PK[]>();
				for (final TypedObject product : entry.getValue())
				{
					final PK[] pkArray = new PK[2];
					pkArray[0] = ((ItemModel) product.getObject()).getPk();
					pkArray[1] = null;
					pkArrayList.add(pkArray);
				}
				if (synchronizeJob instanceof CatalogVersionSyncCronJob)
				{
					((CatalogVersionSyncCronJob) synchronizeJob).addPendingItems(pkArrayList);
				}
				else
				{
					synchronizeJob.addPendingItems(pkArrayList, false);
				}
			}
			else
			{
				SessionContext ctx = null;
				try
				{
					ctx = JaloSession.getCurrentSession().createLocalSessionContext();
					ctx.setAttribute(DISABLE_RESTRICTION, isSearchRestrictionDisabled());

					final List<Category> rawCategory = new ArrayList<Category>();
					for (final TypedObject typedObject : syncPool.get(existingSyncJob))
					{
						rawCategory.add((Category) extractJaloItem(typedObject.getObject()));
					}
					existingSyncJob.addCategoriesToSync(synchronizeJob, rawCategory, true, true);
				}
				finally
				{
					if (ctx != null)
					{
						JaloSession.getCurrentSession().removeLocalSessionContext();
					}
				}
			}
			synchronizeJob.setConfigurator(new CockpitDummySyncConfigurator(synchronizeJob, existingSyncJob));
			existingSyncJob.perform(synchronizeJob, true);
		}
	}

	@SuppressWarnings("deprecation")
	protected class CockpitDummySyncConfigurator implements SyncItemCronJob.Configurator
	{
		protected final SyncItemJob job;
		protected final SyncItemCronJob sicj;

		CockpitDummySyncConfigurator(final SyncItemCronJob sicj, final SyncItemJob job)
		{
			this.job = job;
			this.sicj = sicj;
		}

		@Override
		public void configureCronjob(final SyncItemCronJob sicj, final SyncItemCopyContext sicc)
		{
			LOG.info("Default Synch Configuration");
		}

		@Override
		public CompletionInfo getCompletionInfo()
		{
			return job.getCompletionInfo(sicj);
		}
	}

	/**
	 * Responsible for synchronizing that product which have many synchronization rules
	 *
	 * @param syncJobPkList
	 *           - synchronization primary key list
	 * @param items
	 */
	@SuppressWarnings(
	{ "deprecation", "unchecked" })
	private <E extends TypedObject> void processMultiRuleSync(final List<String> syncJobPkList, final List<E> items)
	{

		if (items == null || items.isEmpty())
		{
			return;
		}

		final boolean isCategory = items.get(0).getObject() instanceof CategoryModel;

		for (final String syncJobPk : syncJobPkList)
		{
			//in order to process synchronization we use one particular job
			final SyncItemJob syncJob = JaloSession.getCurrentSession().getItem(PK.parse(syncJobPk));
			final SyncItemCronJob synchronizeJob = syncJob.newExecution();

			if (!isCategory)
			{
				final List<PK[]> pkArrayList = new ArrayList<PK[]>();
				for (final E product : items)
				{

					final CatalogVersion sourceCatalogVersion = retrieveCatalogVersion(product);
					/*
					 * sourceCatalogVersion.isActive().booleanValue()?
					 */
					if (!isVersionSynchronizedAtLeastOnce(sourceCatalogVersion.getSynchronizations()))
					{
						continue;
					}

					if (sourceCatalogVersion.getSynchronizations().contains(syncJob))
					{

						final PK[] pkArray = new PK[2];
						pkArray[0] = ((ItemModel) product.getObject()).getPk();
						pkArray[1] = null;
						pkArrayList.add(pkArray);
					}
				}
				if (synchronizeJob instanceof CatalogVersionSyncCronJob)
				{
					((CatalogVersionSyncCronJob) synchronizeJob).addPendingItems(pkArrayList);
				}
				else
				{
					synchronizeJob.addPendingItems(pkArrayList, false);
				}
			}
			else
			{

				SessionContext ctx = null;
				try
				{
					ctx = JaloSession.getCurrentSession().createLocalSessionContext();
					ctx.setAttribute(DISABLE_RESTRICTION, isSearchRestrictionDisabled());
					syncJob.addCategoriesToSync(synchronizeJob, getRawObjects(items), true, true);
				}
				finally
				{
					if (ctx != null)
					{
						JaloSession.getCurrentSession().removeLocalSessionContext();
					}
				}
			}
			synchronizeJob.setConfigurator(new CockpitDummySyncConfigurator(synchronizeJob, syncJob));
			syncJob.perform(synchronizeJob, true);
		}
	}


	private <E extends TypedObject> List getRawObjects(final List<E> source)
	{
		final List ret = new ArrayList();
		for (final TypedObject element : source)
		{
			ret.add(extractJaloItem(element.getObject()));
		}
		return ret;
	}

	/**
	 * Method that collects all items according to its catalog version
	 *
	 * @param pool
	 *           - container
	 * @param key
	 *           - passed key (catalog version)
	 * @param item
	 *           - particular item
	 */
	private <E extends TypedObject> void addToPool(final Map<Object, List<E>> pool, final Object key, final E item)
	{
		List<E> value = pool.get(key);
		if (value == null)
		{
			value = new ArrayList<E>();
			pool.put(key, value);
		}
		value.add(item);
	}

	/**
	 * Retrieves source catalog version for given product
	 *
	 * @param item
	 *           - passed item
	 */
	public <E extends TypedObject> CatalogVersion retrieveCatalogVersion(final E item)
	{
		return catalogManager.getCatalogVersion(extractJaloItem(item.getObject()));
	}

	@Override
	public int getPullSyncStatus(final TypedObject product)
	{
		return createPullSyncStatusContext(product).getPullSyncStatus();
	}

	private SyncContextImpl createPullSyncStatusContext(final TypedObject product)
	{
		final SyncContextImpl ret = new SyncContextImpl();
		int pullSync = SYNCHRONIZATION_NOT_AVAILABLE;
		final CatalogVersion targetCatalogVersion = retrieveCatalogVersion(product);

		final Item productItem = extractJaloItem(product.getObject());
		Product sourceProduct = null;
		final List<ItemSyncTimestamp> synchronizationSources = CatalogManager.getInstance().getSynchronizationSources(productItem);

		// just valid for one source atm
		if (synchronizationSources.size() == 1)
		{
			for (final ItemSyncTimestamp itemSyncTimestamp : synchronizationSources)
			{
				final Item sourceItem = itemSyncTimestamp.getSourceItem();
				if (!(sourceItem instanceof Product))
				{
					continue;
				}
				final CatalogVersion sourceCatalogVersion = CatalogManager.getInstance().getCatalogVersion(sourceItem);
				if (sourceCatalogVersion == null || targetCatalogVersion == null || sourceCatalogVersion.equals(targetCatalogVersion))
				{
					continue;
				}

				final SyncItemJob syncJob = CatalogManager.getInstance().getSyncJob(sourceCatalogVersion, targetCatalogVersion);
				if (!checkUserRight(syncJob))
				{
					pullSync = SYNCHRONIZATION_NOT_AVAILABLE;
					continue;
				}
				sourceProduct = (Product) sourceItem;
				pullSync = isSynchronized(sourceProduct, targetCatalogVersion, syncJob, productItem, null);
				try
				{
					ret.addSourceItemModel((ItemModel) getModelService().get(sourceProduct));
				}
				catch (final Exception e)
				{
					LOG.error(e.getMessage());
				}
			}
		}

		ret.setPullSyncStatus(pullSync);
		return ret;
	}

	/**
	 * TODO FIXME - Find way to get appropriate synchronization status, condition that is used below is not correct.
	 * Maybe there are or will be some core mechanisms to achieve that! Checks whether given product is synchronized
	 *
	 * @param object
	 *           - passed item
	 */
	@Override
	public final int isObjectSynchronized(final TypedObject object)
	{
		return getSyncInfo(object).getSyncStatus();
	}

	@SuppressWarnings("deprecation")
	protected SyncInfo getSyncInfo(final TypedObject object)
	{
		final SyncInfo syncInfoSimple = getSyncInfoSimple(object);
		final Set<TypedObject> affectedItems = new HashSet<TypedObject>(syncInfoSimple.getAffectedItems());
		int syncStatus = syncInfoSimple.getSyncStatus();

		for (final TypedObject typedObject : getRelatedReferences(object))
		{
			affectedItems.add(typedObject);
			if (getSyncInfoSimple(typedObject).getSyncStatus() == SYNCHRONIZATION_NOT_OK)
			{
				syncStatus = SYNCHRONIZATION_NOT_OK;
				break;
			}
		}

		syncInfoSimple.setSyncStatus(syncStatus);
		syncInfoSimple.setAffectedItems(affectedItems);

		return syncInfoSimple;
	}




	@SuppressWarnings("deprecation")
	protected SyncInfo getSyncInfoSimple(final TypedObject object)
	{
		int status = SYNCHRONIZATION_OK;
		final Set<TypedObject> affectedItems = new HashSet<TypedObject>();

		final SyncInfo ret = new SyncInfo();
		final CatalogVersion sourceCatalogVersion = retrieveCatalogVersion(object);
		if (sourceCatalogVersion == null)
		{
			status = SynchronizationService.SYNCHRONIZATION_NOT_AVAILABLE;
		}
		else
		{
			final List synchronizationRules = sourceCatalogVersion.getSynchronizations();

			if (synchronizationRules == null || synchronizationRules.isEmpty()
					|| !isTypeInRootTypes(object.getType().getCode(), synchronizationRules))
			{
				status = SynchronizationService.SYNCHRONIZATION_NOT_AVAILABLE;
			}
			else if (!isVersionSynchronizedAtLeastOnce(synchronizationRules))
			{
				status = SynchronizationService.INITIAL_SYNC_IS_NEEDED;
			}
			else
			{
				for (final Iterator iter = synchronizationRules.iterator(); iter.hasNext();)
				{
					final Object rawObject = iter.next();
					if (rawObject instanceof SyncItemJob)
					{
						final SyncItemJob syncTask = (SyncItemJob) rawObject;
						if (!checkUserRight(syncTask))
						{
							status = SYNCHRONIZATION_NOT_AVAILABLE;
							continue;
						}
						final CatalogVersion targetCatalogVersion = syncTask.getTargetVersion();
						status = isSynchronized(extractJaloItem(object.getObject()), targetCatalogVersion, syncTask, null,
								affectedItems);
						if (status == SYNCHRONIZATION_NOT_OK)
						{
							break;
						}
					}
				}
			}
		}

		ret.setSyncStatus(status);
		ret.setAffectedItems(affectedItems);
		return ret;
	}


	/**
	 * Checks whether synchronized type is in synchronization attributes
	 *
	 * @param typeCode
	 *           - particular type
	 * @param syncJobs
	 */
	@SuppressWarnings("deprecation")
	private boolean isTypeInRootTypes(final String typeCode, final List<Job> syncJobs)
	{
		for (final Job job : syncJobs)
		{
			final SyncItemJob catalogVersionSynJob = (SyncItemJob) job;

			final ComposedType objectType = TypeManager.getInstance().getComposedType(typeCode);
			final List<ComposedType> rootTypes = catalogVersionSynJob.getRootTypes();
			for (final ComposedType composedType : rootTypes)
			{
				if (composedType.isAssignableFrom(objectType))
				{
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	private int isSynchronized(final Item sourceItem, final CatalogVersion targetVersion, final SyncItemJob syncTask,
			final Item targetItem, final Set<TypedObject> affectedItemsReturn)
	{
		int ret = SYNCHRONIZATION_OK;
		Item targetCounterPart = null;

		if (targetItem == null)
		{
			SessionContext ctx = null;
			try
			{
				ctx = JaloSession.getCurrentSession().createLocalSessionContext();
				ctx.setAttribute(DISABLE_RESTRICTION, isSearchRestrictionDisabled());
				targetCounterPart = CatalogManager.getInstance().getCounterpartItem(sourceItem, targetVersion);
			}
			finally
			{
				if (ctx != null)
				{
					JaloSession.getCurrentSession().removeLocalSessionContext();
				}
			}
		}
		else
		{
			targetCounterPart = targetItem;
		}

		if (targetCounterPart == null)
		{
			ret = SYNCHRONIZATION_NOT_OK;
		}
		else
		{
			if (catalogManager.getLastSyncModifiedTime(syncTask, sourceItem, targetCounterPart) == null)
			{
				ret = SYNCHRONIZATION_NOT_OK;
			}
			else
			{
				final long secondSyncTime = catalogManager.getLastSyncModifiedTime(syncTask, sourceItem, targetCounterPart).getTime();
				if (secondSyncTime < sourceItem.getModificationTime().getTime())
				{
					ret = SYNCHRONIZATION_NOT_OK;
				}
			}
			if (affectedItemsReturn != null)
			{
				affectedItemsReturn.add(getTypeService().wrapItem(targetCounterPart));
			}
		}
		return ret;
	}

	/**
	 * Checks whether particular product version was synchronized at least once!
	 */
	@Override
	@SuppressWarnings(
	{ "unchecked", "deprecation" })
	public boolean isVersionSynchronizedAtLeastOnce(final List list)
	{
		List<SyncItemJob> synchronizationRules = new ArrayList<SyncItemJob>();
		if (list == null || list.isEmpty())
		{
			return false;
		}

		for (final Object object : list)
		{
			if (object instanceof CatalogVersionModel)
			{
				final CatalogVersion sourceCatalogVersion = (CatalogVersion) modelService.getSource(((CatalogVersionModel) object));
				synchronizationRules = sourceCatalogVersion.getSynchronizations();
			}
			else if (object instanceof CategoryModel)
			{
				final Category category = (Category) modelService.getSource(((CategoryModel) object));
				final CatalogVersion sourceCatalogVersion = catalogManager.getCatalogVersion(category);
				synchronizationRules = sourceCatalogVersion.getSynchronizations();
			}
			else
			{
				synchronizationRules = list;
			}

			for (final SyncItemJob job : synchronizationRules)
			{
				if(checkIfSynchronizationWasExecuted((SyncItemJobModel) getModelService().get(job)))
				{
					return true;
				}
			}
		}
		return false;
	}

	private boolean checkIfSynchronizationWasExecuted(final SyncItemJobModel syncItemJobModel)
	{
		if(useSyncTimestamps())
		{
			Collection<ItemSyncTimestampModel> syncRecords = itemSyncTimestampDao.findSyncTimestampsByCatalogVersion(syncItemJobModel.getSourceVersion(),1);
			return CollectionUtils.isNotEmpty(syncRecords);
		}
		return CollectionUtils.isNotEmpty(syncItemJobModel.getCronJobs());
	}

	private boolean useSyncTimestamps()
	{
		return Config.getBoolean(INIT_SYNCHRONIZATION_CHECK_METHOD, true);
	}

	/**
	 * Retrieves a time of last executed synchronization on the given product
	 *
	 * @param product
	 *           - passed product
	 * @return - time in milliseconds
	 */
	@SuppressWarnings("unchecked")
	public Long getLastTargetProductSyncTimestamp(final Item product)
	{
		final JaloSession jaloSession = JaloSession.getCurrentSession();
		SessionContext ctx = null;
		try
		{
			ctx = JaloSession.getCurrentSession().createLocalSessionContext();
			ctx.setAttribute(DISABLE_RESTRICTION, isSearchRestrictionDisabled());
			if (product instanceof Product)
			{
				final StringBuilder query = new StringBuilder();
				query.append("SELECT " + "{TS:PK} FROM " + "{Product as p JOIN ItemSyncTimestamp as TS  "
						+ "	ON {p:PK}={TS:targetItem} and {p:PK} = " + product.getPK() + "}");

				final SearchResult<ItemSyncTimestamp> results = jaloSession.getFlexibleSearch().search(query.toString(),
						ItemSyncTimestamp.class);

				if (!results.getResult().isEmpty())
				{
					return Long.valueOf(results.getResult().iterator().next().getLastSyncTime().getTime());
				}
			}
		}
		finally
		{
			if (ctx != null)
			{
				JaloSession.getCurrentSession().removeLocalSessionContext();
			}
		}
		return Long.valueOf(0L);
	}



	/**
	 * Checks if the restrictions should be disabled for the internal search operations of the service. By default the
	 * method returns <code>TRUE<code>. You can change this behavior by setting <code>false</code> value using
	 * {@link #setSearchRestrictionsDisabled(boolean)} injection.
	 */
	protected Boolean isSearchRestrictionDisabled()
	{
		if (Boolean.FALSE.equals(disabledSearchRestrictions))
		{
			return Boolean.FALSE;
		}
		return Boolean.TRUE;
	}

	protected boolean checkRootOrSubType(final TypedObject item, final SyncItemJob syncJob)
	{
		return isTypeInRootTypes(item.getType().getCode(), Collections.singletonList((Job) syncJob));
	}

	@Override
	@SuppressWarnings(
	{ "unchecked", "deprecation" })
	public List<SyncItemJobModel>[] getTargetCatalogVersions(final TypedObject item)
	{
		final List[] ret = new List[2];
		final List<SyncItemJobModel> accessibleRules = new ArrayList<SyncItemJobModel>();
		final List<SyncItemJobModel> forbiddenRules = new ArrayList<SyncItemJobModel>();
		final CatalogVersion sourceCatalogVersion = retrieveCatalogVersion(item);
		final List syncCollection = sourceCatalogVersion.getSynchronizations();
		for (final Object synchronization : syncCollection)
		{
			if (synchronization instanceof SyncItemJob)
			{
				final SyncItemJob syncJob = (SyncItemJob) synchronization;

				if (!checkUserRight(syncJob))
				{
					continue;
				}
				final boolean init = isVersionSynchronizedAtLeastOnce(Collections.singletonList(syncJob));
				if (!init)
				{
					forbiddenRules.add((SyncItemJobModel) modelService.get(syncJob));
				}
				else if (syncJob.getTargetVersion() != null && checkRootOrSubType(item, syncJob))
				{
					accessibleRules.add((SyncItemJobModel) modelService.get(syncJob));
				}
			}
		}
		ret[0] = accessibleRules;
		ret[1] = forbiddenRules;
		return ret;
	}


	@Override
	@SuppressWarnings(
	{ "unchecked", "deprecation" })
	public List<SyncItemJobModel>[] getSyncJobs(final ItemModel source, final ObjectType objectType)
	{
		final List<SyncItemJobModel>[] ret = new ArrayList[2];
		CatalogVersion sourceCatalogVersion = null;
		if (source instanceof CatalogVersionModel)
		{
			sourceCatalogVersion = ((CatalogVersion) modelService.getSource(source));
		}
		else if (source instanceof CategoryModel)
		{
			final Category category = ((Category) modelService.getSource(source));
			sourceCatalogVersion = catalogManager.getCatalogVersion(category);
		}

		final List<SyncItemJobModel> accesibleRules = new ArrayList<SyncItemJobModel>();
		final List<SyncItemJobModel> forbidenRules = new ArrayList<SyncItemJobModel>();
		if (sourceCatalogVersion != null)
		{
			final List syncCollection = sourceCatalogVersion.getSynchronizations();
			for (final Object synchronization : syncCollection)
			{
				if (synchronization instanceof SyncItemJob)
				{
					final SyncItemJob syncJob = (SyncItemJob) synchronization;
					final boolean init = isVersionSynchronizedAtLeastOnce(Collections.singletonList(syncJob));
					if (!checkUserRight(syncJob))
					{
						continue;
					}
					if (init || source instanceof CatalogVersionModel)
					{
						if (syncJob.getTargetVersion() != null && objectType != null
								&& syncJob.getRootTypes().contains(typeManager.getComposedType(objectType.getCode())))
						{
							accesibleRules.add((SyncItemJobModel) modelService.get(syncJob));
						}
						else if (objectType == null)
						{
							accesibleRules.add((SyncItemJobModel) modelService.get(syncJob));
						}
					}
					else
					{
						forbidenRules.add((SyncItemJobModel) modelService.get(syncJob));
					}
				}
			}
		}
		ret[0] = accesibleRules;
		ret[1] = forbidenRules;
		return ret;
	}

	@Override
	public CatalogVersionModel getCatalogVersionForItem(final TypedObject item)
	{
		return (CatalogVersionModel) modelService.get(retrieveCatalogVersion(item));
	}

	/**
	 * @see de.hybris.platform.cockpit.services.sync.SynchronizationService#getAllSynchronizationRules(java.util.Collection)
	 */
	@Override
	@SuppressWarnings(
	{ "unchecked", "deprecation" })
	public Map<String, String>[] getAllSynchronizationRules(final Collection items)
	{
		final Map<String, String>[] ret = new HashMap[2];
		final Map<String, String> accesibleRules = new HashMap<String, String>();
		final Map<String, String> forbidenRules = new HashMap<String, String>();

		for (final Object item : items)
		{
			CatalogVersion sourceCatalogVersion = null;
			Item jaloItem = null;
			if (item instanceof TypedObject)
			{
				jaloItem = extractJaloItem(((TypedObject) item).getObject());
				sourceCatalogVersion = retrieveCatalogVersion((TypedObject) item);
			}
			else if (item instanceof CatalogVersionModel)
			{
				sourceCatalogVersion = (CatalogVersion) modelService.getSource(((CatalogVersionModel) item));

			}
			else if (item instanceof CategoryModel)
			{
				jaloItem = modelService.getSource(((CategoryModel) item));
				sourceCatalogVersion = catalogManager.getCatalogVersion(jaloItem);
			}

			if (sourceCatalogVersion != null)
			{
				for (final SyncItemJob syncJob : sourceCatalogVersion.getSynchronizations())
				{

					if (!checkUserRight(syncJob))
					{
						continue;
					}
					final boolean init = isVersionSynchronizedAtLeastOnce(Collections.singletonList(syncJob));

					if (!init && !(item instanceof CatalogVersionModel))
					{
						forbidenRules.put(syncJob.getPK().toString(), prepareReadableSyncRuleName(syncJob));
					}
					else
					{
						ComposedType superType = null;
						if (jaloItem != null)
						{
							final ComposedType currentComposedType = typeManager.getComposedType(jaloItem.getClass());
							if (currentComposedType != null)
							{
								superType = currentComposedType.getSuperType();
							}

							final List<ComposedType> rootTypes = syncJob.getRootTypes();
							if (rootTypes.contains(currentComposedType) || rootTypes.contains(superType))
							{
								accesibleRules.put(syncJob.getPK().toString(), prepareReadableSyncRuleName(syncJob));
							}
						}
						else
						{
							accesibleRules.put(syncJob.getPK().toString(), prepareReadableSyncRuleName(syncJob));
						}
					}
				}
			}
		}
		ret[0] = accesibleRules;
		ret[1] = forbidenRules;
		return ret;
	}

	/**
	 * Prepares more readable name for synchronization rule
	 */
	@SuppressWarnings("deprecation")
	private String prepareReadableSyncRuleName(final SyncItemJob syncJob)
	{

		String finalName = "";
		final CatalogVersion sourceCatalgVersion = syncJob.getSourceVersion();
		final CatalogVersion targetCatalgVersion = syncJob.getTargetVersion();

		String catalogname = "";
		String label = "";
		Catalog catalog = syncJob.getSourceVersion().getCatalog();
		if (catalog.getName() != null)
		{
			catalogname = catalog.getName();
		}
		label = (catalogname != null ? catalogname : LEFT_ANGLE_BRACKET + catalog.getId() + RIGHT_ANGLE_BRACKET) + " "
				+ sourceCatalgVersion.getVersion();
		finalName += label + LEFT_ROUND_BRACKET + CockpitManager.getInstance().getMnemonic(sourceCatalgVersion)
				+ RIGHT_ROUND_BRACKET;
		catalog = syncJob.getTargetVersion().getCatalog();
		if (catalog.getName() != null)
		{
			catalogname = catalog.getName();
		}
		label = (catalogname != null ? catalogname : LEFT_ANGLE_BRACKET + catalog.getId() + RIGHT_ANGLE_BRACKET) + " "
				+ targetCatalgVersion.getVersion();
		finalName += " " + RIGHT_ARROW + " " + label + LEFT_ROUND_BRACKET
				+ CockpitManager.getInstance().getMnemonic(targetCatalgVersion) + RIGHT_ROUND_BRACKET;
		finalName += " (" + syncJob.getCode() + ")";
		return finalName;
	}

	/*
	 * TODO check how to achieve this requirement properly, also SyncItemJob etc. permissions has to be checked
	 */
	/**
	 * Checks whether current user has permission to perform synchronization
	 *
	 * @param job
	 *           - particular job
	 * @return true if user is allowed otherwise false
	 */
	@SuppressWarnings(
	{ "deprecation", "unchecked" })
	private boolean checkUserRight(final Job job)
	{
		return ((job instanceof SyncItemJob) && checkUserRightToTargetVersion(job));
	}

	@SuppressWarnings("deprecation")
	private boolean checkUserRightToTargetVersion(final Job job)
	{
		final SyncItemJob syncItemJob = (SyncItemJob) job;
		final JaloSession jaloSession = JaloSession.getCurrentSession();
		final User currentUser = jaloSession.getUser();
		if (currentUser.isAdmin())
		{
			return true;
		}

		final SessionContext ctx = jaloSession.getSessionContext();
		return catalogManager.canSync(ctx, currentUser, syncItemJob);
	}

	@SuppressWarnings("deprecation")
	@Override
	public List<String> getSynchronizationStatuses(final List<SyncItemJobModel> rules, final TypedObject sourceObject)
	{
		final Collection<TypedObject> relatedReferences = new ArrayList<TypedObject>();
		final List<String> retStatuses = getSynchronizationStatusesSimple(rules, sourceObject);
		relatedReferences.addAll(getRelatedReferences(sourceObject));

		for (final TypedObject typedObject : relatedReferences)
		{
			final List<String> childrenStatuses = getSynchronizationStatusesSimple(rules, typedObject);
			retStatuses.retainAll(childrenStatuses);
		}
		return retStatuses;
	}


	@SuppressWarnings("deprecation")
	public List<String> getSynchronizationStatusesSimple(final List<SyncItemJobModel> rules, final TypedObject sourceObject)
	{

		final List<String> result = new ArrayList<String>();
		SessionContext ctx = null;
		try
		{
			ctx = JaloSession.getCurrentSession().createLocalSessionContext();
			ctx.setAttribute(DISABLE_RESTRICTION, isSearchRestrictionDisabled());
			for (final SyncItemJobModel rule : rules)
			{
				final CatalogVersion target = (CatalogVersion) modelService.getSource(rule.getTargetVersion());
				final Item targetCounterPart = catalogManager.getCounterpartItem(extractJaloItem(sourceObject.getObject()), target);
				if (targetCounterPart == null)
				{
					continue;
				}

				final SyncItemJob syncJob = modelService.getSource(rule);

				/* final long lastSyncTime = getLastTargetProductSyncTimestamp(targetCounterPart); */
				long secondSyncTime = 0L;
				final Date secondSyncDate = catalogManager.getLastSyncModifiedTime(syncJob,
						extractJaloItem(sourceObject.getObject()), targetCounterPart);

				if (secondSyncDate != null)
				{
					secondSyncTime = secondSyncDate.getTime();
				}
				if (secondSyncTime >= (extractJaloItem(sourceObject.getObject())).getModificationTime().getTime())
				{
					result.add(syncJob.getCode());
				}
			}
		}
		finally
		{
			if (ctx != null)
			{
				JaloSession.getCurrentSession().removeLocalSessionContext();
			}
		}
		return result;
	}

	/**
	 * Responsible for catalog version synchronization //TODO should be refactor
	 */
	@Override
	@SuppressWarnings("deprecation")
	public void performCatalogVersionSynchronization(final Collection<CatalogVersionModel> data,
			final List<String> syncRulePkList, final CatalogVersionModel targetCatalogVersion, final String qualifier)
	{
		if (syncRulePkList != null)
		{
			final JaloSession jaloSession = JaloSession.getCurrentSession();
			for (final String jobPk : syncRulePkList)
			{
				final SyncItemJob syncJob = jaloSession.getItem(PK.parse(jobPk));
				final SyncItemCronJob synchronizeJob = syncJob.newExecution();
				// syncJob.configureFullVersionSync(synchronizeJob); // PLA-12752
				syncJob.perform(synchronizeJob, true);
			}
		}
		else
		{
			for (final Object object : data)
			{
				CatalogVersion catalogVersion = null;
				if (object instanceof CatalogVersionModel)
				{
					catalogVersion = (CatalogVersion) modelService.getSource(((CatalogVersionModel) object));
				}
				else if (object instanceof CategoryModel)
				{
					catalogVersion = catalogManager.getCatalogVersion((Item) modelService.getSource(((CategoryModel) object)));
				}
				//				if (catalogVersion.isActive().booleanValue())
				//				{
				//					continue;
				//				}

				SyncItemJob catalogVersionSyncJob = null;
				if (targetCatalogVersion == null)
				{
					catalogVersionSyncJob = catalogManager.getSyncJobFromSource(catalogVersion);
				}
				else
				{
					final CatalogVersion target = (CatalogVersion) modelService.getSource(targetCatalogVersion);

					//CatalogVersion target = null;
					if (qualifier != null)
					{
						catalogVersionSyncJob = catalogManager.getSyncJob(catalogVersion, target, qualifier);
					}
					else
					{
						catalogVersionSyncJob = catalogManager.getSyncJob(catalogVersion, target);
					}
				}
				if (catalogVersionSyncJob != null)
				{
					final SyncItemCronJob synchronizeJob = catalogVersionSyncJob.newExecution();
					// catalogVersionSyncJob.configureFullVersionSync(synchronizeJob);  // PLA-12752
					catalogVersionSyncJob.perform(synchronizeJob, true);
				}
			}
		}
	}

	/**
	 * @see de.hybris.platform.cockpit.services.sync.SynchronizationService#hasMultipleRules(java.util.Collection)
	 */

	@Override
	public boolean hasMultipleRules(final Collection items)
	{
		for (final Iterator iter = items.iterator(); iter.hasNext();)
		{
			final Object item = iter.next();
			CatalogVersion version = null;
			if ((item instanceof TypedObject) && (((TypedObject) item).getObject() instanceof ProductModel))
			{
				version = retrieveCatalogVersion((TypedObject) item);
			}
			else if (item instanceof CatalogVersionModel)
			{
				version = (CatalogVersion) modelService.getSource(((CatalogVersionModel) item));
			}
			else if (item instanceof CategoryModel)
			{
				version = catalogManager.getCatalogVersion((Item) modelService.getSource((CategoryModel) item));
			}
			if (version != null && version.getSynchronizations().size() > 1)
			{
				return true;
			}
		}
		return false;
	}


	private Item extractJaloItem(final Object sourceObject)
	{
		if (sourceObject instanceof Item)
		{
			return (Item) sourceObject;
		}
		if (sourceObject instanceof ItemModel)
		{
			return getModelService().getSource((ItemModel) sourceObject);
		}

		return null;
	}

	@Override
	public SyncContext getSyncContext(final TypedObject product)
	{
		return getSyncContext(product, false);
	}

	@Override
	public SyncContext getSyncContext(final TypedObject product, final boolean pullSync)
	{
		SyncContextImpl ret = null;
		if (pullSync)
		{

			ret = createPullSyncStatusContext(product);
		}
		else
		{
			// TODO pp: combine the following methods for better performance
			final List<SyncItemJobModel>[] targetCatalogVersions = getTargetCatalogVersions(product);
			final CatalogVersionModel sourceCatalogVersion = getCatalogVersionForItem(product);
			final Set<CatalogVersionModel> sourceVersions = new HashSet<CatalogVersionModel>();
			sourceVersions.add(sourceCatalogVersion);

			final SyncInfo syncInfo = getSyncInfo(product);
			final int productSynchronized = syncInfo.getSyncStatus();

			ret = new SyncContextImpl(SYNCHRONIZATION_NOT_AVAILABLE, sourceVersions, targetCatalogVersions, productSynchronized,
					null);
			if (syncInfo.getAffectedItems() != null)
			{
				ret.setAffectedItems(syncInfo.getAffectedItems());
			}
		}

		return ret;
	}

	/**
	 * Sets the synchronization service DAO.
	 *
	 * @param synchronizationServiceDao
	 *           the synchronizationServiceDao to set
	 */
	public void setSynchronizationServiceDao(final SynchronizationServiceDao synchronizationServiceDao)
	{
		this.synchronizationServiceDao = synchronizationServiceDao;
	}


	protected class SyncContextImpl implements SyncContext
	{
		private int pullSyncStatus = 0;
		private Set<CatalogVersionModel> sourceCatalogVersionModels = null;
		private List<SyncItemJobModel>[] targetCatalogVersions = null;
		private int productSynchronized = 0;
		private final Set<ItemModel> sourceItemModels = new HashSet<ItemModel>();
		private Set<TypedObject> affectedItems = null;


		public SyncContextImpl()
		{
			super();
		}

		public SyncContextImpl(final int pullSyncStatus, final Set<CatalogVersionModel> sourceCatalogVersionModels,
				final List<SyncItemJobModel>[] targetCatalogVersions, final int productSynchronized, final ItemModel sourceItemModel)
		{
			super();
			this.pullSyncStatus = pullSyncStatus;
			this.sourceCatalogVersionModels = sourceCatalogVersionModels;
			this.targetCatalogVersions = targetCatalogVersions;
			this.productSynchronized = productSynchronized;
			this.sourceItemModels.add(sourceItemModel);
		}

		@Override
		public int getPullSyncStatus()
		{
			return pullSyncStatus;
		}

		@Override
		public Set<CatalogVersionModel> getSourceCatalogVersions()
		{
			return sourceCatalogVersionModels;
		}

		@Override
		public List<SyncItemJobModel>[] getSyncJobs()
		{
			return targetCatalogVersions;
		}

		@Override
		public int isProductSynchronized()
		{
			return productSynchronized;
		}

		@Override
		public Collection<ItemModel> getSourceItemModels()
		{
			return Collections.unmodifiableCollection(sourceItemModels);
		}

		public void setPullSyncStatus(final int pullSyncStatus)
		{
			this.pullSyncStatus = pullSyncStatus;
		}

		public void setProductSynchronized(final int productSynchronized)
		{
			this.productSynchronized = productSynchronized;
		}

		public void addSourceItemModel(final ItemModel sourceItemModel)
		{
			if (sourceItemModel != null)
			{
				this.sourceItemModels.add(sourceItemModel);
			}
		}

		@Override
		public Collection<TypedObject> getAffectedItems()
		{
			final Set<TypedObject> ret = new HashSet<TypedObject>();
			for (final ItemModel model : getSourceItemModels())
			{
				final TypedObject wrapItem = getTypeService().wrapItem(model);
				if (wrapItem != null)
				{
					ret.add(wrapItem);
				}
			}
			if (affectedItems != null)
			{
				ret.addAll(affectedItems);
			}
			return ret;
		}

		public void setAffectedItems(final Set<TypedObject> items)
		{
			this.affectedItems = items;
		}
	}

	/**
	 * Retrieves all related references according to current configuration.</p> <b>Note:</b><br/>
	 * This method retrieves all related references that take a part into synch status computation.
	 *
	 * @param typedObject
	 *           given object
	 * @return related references
	 */
	protected List<TypedObject> getRelatedReferences(final TypedObject typedObject)
	{
		final List<String> relatedReferencesTypes = getConfiguredReferenceTypes(typedObject);

		final List<TypedObject> ret = new ArrayList<TypedObject>();
		if (CollectionUtils.isNotEmpty(relatedReferencesTypes))
		{
			ret.addAll(getTypeService().wrapItems(
					lookupRelatedReferences(typedObject, relatedReferencesTypes,
							computeRelatedReferencesMaxDepth(relatedReferencesTypes), new HashSet<Long>())));
			if (ret.contains(typedObject))
			{
				ret.remove(typedObject);
			}
		}
		return ret;
	}

	private int computeRelatedReferencesMaxDepth(final List<String> relatedReferencesTypes)
	{
		if (this.relatedReferencesMaxDepth == -1)
		{
			return relatedReferencesTypes.size();
		}
		else
		{
			return Math.max(0, this.relatedReferencesMaxDepth);
		}
	}

	private boolean isValueTypeAssignableFrom(final PropertyDescriptor propertyDescriptor, final Collection<String> typeCodes)
	{
		for (final String typeCode : typeCodes)
		{
			if (getTypeService().getObjectType(typeCode).isAssignableFrom(
					getTypeService().getObjectType(getTypeService().getValueTypeCode(propertyDescriptor))))
			{
				return true;
			}
		}
		return false;
	}

	private Set<PropertyDescriptor> getRelevantDescriptors(final BaseType baseType,
														   final List<String> relatedReferenceTypesAndProperties)
	{
		relatedProperties.computeIfAbsent(baseType, type -> {
			final Set<PropertyDescriptor> configuredDescriptors = new HashSet<PropertyDescriptor>();
			final Set<String> configuredTypes = new HashSet<String>();
			for (final String typeOrProperty : relatedReferenceTypesAndProperties)
			{
				if (StringUtils.contains(typeOrProperty, "."))
				{
					configuredDescriptors.add(getTypeService().getPropertyDescriptor(typeOrProperty));
				}
				else
				{
					configuredTypes.add(typeOrProperty);
				}
			}
			HashSet<PropertyDescriptor> ret = new HashSet<>();
			final Set<PropertyDescriptor> allPropertyDescriptors = type.getPropertyDescriptors();

			for (final PropertyDescriptor propertyDescriptor : allPropertyDescriptors)
			{
				if (PropertyDescriptor.REFERENCE.equals(propertyDescriptor.getEditorType())
						&& (configuredDescriptors.contains(propertyDescriptor) || isValueTypeAssignableFrom(propertyDescriptor,
						configuredTypes)))
				{
					ret.add(propertyDescriptor);
				}
			}
			return ret;
		});
		return relatedProperties.get(baseType);
	}

        /**
         * Recursively look for related references. </p>
         *
         * @param typedObject
         *           given object
         * @param relatedReferenceTypesAndProperties
         *           configured typecodes and property qualifiers
         * @param depth
         *           maximum depth of recursive calls
         * @return related references
         */
	private Set<Object> lookupRelatedReferences(final TypedObject typedObject,
			final List<String> relatedReferenceTypesAndProperties, final int depth, final Set<Long> added)
	{
		final Set<Object> ret = new HashSet<Object>();
		if (depth == 0 || typedObject == null)
		{
			return ret;
		}

		final Set<String> availableLanguageIsos = getSystemService().getAvailableLanguageIsos();

		final Set<PropertyDescriptor> relevantDescriptors = getRelevantDescriptors(typedObject.getType(),
				relatedReferenceTypesAndProperties);

		final ObjectValueContainer valueContainer = TypeTools.createValueContainer(valueHandlerRegistry, typedObject, relevantDescriptors, availableLanguageIsos, false);


		for (final PropertyDescriptor propertyDescriptor : relevantDescriptors)
		{
			if (propertyDescriptor.isLocalized())
			{
				for (final String langIso : availableLanguageIsos)
				{
					final ObjectValueHolder ovh = valueContainer.getValue(propertyDescriptor, langIso);

					final List<Object> objects = extractNonLocalizedAttrValues(propertyDescriptor, ovh);
					for (Object object : objects)
					{
						object = getTypeService().wrapItem(object);
						if (object instanceof TypedObject)
						{
							final Long pk = getObjectPk((TypedObject) object);
							if (!added.contains(pk))
							{
								ret.add(object);
								added.add(pk);
							}
						}
					}
				}
			}
			else
			{
				final List<Object> objects = extractNonLocalizedAttrValues(propertyDescriptor,
						valueContainer.getValue(propertyDescriptor, null));
				for (Object object : objects)
				{
					object = getTypeService().wrapItem(object);
					if (object instanceof TypedObject)
					{
						final Long pk = getObjectPk((TypedObject) object);
						if (!added.contains(pk))
						{
							ret.add(object);
							added.add(pk);
						}
					}
				}
			}
		}

		final List<Object> localCopy = new ArrayList<Object>(ret);
		for (final Object alreadyFound : localCopy)
		{
			ret.addAll(lookupRelatedReferences(getTypeService().wrapItem(alreadyFound), relatedReferenceTypesAndProperties,
					depth - 1, added));
		}
		return ret;
	}

	private Long getObjectPk(final TypedObject object)
	{
		final PK pk = ((ItemModel) object.getObject()).getPk();
		return Long.valueOf(pk == null ? 0 : pk.getLongValue());
	}

	/**
	 * Returns configured reference types. </p>
	 *
	 * @param typedObject
	 *           given object
	 * @return configured reference types
	 *
	 */
	protected List<String> getConfiguredReferenceTypes(final TypedObject typedObject)
	{
		final List<ObjectType> pathTypes = TypeTools.getAllSupertypes(typedObject.getType());
		Collections.reverse(pathTypes);

		List<String> relatedReferencesTypes = getRelatedReferencesTypesMap().get(typedObject.getType().getCode());
		final Iterator<ObjectType> fallbackIter = pathTypes.iterator();
		while (fallbackIter.hasNext() && relatedReferencesTypes == null)
		{
			final ObjectType currentObjetType = fallbackIter.next();
			relatedReferencesTypes = getRelatedReferencesTypesMap().get(currentObjetType.getCode());
		}
		return relatedReferencesTypes;
	}

	private List<Object> extractNonLocalizedAttrValues(final PropertyDescriptor propertyDescriptor,
			final ObjectValueHolder objectValueHolder)
	{
		final List<Object> ret = new ArrayList<Object>();
		if (PropertyDescriptor.Multiplicity.SINGLE.equals(propertyDescriptor.getMultiplicity()))
		{
			final Object currentValue = objectValueHolder.getCurrentValue();
			if (currentValue != null)
			{
				ret.add(currentValue);
			}

		}
		else if (PropertyDescriptor.Multiplicity.LIST.equals(propertyDescriptor.getMultiplicity())
				|| PropertyDescriptor.Multiplicity.SET.equals(propertyDescriptor.getMultiplicity()))
		{

			final Object rawColletion = objectValueHolder.getCurrentValue();
			if (rawColletion instanceof Collection)
			{
				ret.addAll((Collection) rawColletion);
			}
		}
		return ret;
	}




	protected class SyncInfo
	{
		int syncStatus = SYNCHRONIZATION_NOT_AVAILABLE;
		Set<TypedObject> affectedItems = null;

		public int getSyncStatus()
		{
			return syncStatus;
		}

		public void setSyncStatus(final int syncStatus)
		{
			this.syncStatus = syncStatus;
		}

		public Set<TypedObject> getAffectedItems()
		{
			return affectedItems;
		}

		public void setAffectedItems(final Set<TypedObject> affectedItems)
		{
			this.affectedItems = affectedItems;
		}

	}

	public void setSearchRestrictionsDisabled(final boolean disabled)
	{
		disabledSearchRestrictions = Boolean.valueOf(disabled);
	}

	public void setItemSyncTimestampDao(final ItemSyncTimestampDao itemSyncTimestampDao)
	{
		this.itemSyncTimestampDao = itemSyncTimestampDao;
	}

    public SystemService getSystemService(){

        if(this.systemService==null){
            this.systemService=
                    UISessionUtils.getCurrentSession().getSystemService();
        }
        return systemService;
    }

    public void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }


    @Required
    public void setValueHandlerRegistry(ObjectValueHandlerRegistry valueHandlerRegistry) {
        this.valueHandlerRegistry = valueHandlerRegistry;
    }

}