/*
 * Copyright 2011-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.core;

import static org.springframework.data.mongodb.core.MongoTemplate.potentiallyConvertRuntimeException;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.util.Assert;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.IndexOptions;

/**
 * Default implementation of {@link IndexOperations}.
 * 
 * @author Mark Pollack
 * @author Oliver Gierke
 * @author Komi Innocent
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public class DefaultIndexOperations implements IndexOperations {

	private final MongoDbFactory mongoDbFactory;
	private final String collectionName;

	/**
	 * Creates a new {@link DefaultIndexOperations}.
	 * 
	 * @param mongoDbFactory must not be {@literal null}.
	 * @param collectionName must not be {@literal null}.
	 */
	public DefaultIndexOperations(MongoDbFactory mongoDbFactory, String collectionName) {

		Assert.notNull(mongoDbFactory, "MongoDbFactory must not be null!");
		Assert.notNull(collectionName, "Collection name can not be null!");

		this.mongoDbFactory = mongoDbFactory;
		this.collectionName = collectionName;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.IndexOperations#ensureIndex(org.springframework.data.mongodb.core.index.IndexDefinition)
	 */
	public void ensureIndex(final IndexDefinition indexDefinition) {
		execute(collection -> {

			Document indexOptions = indexDefinition.getIndexOptions();

			if (indexOptions != null) {

				IndexOptions ops = IndexConverters.DEFINITION_TO_MONGO_INDEX_OPTIONS.convert(indexDefinition);
				collection.createIndex(indexDefinition.getIndexKeys(), ops);
			} else {
				collection.createIndex(indexDefinition.getIndexKeys());
			}
			return null;
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.IndexOperations#dropIndex(java.lang.String)
	 */
	public void dropIndex(final String name) {
		execute(new CollectionCallback<Void>() {
			public Void doInCollection(MongoCollection<Document> collection) throws MongoException, DataAccessException {
				collection.dropIndex(name);
				return null;
			}
		});

	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.IndexOperations#dropAllIndexes()
	 */
	public void dropAllIndexes() {
		dropIndex("*");
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.IndexOperations#resetIndexCache()
	 */
	@Deprecated
	public void resetIndexCache() {
		execute(new CollectionCallback<Void>() {
			public Void doInCollection(MongoCollection<Document> collection) throws MongoException, DataAccessException {

				// TODO remove this one
				// ReflectiveDBCollectionInvoker.resetIndexCache(collection);
				return null;
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.core.IndexOperations#getIndexInfo()
	 */
	public List<IndexInfo> getIndexInfo() {

		return execute(new CollectionCallback<List<IndexInfo>>() {
			public List<IndexInfo> doInCollection(MongoCollection<Document> collection)
					throws MongoException, DataAccessException {

				MongoCursor<Document> dbObjectList = collection.listIndexes(Document.class).iterator();

				return getIndexData(dbObjectList);
			}

			private List<IndexInfo> getIndexData(MongoCursor<Document> dbObjectList) {

				List<IndexInfo> indexInfoList = new ArrayList<IndexInfo>();


				while (dbObjectList.hasNext()) {

					Document ix = dbObjectList.next();
					IndexInfo indexInfo = IndexConverters.DOCUMENT_INDEX_INFO.convert(ix);
					indexInfoList.add(indexInfo);
				}

				return indexInfoList;
			}
		});
	}

	public <T> T execute(CollectionCallback<T> callback) {

		Assert.notNull(callback);

		try {
			MongoCollection<Document> collection = mongoDbFactory.getDb().getCollection(collectionName);
			return callback.doInCollection(collection);
		} catch (RuntimeException e) {
			throw potentiallyConvertRuntimeException(e, mongoDbFactory.getExceptionTranslator());
		}
	}
}
