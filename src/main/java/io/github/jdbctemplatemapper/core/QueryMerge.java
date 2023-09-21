package io.github.jdbctemplatemapper.core;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.util.Assert;
import io.github.jdbctemplatemapper.querymerge.IQueryMergeFluent;
import io.github.jdbctemplatemapper.querymerge.IQueryMergeHasMany;
import io.github.jdbctemplatemapper.querymerge.IQueryMergeHasOne;
import io.github.jdbctemplatemapper.querymerge.IQueryMergeJoinColumnManySide;
import io.github.jdbctemplatemapper.querymerge.IQueryMergeJoinColumnOwningSide;
import io.github.jdbctemplatemapper.querymerge.IQueryMergeOrderBy;
import io.github.jdbctemplatemapper.querymerge.IQueryMergePopulateProperty;
import io.github.jdbctemplatemapper.querymerge.IQueryMergeThroughJoinColumns;
import io.github.jdbctemplatemapper.querymerge.IQueryMergeThroughJoinTable;
import io.github.jdbctemplatemapper.querymerge.IQueryMergeType;

/**
 * QueryMerge allows query results to be merged with results of another query.
 *
 * <pre>
 *
 * See <a href=
 * "https://github.com/jdbctemplatemapper/jdbctemplatemapper#querying-relationships">Querying relationships</a> for more info
 * </pre>
 *
 * @author ajoseph
 */
public class QueryMerge<T> implements IQueryMergeFluent<T> {
  private static final int CACHE_MAX_ENTRIES = 1000;
  // key - cacheKey
  // value - Sql
  private static Map<String, String> successQueryMergeSqlCache = new ConcurrentHashMap<>();

  private int inClauseChunkSize = 100;
  private Class<T> ownerType;
  private RelationshipType relationshipType;
  private Class<?> relatedType;
  private String propertyName; // propertyName on ownerType that needs to be populated
  private String joinColumnOwningSide;
  private String joinColumnManySide;

  private String throughJoinTable;
  private String throughOwnerTypeJoinColumn;
  private String throughRelatedTypeJoinColumn;
  private String orderBy;

  private QueryMerge(Class<T> type) {
    this.ownerType = type;
  }

  /**
   * The type being merged with the new query. The execute method will populate a list of this type
   * with the relationship
   *
   * @param <T> the type
   * @param type The type
   * @return interface with the next methods in the chain
   */
  public static <T> IQueryMergeType<T> type(Class<T> type) {
    Assert.notNull(type, "Type cannot be null");
    return new QueryMerge<T>(type);
  }

  /**
   * hasOne relationship
   *
   * @param relatedType the related type
   * @return interface with the next methods in the chain
   */
  public IQueryMergeHasOne<T> hasOne(Class<?> relatedType) {
    Assert.notNull(relatedType, "relatedType cannot be null");
    this.relationshipType = RelationshipType.HAS_ONE;
    this.relatedType = relatedType;
    return this;
  }

  /**
   * hasMany relationship
   *
   * @param relatedType the related type
   * @return interface with the next methods in the chain
   */
  public IQueryMergeHasMany<T> hasMany(Class<?> relatedType) {
    Assert.notNull(relatedType, "relatedType cannot be null");
    this.relationshipType = RelationshipType.HAS_MANY;
    this.relatedType = relatedType;
    return this;
  }

  /**
   * Join column for hasOne relationship. The join column (the foreign key) is in the table of the
   * owning model. Example: Order hasOne Customer. The join column(foreign key) will be on the table
   * order (of the owning model)
   *
   * <p>
   * The join column should not have a table prefix.
   *
   * @param joinColumnOwningSide the join column on the owning side (with no table prefix)
   * @return interface with the next methods in the chain
   */
  public IQueryMergeJoinColumnOwningSide<T> joinColumnOwningSide(String joinColumnOwningSide) {
    if (MapperUtils.isBlank(joinColumnOwningSide)) {
      throw new IllegalArgumentException("joinColumnOwningSide cannot be null or blank");
    }
    this.joinColumnOwningSide = MapperUtils.toLowerCase(joinColumnOwningSide.trim());
    return this;
  }

  /**
   * Join column for hasMany relationship: The join column (the foreign key) is in the table of the
   * many side. Example: Order hasMany OrderLine. The join column will be on the table order_line
   * (the many side)
   *
   * <p>
   * The join column should not have a table prefix.
   *
   * @param joinColumnManySide the join column on the many side (with no table prefix)
   * @return interface with the next methods in the chain
   */
  public IQueryMergeJoinColumnManySide<T> joinColumnManySide(String joinColumnManySide) {
    if (MapperUtils.isBlank(joinColumnManySide)) {
      throw new IllegalArgumentException("joinColumnManySide cannot be null or blank");
    }
    this.joinColumnManySide = MapperUtils.toLowerCase(joinColumnManySide.trim());
    return this;
  }


