package ca.uhn.fhir.jpa.dao;

/*
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2020 University Health Network
 * %%
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
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.IInterceptorBroadcaster;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.dao.index.IdHelperService;
import ca.uhn.fhir.jpa.model.config.PartitionSettings;
import ca.uhn.fhir.jpa.model.cross.ResourcePersistentId;
import ca.uhn.fhir.jpa.model.entity.ResourceHistoryTable;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static ca.uhn.fhir.jpa.dao.SearchBuilder.toPredicateArray;

/**
 * The HistoryBuilder is responsible for building history queries
 */
public class HistoryBuilder {

	private final String myResourceType;
	private final Long myResourceId;
	private final Date myRangeStartInclusive;
	private final Date myRangeEndInclusive;
	@Autowired
	protected IInterceptorBroadcaster myInterceptorBroadcaster;
	@PersistenceContext(type = PersistenceContextType.TRANSACTION)
	protected EntityManager myEntityManager;
	@Autowired
	private PartitionSettings myPartitionSettings;
	@Autowired
	private FhirContext myCtx;


	/**
	 * Constructor
	 */
	public HistoryBuilder(@Nullable String theResourceType, @Nullable Long theResourceId, @Nullable Date theRangeStartInclusive, @Nullable Date theRangeEndInclusive) {
		myResourceType = theResourceType;
		myResourceId = theResourceId;
		myRangeStartInclusive = theRangeStartInclusive;
		myRangeEndInclusive = theRangeEndInclusive;
	}

	public Long fetchCount(RequestPartitionId thePartitionId) {
		CriteriaBuilder cb = myEntityManager.getCriteriaBuilder();
		CriteriaQuery<Long> criteriaQuery = cb.createQuery(Long.class);
		Root<ResourceHistoryTable> from = criteriaQuery.from(ResourceHistoryTable.class);
		criteriaQuery.select(cb.count(from));

		addPredicatesToQuery(cb, thePartitionId, criteriaQuery, from);

		TypedQuery<Long> query = myEntityManager.createQuery(criteriaQuery);
		return query.getSingleResult();
	}

	@SuppressWarnings("OptionalIsPresent")
	public List<ResourceHistoryTable> fetchEntities(RequestPartitionId thePartitionId, int theFromIndex, int theToIndex) {
		CriteriaBuilder cb = myEntityManager.getCriteriaBuilder();
		CriteriaQuery<ResourceHistoryTable> criteriaQuery = cb.createQuery(ResourceHistoryTable.class);
		Root<ResourceHistoryTable> from = criteriaQuery.from(ResourceHistoryTable.class);

		addPredicatesToQuery(cb, thePartitionId, criteriaQuery, from);

		from.fetch("myProvenance", JoinType.LEFT);

		criteriaQuery.orderBy(cb.desc(from.get("myUpdated")));

		TypedQuery<ResourceHistoryTable> query = myEntityManager.createQuery(criteriaQuery);

		query.setFirstResult(theFromIndex);
		query.setMaxResults(theToIndex - theFromIndex);

		List<ResourceHistoryTable> tables = query.getResultList();
		if (tables.size() > 0) {
			ImmutableListMultimap<Long, ResourceHistoryTable> resourceIdToHistoryEntries = Multimaps.index(tables, ResourceHistoryTable::getResourceId);

			Map<Long, Optional<String>> pidToForcedId = myIdHelperService.translatePidsToForcedIds(resourceIdToHistoryEntries.keySet());

			for (Long nextResourceId : resourceIdToHistoryEntries.keySet()) {
				List<ResourceHistoryTable> historyTables = resourceIdToHistoryEntries.get(nextResourceId);

				Optional<String> forcedId = myIdHelperService.translatePidIdToForcedId(new ResourcePersistentId(nextResourceId));
				String resourceId;
				if (forcedId.isPresent()) {
					resourceId = historyTables.get(0).getResourceType() + '/' + forcedId.get();
				} else {
					resourceId = historyTables.get(0).getResourceType() + '/' + nextResourceId;
				}

				for (ResourceHistoryTable nextHistoryTable : historyTables) {
					nextHistoryTable.setTransientForcedId(resourceId);
				}
			}
		}

		return tables;
	}


	@Autowired
	private IdHelperService myIdHelperService;

	private void addPredicatesToQuery(CriteriaBuilder theCriteriaBuilder, RequestPartitionId thePartitionId, CriteriaQuery<?> theQuery, Root<ResourceHistoryTable> theFrom) {
		List<Predicate> predicates = new ArrayList<>();

		if (!thePartitionId.isAllPartitions()) {
			if (thePartitionId.getPartitionId() != null) {
				predicates.add(theCriteriaBuilder.equal(theFrom.get("myPartitionIdValue").as(Integer.class), thePartitionId.getPartitionId()));
			} else {
				predicates.add(theCriteriaBuilder.isNull(theFrom.get("myPartitionIdValue").as(Integer.class)));
			}
		}

		if (myResourceId != null) {
			predicates.add(theCriteriaBuilder.equal(theFrom.get("myResourceId"), myResourceId));
		} else if (myResourceType != null) {
			validateNotSearchingAllPartitions(thePartitionId);
			predicates.add(theCriteriaBuilder.equal(theFrom.get("myResourceType"), myResourceType));
		} else {
			validateNotSearchingAllPartitions(thePartitionId);
		}

		if (myRangeStartInclusive != null) {
			predicates.add(theCriteriaBuilder.greaterThanOrEqualTo(theFrom.get("myUpdated").as(Date.class), myRangeStartInclusive));
		}
		if (myRangeEndInclusive != null) {
			predicates.add(theCriteriaBuilder.lessThanOrEqualTo(theFrom.get("myUpdated").as(Date.class), myRangeEndInclusive));
		}

		if (predicates.size() > 0) {
			theQuery.where(toPredicateArray(predicates));
		}
	}

	private void validateNotSearchingAllPartitions(RequestPartitionId thePartitionId) {
		if (myPartitionSettings.isPartitioningEnabled()) {
			if (thePartitionId.isAllPartitions()) {
				String msg = myCtx.getLocalizer().getMessage(HistoryBuilder.class, "noSystemOrTypeHistoryForPartitionAwareServer");
				throw new InvalidRequestException(msg);
			}
		}
	}


}