  /**
   * The join table (associative table) for the hasMany through (many to many) relationship
   *
   * @param tableName the associative table name
   * @return interface with the next methods in the chain
   */
  public IQueryMergeThroughJoinTable<T> throughJoinTable(String tableName) {
    if (MapperUtils.isBlank(tableName)) {
      throw new IllegalArgumentException("throughJoinTable() tableName cannot be null or blank");
    }
    this.relationshipType = RelationshipType.HAS_MANY_THROUGH;
    this.throughJoinTable = tableName;
    return this;
  }

  /**
   * The join columns for the owning side and the related side for hasMany through relationship
   *
   * @param ownerTypeJoinColumn the join column for the owning side
   * @param relatedTypeJoinColumn the join column for the related side
   * @return interface with the next methods in the chain
   */
  public IQueryMergeThroughJoinColumns<T> throughJoinColumns(String ownerTypeJoinColumn,
      String relatedTypeJoinColumn) {
    if (MapperUtils.isBlank(ownerTypeJoinColumn)) {
      throw new IllegalArgumentException(
          "throughJoinColumns() ownerTypeJoinColumn cannot be null or blank");
    }
    if (MapperUtils.isBlank(relatedTypeJoinColumn)) {
      throw new IllegalArgumentException(
          "throughJoinColumns() relatedTypeJoinColumn cannot be null or blank");
    }
    this.throughOwnerTypeJoinColumn = ownerTypeJoinColumn;
    this.throughRelatedTypeJoinColumn = relatedTypeJoinColumn;
    return this;
  }


  /**
   * The relationship property that needs to be populated on the owning type.
   *
   * <p>
   * For hasMany() this property has to be an initialized collection (cannot be null) and the
   * generic type should match the hasMany related type.
   *
   * <p>
   * For hasOne this property has to be the same type as hasOne related type
   *
   * @param propertyName name of property that needs to be populated
   * @return interface with the next methods in the chain
   */
  public IQueryMergePopulateProperty<T> populateProperty(String propertyName) {
    if (MapperUtils.isBlank(propertyName)) {
      throw new IllegalArgumentException("propertyName cannot be null or blank");
    }
    this.propertyName = propertyName;
    return this;
  }

  /**
   * The orderBy clause for QueryMerge. For QueryMerge orderBy is only supported for hasMany and
   * hasMany through. For others it makes sense because the order it determined by the mergeList
   * 
   * orderBy columns have to be on the table of the hasMany/hasMany through side.
   * 
   * @param orderBy the orderBy clause.
   * @return interface with the next methods in the chain
   */
  public IQueryMergeOrderBy<T> orderBy(String orderBy) {
    if (MapperUtils.isBlank(orderBy)) {
      throw new IllegalArgumentException("orderBy cannot be null or blank");
    }
    this.orderBy = orderBy;
    return this;
  }

  /**
   * The query executes an sql 'IN' clause to get the related side (hasOne, hasMany) objects and
   * merges those with the objects in the mergeList
   *
   * <pre>
   * Some databases have limits on size of 'IN' clause.
   * If the the mergeList is large, multiple 'IN' queries will be issued with each query
   * having up to 100 IN clause parameters to get the records.
   * </pre>
   *
   * @param jdbcTemplateMapper the jdbcTemplateMapper
   * @param mergeList a list of objects of owning type.
   */
  public void execute(JdbcTemplateMapper jdbcTemplateMapper, List<T> mergeList) {
    Assert.notNull(jdbcTemplateMapper, "jdbcTemplateMapper cannot be null");

    String cacheKey = getQueryMergeCacheKey(jdbcTemplateMapper);
    if (!previouslySuccessfulQuery(cacheKey)) {
      QueryValidator.validate(jdbcTemplateMapper, ownerType, relationshipType, relatedType,
          joinColumnOwningSide, joinColumnManySide, propertyName, throughJoinTable,
          throughOwnerTypeJoinColumn, throughRelatedTypeJoinColumn);
    }
    if (MapperUtils.isEmpty(mergeList)) {
      return;
    }

    if (relationshipType == RelationshipType.HAS_ONE) {
      if (MapperUtils.isNotBlank(orderBy)) {
        throw new IllegalStateException(
            "For QueryMerge hasOne relationships orderBy is not supported."
                + " The order is already dictated by the mergeList");
      }
      processHasOne(jdbcTemplateMapper, mergeList, ownerType, relatedType, cacheKey);
    } else if (relationshipType == RelationshipType.HAS_MANY) {
      processHasMany(jdbcTemplateMapper, mergeList, ownerType, relatedType, cacheKey);
    } else if (relationshipType == RelationshipType.HAS_MANY_THROUGH) {
      processHasManyThrough(jdbcTemplateMapper, mergeList, ownerType, relatedType, cacheKey);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void processHasOne(JdbcTemplateMapper jtm, List<T> mergeList, Class<?> ownerType,
      Class<?> relatedType, String cacheKey) {

    TableMapping ownerTypeTableMapping = jtm.getMappingHelper().getTableMapping(ownerType);
    TableMapping relatedTypeTableMapping = jtm.getMappingHelper().getTableMapping(relatedType);
    String joinPropertyName = ownerTypeTableMapping.getPropertyName(joinColumnOwningSide);

    // used to avoid excessive BeanWrapper creation
    List<BeanWrapper> bwMergeList = new ArrayList<>();
    Set params = new HashSet<>();
    for (T obj : mergeList) {
      if (obj != null) {
        BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(obj);
        bw.setPropertyValue(propertyName, null);
        bwMergeList.add(bw);
        Object joinPropertyValue = bw.getPropertyValue(joinPropertyName);
        if (joinPropertyValue != null) {
          params.add(joinPropertyValue);
        }
      }
    }
    if (MapperUtils.isEmpty(params)) {
      return;
    }
    SelectMapper<?> selectMapper =
        jtm.getSelectMapper(relatedType, relatedTypeTableMapping.getTableName());
    String sql = successQueryMergeSqlCache.get(cacheKey);
    if (sql == null) {
      sql =
          "SELECT " + selectMapper.getColumnsSql() + " FROM "
              + jtm.getMappingHelper()
                  .fullyQualifiedTableName(relatedTypeTableMapping.getTableName())
              + " WHERE " + relatedTypeTableMapping.getIdColumnName()
              + " IN (:joinPropertyOwningSideValues)";
    }

    String relatedModelIdPropName = relatedTypeTableMapping.getIdPropertyName();
    Map<Object, Object> idToRelatedModelMap = new HashMap<>();

    ResultSetExtractor<List<T>> rsExtractor = new ResultSetExtractor<List<T>>() {
      public List<T> extractData(ResultSet rs) throws SQLException, DataAccessException {
        while (rs.next()) {
          BeanWrapper bwRelatedModel = selectMapper.buildBeanWrapperModel(rs);
          if (bwRelatedModel != null) {
            idToRelatedModelMap.put(bwRelatedModel.getPropertyValue(relatedModelIdPropName),
                bwRelatedModel.getWrappedInstance());
          }
        }
        return null;
      }
    };

    // some databases have limits on number of entries in a 'IN' clause
    // Chunk the collection and make multiple calls as needed.
    Collection<List<?>> chunkedJoinPropertyOwningSideValues =
        MapperUtils.chunkTheCollection(params, inClauseChunkSize);
    for (List<?> joinPropertyOwningSideValues : chunkedJoinPropertyOwningSideValues) {
      MapSqlParameterSource queryParams =
          new MapSqlParameterSource("joinPropertyOwningSideValues", joinPropertyOwningSideValues);
      jtm.getNamedParameterJdbcTemplate().query(sql, queryParams, rsExtractor);
    }

    for (BeanWrapper bw : bwMergeList) {
      // find the matching related model
      Object relatedModel = idToRelatedModelMap.get(bw.getPropertyValue(joinPropertyName));
      if (relatedModel != null) {
        bw.setPropertyValue(propertyName, relatedModel);
      }
    }
    // code reaches here query success, handle caching
    if (!previouslySuccessfulQuery(cacheKey)) {
      addToCache(cacheKey, sql);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void processHasMany(JdbcTemplateMapper jtm, List<T> mergeList, Class<?> ownerType,
      Class<?> relatedType, String cacheKey) {

    TableMapping ownerTypeTableMapping = jtm.getMappingHelper().getTableMapping(ownerType);
    TableMapping relatedTypeTableMapping = jtm.getMappingHelper().getTableMapping(relatedType);
    String joinPropertyName = relatedTypeTableMapping.getPropertyName(joinColumnManySide);
    String ownerTypeIdPropName = ownerTypeTableMapping.getIdPropertyName();

    Map<Object, BeanWrapper> idToBeanWrapperOwnerModelMap = new HashMap<>();
    Set params = new HashSet<>();
    for (Object obj : mergeList) {
      if (obj != null) {
        BeanWrapper bwOwnerModel = PropertyAccessorFactory.forBeanPropertyAccess(obj);
        Object idValue = bwOwnerModel.getPropertyValue(ownerTypeIdPropName);
        if (idValue != null) {
          params.add(idValue);
          // init collection to address edge case where collection is initialized with values
          Collection collection = (Collection) bwOwnerModel.getPropertyValue(propertyName);
          if (collection.size() > 0) {
            collection.clear();
          }
          idToBeanWrapperOwnerModelMap.put(idValue, bwOwnerModel);
        }
      }
    }
    if (MapperUtils.isEmpty(params)) {
      return;
    }
    SelectMapper<?> selectMapper =
        jtm.getSelectMapper(relatedType, relatedTypeTableMapping.getTableName());
    String sql = successQueryMergeSqlCache.get(cacheKey);
    if (sql == null) {
      sql = "SELECT " + selectMapper.getColumnsSql() + " FROM "
          + jtm.getMappingHelper().fullyQualifiedTableName(relatedTypeTableMapping.getTableName())
          + " WHERE " + joinColumnManySide + " IN (:ownerTypeIds)";

      if (MapperUtils.isNotBlank(orderBy)) {
        sql += " ORDER BY " + orderBy;
      }
    }
    ResultSetExtractor<List<T>> rsExtractor = new ResultSetExtractor<List<T>>() {
      public List<T> extractData(ResultSet rs) throws SQLException, DataAccessException {
        while (rs.next()) {
          BeanWrapper bwRelatedModel = selectMapper.buildBeanWrapperModel(rs);
          if (bwRelatedModel != null) {
            Object joinPropertyValue = bwRelatedModel.getPropertyValue(joinPropertyName);
            BeanWrapper bwOwnerModel = idToBeanWrapperOwnerModelMap.get(joinPropertyValue);
            if (bwOwnerModel != null) {
              // already validated so we know collection is initialized
              Collection collection = (Collection) bwOwnerModel.getPropertyValue(propertyName);
              collection.add(bwRelatedModel.getWrappedInstance());
            }
          }
        }
        return null;
      }
    };

    // some databases have limits on number of entries in a 'IN' clause
    // Chunk the collection and make multiple calls as needed.
    Collection<List<?>> chunkedOwnerTypeIds =
        MapperUtils.chunkTheCollection(params, inClauseChunkSize);
    for (List ownerTypeIds : chunkedOwnerTypeIds) {
      MapSqlParameterSource queryParams = new MapSqlParameterSource("ownerTypeIds", ownerTypeIds);
      jtm.getNamedParameterJdbcTemplate().query(sql, queryParams, rsExtractor);
    }

    // code reaches here query success, handle caching
    if (!previouslySuccessfulQuery(cacheKey)) {
      addToCache(cacheKey, sql);
    }
  }


  @SuppressWarnings({"rawtypes", "unchecked"})
  private void processHasManyThrough(JdbcTemplateMapper jtm, List<T> mergeList, Class<?> ownerType,
      Class<?> relatedType, String cacheKey) {

    MappingHelper mappingHelper = jtm.getMappingHelper();
    TableMapping ownerTypeTableMapping = mappingHelper.getTableMapping(ownerType);
    TableMapping relatedTypeTableMapping = mappingHelper.getTableMapping(relatedType);

    String ownerTypeTableName = ownerTypeTableMapping.getTableName();
    String relatedTypeTableName = relatedTypeTableMapping.getTableName();
    String ownerTypeIdPropName = ownerTypeTableMapping.getIdPropertyName();

    // key - ownerTypeId, value - bean wrapped owner model from mergeList
    Map<Object, BeanWrapper> idToBeanWrapperOwnerModelMap = new HashMap<>();
    Set params = new HashSet<>();
    for (Object obj : mergeList) {
      if (obj != null) {
        BeanWrapper bwOwnerModel = PropertyAccessorFactory.forBeanPropertyAccess(obj);
        Object idValue = bwOwnerModel.getPropertyValue(ownerTypeIdPropName);
        if (idValue != null) {
          params.add(idValue);
          // init collection to address edge case where collection is initialized with values
          Collection collection = (Collection) bwOwnerModel.getPropertyValue(propertyName);
          if (collection.size() > 0) {
            collection.clear();
          }
          idToBeanWrapperOwnerModelMap.put(idValue, bwOwnerModel);
        }
      }
    }
    if (MapperUtils.isEmpty(params)) {
      return;
    }

    // Hack to get the ownerType id value. The select statement is build in such a way the
    // buildBeanWrapperModel(rs)
    // returns the ownerType id value.
    SelectMapper<?> selectMapperOwnerType = jtm.getSelectMapper(ownerType, ownerTypeTableName);

    SelectMapper<?> selectMapperRelatedType =
        jtm.getSelectMapper(relatedType, relatedTypeTableMapping.getTableName());

    String sql = successQueryMergeSqlCache.get(cacheKey);
    if (sql == null) {
      sql = "SELECT " + throughJoinTable + "." + throughOwnerTypeJoinColumn + " as "
          + ownerTypeTableName + "_" + ownerTypeTableMapping.getIdColumnName() + ", "
          + selectMapperRelatedType.getColumnsSql() + " FROM "
          + jtm.getMappingHelper().fullyQualifiedTableName(throughJoinTable) + " LEFT JOIN "
          + mappingHelper.fullyQualifiedTableName(relatedTypeTableName) + " on " + throughJoinTable
          + "." + throughRelatedTypeJoinColumn + " = " + relatedTypeTableName + "."
          + relatedTypeTableMapping.getIdColumnName() + " WHERE " + throughJoinTable + "."
          + throughOwnerTypeJoinColumn + " IN (:ownerTypeIds)";

      if (MapperUtils.isNotBlank(orderBy)) {
        sql += " ORDER BY " + orderBy;
      }
    }

    ResultSetExtractor<List<T>> rsExtractor = new ResultSetExtractor<List<T>>() {
      public List<T> extractData(ResultSet rs) throws SQLException, DataAccessException {
        while (rs.next()) {
          BeanWrapper bwOwnerType = selectMapperOwnerType.buildBeanWrapperModel(rs);
          if (bwOwnerType != null) {
            BeanWrapper bwRelatedModel = selectMapperRelatedType.buildBeanWrapperModel(rs);
            if (bwRelatedModel != null) {
              Object ownerTypeIdValue = bwOwnerType.getPropertyValue(ownerTypeIdPropName);
              if (idToBeanWrapperOwnerModelMap.containsKey(ownerTypeIdValue)) {
                BeanWrapper bw = idToBeanWrapperOwnerModelMap.get(ownerTypeIdValue);
                Collection collection = (Collection) bw.getPropertyValue(propertyName);
                collection.add(bwRelatedModel.getWrappedInstance());
              }
            }
          }
        }
        return null;
      }
    };

    // some databases have limits on number of entries in a 'IN' clause
    // Chunk the collection and make multiple calls as needed.
    Collection<List<?>> chunkedOwnerTypeIds =
        MapperUtils.chunkTheCollection(params, inClauseChunkSize);
    for (List ownerTypeIds : chunkedOwnerTypeIds) {
      MapSqlParameterSource queryParams = new MapSqlParameterSource("ownerTypeIds", ownerTypeIds);
      jtm.getNamedParameterJdbcTemplate().query(sql, queryParams, rsExtractor);
    }

    // code reaches here query success, handle caching
    if (!previouslySuccessfulQuery(cacheKey)) {
      addToCache(cacheKey, sql);
    }
  }

  private String getQueryMergeCacheKey(JdbcTemplateMapper jdbcTemplateMapper) {
 // @formatter:off
    return String.join("-", 
        ownerType.getName(),
        relatedType == null ? null : relatedType.getName(),
        relationshipType == null ? null : relationshipType.toString(),
        joinColumnOwningSide,
        joinColumnManySide,
        throughJoinTable, 
        throughOwnerTypeJoinColumn,
        throughRelatedTypeJoinColumn,
        propertyName,
        orderBy,
        jdbcTemplateMapper.toString());
 // @formatter:on
  }

  private boolean previouslySuccessfulQuery(String key) {
    return successQueryMergeSqlCache.containsKey(key);
  }

  private void addToCache(String key, String sql) {
    if (successQueryMergeSqlCache.size() < CACHE_MAX_ENTRIES) {
      successQueryMergeSqlCache.put(key, sql);
    } else {
      // remove a random entry from cache and add new entry
      String k = successQueryMergeSqlCache.keySet().iterator().next();
      successQueryMergeSqlCache.remove(k);

      successQueryMergeSqlCache.put(key, sql);
    }
  }
}
