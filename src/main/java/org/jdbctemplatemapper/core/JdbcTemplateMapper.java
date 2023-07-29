package org.jdbctemplatemapper.core;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.util.Assert;

/**
 * <pre>
 * Spring's JdbcTemplate gives full control of data access using SQL. Its a is better option for complex
 * enterprise applications than an ORM (ORM magic/nuances get in the way for large and complex
 * applications). Even though JdbcTemplate abstracts away a lot of the boiler plate code needed by JDBC, it is
 * verbose.
 *
 * JdbcTemplateMapper tries to mitigate the verboseness. It is a helper utility for JdbcTemplate (NOT a replacement)
 * It provides simple CRUD one liners and less verbose ways to query relationships. Your project code will be a mix of
 * JdbcTemplate and JdbcTemplateMapper. Use JdbcTemplateMapper's more concise features where appropriate.
 *
 * <pre>
 * Features:
 * 1) Simple CRUD one liners
 * 2) Methods to retrieve relationships (toOne(), toMany() etc)
 * 3) Can be configured for:
 *     a) auto assign created on, updated on.
 *     b) auto assign created by, updated by using an implementation of IRecordOperatorResolver.
 *     c) optimistic locking functionality for updates by configuring a version property.
 * 4) Thread safe
 * 5) Tested against PostgreSQL, MySQL, Oracle, SQLServer (Unit tests are run against these databases).
 *    Should work with all other relational databases.
 *
 * <b>JdbcTemplateMapper is opinionated<b/>. Projects have to meet the following 2 criteria to use it:
 * 1)Models should have a property exactly named 'id' (or 'ID') which has to be of type Integer or Long.
 * 2)Camel case object property names are mapped to snake case database column names.
 *   Properties of a model like 'firstName', 'lastName' will be mapped to corresponding database columns
 *   first_name/FIRST_NAME and last_name/LAST_NAME in the database table. If you are using a
 *   case sensitive database installation and have mixed case database column names like 'Last_Name' you could
 *   run into problems.
 *
 * Examples of simple CRUD:
 *
 * // Product class below maps to product/PRODUCT table by default.
 * // Use annotation @Table(name="some_other_tablename") to override the default
 *
 * public class Product {
 *    private Integer id; // 'id' property is needed for all models and has to be of type Integer or Long
 *    private String productName;
 *    private Double price;
 *    private LocalDateTime availableDate;
 *
 *    // for insert/update/find.. methods will ignore properties which do not
 *    // have a corresponding snake case columns in database table
 *    private String someNonDatabaseProperty;
 *
 *    // getters and setters ...
 * }
 *
 * Product product = new Product();
 * product.setProductName("some product name");
 * product.setPrice(10.25);
 * product.setAvailableDate(LocalDateTime.now());
 * jdbcTemplateMapper.insert(product);
 *
 * product = jdbcTemplateMapper.findById(1, Product.class);
 * product.setPrice(11.50);
 * jdbcTemplateMapper.update(product);
 *
 * List<Product> products = jdbcTemplateMapper.findAll(Product.class);
 *
 * jdbcTemplateMapper.delete(product);
 *
 * See methods toOne() and  toMany() for relationship retrieval.
 *
 * Installation:
 * Requires Java8 or above and dependencies are the same as that for Spring's JdbcTemplate
 *
 * pom.xml dependencies
 * For a spring boot application:
 * {@code
 *  <dependency>
 *    <groupId>org.springframework.boot</groupId>
 *    <artifactId>spring-boot-starter-jdbc</artifactId>
 * </dependency>
 * }
 *
 * For a regular spring application:
 * {@code
 *  <dependency>
 *   <groupId>org.springframework</groupId>
 *   <artifactId>spring-jdbc</artifactId>
 *   <version>YourVersionOfSpringJdbc</version>
 *  </dependency>
 * }
 *
 * Spring bean configuration for JdbcTemplateMapper will look something like below:
 * (Assuming that org.springframework.jdbc.core.JdbcTemplate is configured as per Spring instructions)
 *
 * &#64;Bean
 * public JdbcTemplateMapper jdbcTemplateMapper(JdbcTemplate jdbcTemplate) {
 *
 *   return new JdbcTemplateMapper(jdbcTemplate);
 *
 *   //if the database setup needs a schema name, pass it as argument.
 *   //return new JdbcTemplateMapper(jdbcTemplate, "your_database_schema_name");
 * }
 *
 * </pre>
 *
 * @author ajoseph
 */
public class JdbcTemplateMapper {
  private JdbcTemplate jdbcTemplate;
  private NamedParameterJdbcTemplate npJdbcTemplate;
  private IRecordOperatorResolver recordOperatorResolver;

  // TONY private String catalogName;
  // TONY private String schemaName;
  // this is for the call to databaseMetaData.getColumns() just in case a database needs something
  // other than null
  // TONY private String metaDataColumnNamePattern;
  private String createdByPropertyName;
  private String createdOnPropertyName;
  private String updatedByPropertyName;
  private String updatedOnPropertyName;
  private String versionPropertyName;

  // Need this for type conversions like java.sql.Timestamp to java.time.LocalDateTime etc
  private DefaultConversionService defaultConversionService = new DefaultConversionService();

  // Inserts use SimpleJdbcInsert. Since SimpleJdbcInsert is thread safe, cache it
  // Map key - table name,
  //     value - SimpleJdcInsert object for the specific table
  private Map<String, SimpleJdbcInsert> simpleJdbcInsertCache = new ConcurrentHashMap<>();

  // update sql cache
  // Map key   - table name or sometimes tableName-updatePropertyName1-updatePropertyName2...
  //     value - the update sql
  private Map<String, UpdateSqlAndParams> updateSqlAndParamsCache = new ConcurrentHashMap<>();

  // Map key - tableName-tableAlias
  //     value - the selectColumns string
  private Map<String, String> selectColumnsCache = new ConcurrentHashMap<>();

  private DatabaseUtils dbUtils;
  private MappingUtils mappingUtils;

  /**
   * The constructor.
   *
   * @param dataSource The dataSource for the mapper
   */
  public JdbcTemplateMapper(JdbcTemplate jdbcTemplate) {
    this(jdbcTemplate, null);
  }

  /**
   * The constructor.
   *
   * @param dataSource The dataSource for the mapper
   * @param schemaName database schema name.
   */
  public JdbcTemplateMapper(JdbcTemplate jdbcTemplate, String schemaName) {
    Assert.notNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.jdbcTemplate = jdbcTemplate;
    this.npJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);

    this.dbUtils = new DatabaseUtils(jdbcTemplate, npJdbcTemplate, schemaName);

    this.mappingUtils = new MappingUtils(dbUtils);
  }

  /**
   * Gets the JdbcTemplate of the jdbcTemplateMapper
   *
   * @return the JdbcTemplate
   */
  public JdbcTemplate getJdbcTemplate() {
    return this.jdbcTemplate;
  }

  /**
   * Gets the NamedParameterJdbcTemplate of the jdbcTemplateMapper
   *
   * @return the NamedParameterJdbcTemplate
   */
  public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
    return this.npJdbcTemplate;
  }

  /**
   * Assign this to identify the property name of created on field. This property has to be of type
   * LocalDateTime. When an object is inserted into the database the value of this field will be set
   * to current. Assign this while initializing jdbcTemplateMapper.
   *
   * @param propName : the created on property name.
   * @return The jdbcTemplateMapper
   */
  public JdbcTemplateMapper withCreatedOnPropertyName(String propName) {
    this.createdOnPropertyName = propName;
    return this;
  }

  /**
   * An implementation of IRecordOperatorResolver is used to populate the created by and updated by
   * fields. Assign this while initializing the jdbcTemplateMapper
   *
   * @param recordOperatorResolver The implement for interface IRecordOperatorResolver
   * @return The jdbcTemplateMapper The jdbcTemplateMapper
   */
  public JdbcTemplateMapper withRecordOperatorResolver(
      IRecordOperatorResolver recordOperatorResolver) {
    this.recordOperatorResolver = recordOperatorResolver;
    return this;
  }

  /**
   * Assign this to identify the property name of the created by field. The created by property will
   * be assigned the value from IRecordOperatorResolver.getRecordOperator() when the object is
   * inserted into the database. Assign this while initializing the jdbcTemplateMapper
   *
   * @param propName : the created by property name.
   * @return The jdbcTemplateMapper
   */
  public JdbcTemplateMapper withCreatedByPropertyName(String propName) {
    this.createdByPropertyName = propName;
    return this;
  }

  /**
   * Assign this to identify the property name of updated on field. This property has to be of type
   * LocalDateTime. When an object is updated in the database the value of this field will be set to
   * current. Assign this while initializing jdbcTemplateMapper.
   *
   * @param propName : the updated on property name.
   * @return The jdbcTemplateMapper
   */
  public JdbcTemplateMapper withUpdatedOnPropertyName(String propName) {
    this.updatedOnPropertyName = propName;
    return this;
  }

  /**
   * Assign this to identify the property name of updated by field. The updated by property will be
   * assigned the value from IRecordOperatorResolver.getRecordOperator when the object is updated in
   * the database. Assign this while initializing the jdbcTemplateMapper
   *
   * @param propName : the update by property name.
   * @return The jdbcTemplateMapper
   */
  public JdbcTemplateMapper withUpdatedByPropertyName(String propName) {
    this.updatedByPropertyName = propName;
    return this;
  }

  /**
   * The property used for optimistic locking. The property has to be of type Integer. If the object
   * has the version property name, on inserts it will be set to 1 and on updates it will
   * incremented by 1. Assign this while initializing jdbcTemplateMapper.
   *
   * @param propName The version propertyName
   * @return The jdbcTemplateMapper
   */
  public JdbcTemplateMapper withVersionPropertyName(String propName) {
    this.versionPropertyName = propName;
    return this;
  }

  /**
   * For databases which have a catalog set the catalog name
   *
   * @param catalogName The catalog
   */
  public void setCatalogName(String catalogName) {
    dbUtils.setCatalogName(catalogName);
  }

  /**
   * For most jdbc drivers when getting column metadata using jdbc, the columnPattern argument null
   * returns all the columns (which is the default for JdbcTemplateMapper). Some jdbc drivers may
   * require to pass something like '%'. Use this method to set the column pattern.
   *
   * @param metaDataColumnNamePattern
   */
  public void setMetaDataColumnNamePattern(String metaDataColumnNamePattern) {
    dbUtils.setMetaDataColumnNamePattern(metaDataColumnNamePattern);
  }

  /**
   * Returns the object by Id. Return null if not found
   *
   * @param id Id of object
   * @param clazz Class of object
   * @param <T> the type of the object
   * @return the object of the specific type
   */
  public <T> T findById(Object id, Class<T> clazz) {
    Assert.notNull(clazz, "Class must not be null");
    if (!(id instanceof Integer || id instanceof Long)) {
      throw new RuntimeException("id has to be type of Integer or Long");
    }

    TableMapping tableMapping = mappingUtils.getTableMapping(clazz);
    String idColumnName = tableMapping.getIdColumnName();
    if (idColumnName == null) {
      throw new RuntimeException(
          "Either "
              + clazz.getSimpleName()
              + " does not have an 'id' property or its corresponding table "
              + tableMapping.getTableName()
              + " does not have an 'id' column");
    }
    String sql =
        "SELECT * FROM "
            + dbUtils.fullyQualifiedTableName(tableMapping.getTableName())
            + " WHERE "
            + idColumnName
            + " = ?";
    RowMapper<T> mapper = BeanPropertyRowMapper.newInstance(clazz);
    try {
      Object obj = jdbcTemplate.queryForObject(sql, mapper, id);
      return clazz.cast(obj);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  /**
   * Find all objects
   *
   * @param clazz Type of object
   * @param <T> the type of the objects
   * @return List of objects
   */
  public <T> List<T> findAll(Class<T> clazz) {
    Assert.notNull(clazz, "Class must not be null");

    String tableName = mappingUtils.getTableMapping(clazz).getTableName();
    String sql = "SELECT * FROM " + dbUtils.fullyQualifiedTableName(tableName);
    RowMapper<T> mapper = BeanPropertyRowMapper.newInstance(clazz);
    return jdbcTemplate.query(sql, mapper);
  }

  /**
   * Find all objects and order them using the order by clause passed as argument
   *
   * @param clazz Type of object
   * @param <T> the type of the objects
   * @param orderByClause The order by sql
   * @return List of objects
   */
  public <T> List<T> findAll(Class<T> clazz, String orderByClause) {
    Assert.notNull(clazz, "Class must not be null");

    String tableName = mappingUtils.getTableMapping(clazz).getTableName();
    String sql =
        "SELECT * FROM " + dbUtils.fullyQualifiedTableName(tableName) + " " + orderByClause;
    RowMapper<T> mapper = BeanPropertyRowMapper.newInstance(clazz);
    return jdbcTemplate.query(sql, mapper);
  }

  /**
   * Inserts an object whose id in database is auto increment. Once inserted the object will have
   * the id assigned.
   *
   * <p>Also assigns created by, created on, updated by, updated on, version if these properties
   * exist for the object and the JdbcTemplateMapper is configured for them.
   *
   * @param obj The object to be saved
   */
  public void insert(Object obj) {
    Assert.notNull(obj, "Object must not be null");

    BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(obj);
    if (!bw.isReadableProperty("id")) {
      throw new RuntimeException(
          "Object " + obj.getClass().getSimpleName() + " has to have a property named 'id'.");
    }

    Object idValue = bw.getPropertyValue("id");
    if (idValue != null) {
      throw new RuntimeException(
          "For method insert() the objects 'id' property has to be null since this insert is for an object whose id is autoincrement in database.");
    }

    TableMapping tableMapping = mappingUtils.getTableMapping(obj.getClass());
    if (tableMapping.getIdColumnName() == null) {
      throw new RuntimeException(
          "table " + tableMapping.getTableName() + " does not have a property named 'id'");
    }
    LocalDateTime now = LocalDateTime.now();

    if (createdOnPropertyName != null
        && tableMapping.getColumnName(createdOnPropertyName) != null) {
      bw.setPropertyValue(createdOnPropertyName, now);
    }

    if (createdByPropertyName != null
        && recordOperatorResolver != null
        && tableMapping.getColumnName(createdByPropertyName) != null) {
      bw.setPropertyValue(createdByPropertyName, recordOperatorResolver.getRecordOperator());
    }
    if (updatedOnPropertyName != null
        && tableMapping.getColumnName(updatedOnPropertyName) != null) {
      bw.setPropertyValue(updatedOnPropertyName, now);
    }
    if (updatedByPropertyName != null
        && recordOperatorResolver != null
        && tableMapping.getColumnName(updatedByPropertyName) != null) {
      bw.setPropertyValue(updatedByPropertyName, recordOperatorResolver.getRecordOperator());
    }
    if (versionPropertyName != null && tableMapping.getColumnName(versionPropertyName) != null) {
      bw.setPropertyValue(versionPropertyName, 1);
    }

    Map<String, Object> attributes = CommonUtils.convertToSnakeCaseAttributes(obj);

    SimpleJdbcInsert jdbcInsert = simpleJdbcInsertCache.get(tableMapping.getTableName());
    if (jdbcInsert == null) {
      jdbcInsert =
          new SimpleJdbcInsert(jdbcTemplate)
              .withCatalogName(dbUtils.getCatalogName())
              .withSchemaName(dbUtils.getSchemaName())
              .withTableName(tableMapping.getTableName())
              .usingGeneratedKeyColumns(tableMapping.getIdColumnName());
      simpleJdbcInsertCache.put(tableMapping.getTableName(), jdbcInsert);
    }

    Number idNumber = jdbcInsert.executeAndReturnKey(attributes);
    bw.setPropertyValue("id", idNumber); // set auto increment id value on object
  }

  /**
   * Inserts an object whose id in database is NOT auto increment. In this case the object's 'id'
   * has to be assigned up front (using a sequence or some other way) and cannot be null.
   *
   * <p>Also assigns created by, created on, updated by, updated on, version if these properties
   * exist for the object and the JdbcTemplateMapper is configured for them.
   *
   * @param obj The object to be saved
   */
  public void insertWithId(Object obj) {
    Assert.notNull(obj, "Object must not be null");

    BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(obj);
    if (!bw.isReadableProperty("id")) {
      throw new RuntimeException(
          "Object " + obj.getClass().getSimpleName() + " has to have a property named 'id'.");
    }

    Object idValue = bw.getPropertyValue("id");
    if (idValue == null) {
      throw new RuntimeException(
          "For method insertById() the objects 'id' property cannot be null.");
    }

    TableMapping tableMapping = mappingUtils.getTableMapping(obj.getClass());
    if (tableMapping.getIdColumnName() == null) {
      throw new RuntimeException(
          "table " + tableMapping.getTableName() + " does not have a property named 'id'");
    }

    LocalDateTime now = LocalDateTime.now();
    if (createdOnPropertyName != null
        && tableMapping.getColumnName(createdOnPropertyName) != null) {
      bw.setPropertyValue(createdOnPropertyName, now);
    }
    if (createdByPropertyName != null
        && recordOperatorResolver != null
        && tableMapping.getColumnName(createdByPropertyName) != null) {
      bw.setPropertyValue(createdByPropertyName, recordOperatorResolver.getRecordOperator());
    }
    if (updatedOnPropertyName != null
        && tableMapping.getColumnName(updatedOnPropertyName) != null) {
      bw.setPropertyValue(updatedOnPropertyName, now);
    }
    if (updatedByPropertyName != null
        && recordOperatorResolver != null
        && tableMapping.getColumnName(updatedByPropertyName) != null) {
      bw.setPropertyValue(updatedByPropertyName, recordOperatorResolver.getRecordOperator());
    }
    if (versionPropertyName != null && tableMapping.getColumnName(versionPropertyName) != null) {
      bw.setPropertyValue(versionPropertyName, 1);
    }

    SimpleJdbcInsert jdbcInsert = simpleJdbcInsertCache.get(tableMapping.getTableName());
    if (jdbcInsert == null) {
      jdbcInsert =
          new SimpleJdbcInsert(jdbcTemplate)
              .withCatalogName(dbUtils.getCatalogName())
              .withSchemaName(dbUtils.getSchemaName())
              .withTableName(tableMapping.getTableName());

      simpleJdbcInsertCache.put(tableMapping.getTableName(), jdbcInsert);
    }

    Map<String, Object> attributes = CommonUtils.convertToSnakeCaseAttributes(obj);
    jdbcInsert.execute(attributes);
  }

  /**
   * Updates object. Assigns updated by, updated on if these properties exist for the object and the
   * jdbcTemplateMapper is configured for these fields. if optimistic locking 'version' property
   * exists for object throws an OptimisticLockingException if object is stale
   *
   * @param obj object to be updated
   * @return number of records updated
   */
  public Integer update(Object obj) {
    Assert.notNull(obj, "Object must not be null");

    if (!hasIdProperty(obj)) {
      throw new RuntimeException(
          "Object " + obj.getClass().getSimpleName() + " does not have a property named 'id'");
    }

    TableMapping tableMapping = mappingUtils.getTableMapping(obj.getClass());
    String idColumnName = tableMapping.getIdColumnName();
    if (idColumnName == null) {
      throw new RuntimeException(
          "table " + tableMapping.getTableName() + " does not have a column named 'id'");
    }

    UpdateSqlAndParams updateSqlAndParams = updateSqlAndParamsCache.get(obj.getClass().getName());

    if (updateSqlAndParams == null) {
      // ignore these attributes when generating the sql 'SET' command
      List<String> ignoreAttrs = new ArrayList<>();
      ignoreAttrs.add("id");
      if (createdByPropertyName != null) {
        ignoreAttrs.add(createdByPropertyName);
      }
      if (createdOnPropertyName != null) {
        ignoreAttrs.add(createdOnPropertyName);
      }

      Set<String> updatePropertyNames = new LinkedHashSet<>();

      for (PropertyInfo propertyInfo : CommonUtils.getObjectPropertyInfo(obj)) {
        // if not a ignore property and has a table column mapping add it to the update property
        // list
        if (!ignoreAttrs.contains(propertyInfo.getPropertyName())
            && tableMapping.getColumnName(propertyInfo.getPropertyName()) != null) {
          updatePropertyNames.add(propertyInfo.getPropertyName());
        }
      }
      updateSqlAndParams = buildUpdateSqlAndParams(tableMapping, updatePropertyNames);
      updateSqlAndParamsCache.put(obj.getClass().getName(), updateSqlAndParams);
    }
    return issueUpdate(updateSqlAndParams, obj, tableMapping);
  }

  /**
   * Updates the propertyNames (passed in as args) of the object. Assigns updated by, updated on if
   * these properties exist for the object and the jdbcTemplateMapper is configured for these
   * fields.
   *
   * @param obj object to be updated
   * @param propertyNames array of property names that should be updated
   * @return number of records updated (1 or 0)
   */
  public Integer update(Object obj, String... propertyNames) {
    Assert.notNull(obj, "Object must not be null");
    Assert.notNull(propertyNames, "propertyNames must not be null");

    if (!hasIdProperty(obj)) {
      throw new RuntimeException(
          "Object " + obj.getClass().getSimpleName() + " does not have a property named 'id'");
    }

    TableMapping tableMapping = mappingUtils.getTableMapping(obj.getClass());
    String idColumnName = tableMapping.getIdColumnName();
    if (idColumnName == null) {
      throw new RuntimeException(
          "table " + tableMapping.getTableName() + " does not have a column named 'id'");
    }

    // cachekey ex: className-propertyName1-propertyName2
    String cacheKey = obj.getClass().getName() + "-" + String.join("-", propertyNames);
    UpdateSqlAndParams updateSqlAndParams = updateSqlAndParamsCache.get(cacheKey);
    if (updateSqlAndParams == null) {
      // check properties have a corresponding table column
      for (String propertyName : propertyNames) {
        if (tableMapping.getColumnName(propertyName) == null) {
          throw new RuntimeException(
              "property "
                  + propertyName
                  + " is not a property of object "
                  + obj.getClass().getName()
                  + " or does not have a corresponding column in table "
                  + tableMapping.getTableName());
        }
      }

      // auto assigned  cannot be updated by user.
      List<String> autoAssignedAttrs = new ArrayList<>();
      autoAssignedAttrs.add("id");
      if (versionPropertyName != null && tableMapping.getColumnName(versionPropertyName) != null) {
        autoAssignedAttrs.add(versionPropertyName);
      }
      if (updatedOnPropertyName != null
          && tableMapping.getColumnName(updatedOnPropertyName) != null) {
        autoAssignedAttrs.add(updatedOnPropertyName);
      }
      if (updatedByPropertyName != null
          && recordOperatorResolver != null
          && tableMapping.getColumnName(updatedByPropertyName) != null) {
        autoAssignedAttrs.add(updatedByPropertyName);
      }

      for (String propertyName : propertyNames) {
        if (autoAssignedAttrs.contains(propertyName)) {
          throw new RuntimeException(
              "property "
                  + propertyName
                  + " is an auto assigned property which cannot be manually set in update statement");
        }
      }

      // add input properties to the update property list
      Set<String> updatePropertyNames = new LinkedHashSet<>();
      for (String propertyName : propertyNames) {
        updatePropertyNames.add(propertyName);
      }

      // add the auto assigned properties if configured and have table column mapping
      if (versionPropertyName != null && tableMapping.getColumnName(versionPropertyName) != null) {
        updatePropertyNames.add(versionPropertyName);
      }
      if (updatedOnPropertyName != null
          && tableMapping.getColumnName(updatedOnPropertyName) != null) {
        updatePropertyNames.add(updatedOnPropertyName);
      }
      if (updatedByPropertyName != null
          && recordOperatorResolver != null
          && tableMapping.getColumnName(updatedByPropertyName) != null) {
        updatePropertyNames.add(updatedByPropertyName);
      }

      updateSqlAndParams = buildUpdateSqlAndParams(tableMapping, updatePropertyNames);
      updateSqlAndParamsCache.put(cacheKey, updateSqlAndParams);
    }
    return issueUpdate(updateSqlAndParams, obj, tableMapping);
  }

  /**
   * Physically Deletes the object from the database
   *
   * @param obj Object to be deleted
   * @return number of records were deleted (1 or 0)
   */
  public Integer delete(Object obj) {
    Assert.notNull(obj, "Object must not be null");

    BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(obj);
    if (!bw.isReadableProperty("id")) {
      throw new RuntimeException(
          "Object " + obj.getClass().getSimpleName() + " has to have a property named 'id'.");
    }

    TableMapping tableMapping = mappingUtils.getTableMapping(obj.getClass());
    if (tableMapping.getIdColumnName() == null) {
      throw new RuntimeException(
          "table " + tableMapping.getTableName() + " does not have a property named 'id'");
    }

    String sql =
        "delete from "
            + dbUtils.fullyQualifiedTableName(tableMapping.getTableName())
            + " where id = ?";
    Object id = bw.getPropertyValue("id");
    return jdbcTemplate.update(sql, id);
  }

  /**
   * Physically Deletes the object from the database by id
   *
   * @param id Id of object to be deleted
   * @param clazz Type of object to be deleted.
   * @return number records were deleted (1 or 0)
   */
  public <T> Integer deleteById(Object id, Class<T> clazz) {
    Assert.notNull(clazz, "Class must not be null");

    if (!(id instanceof Integer || id instanceof Long)) {
      throw new RuntimeException("id has to be type of Integer or Long");
    }
    String tableName = mappingUtils.getTableMapping(clazz).getTableName();
    String sql = "delete from " + dbUtils.fullyQualifiedTableName(tableName) + " where id = ?";
    return jdbcTemplate.update(sql, id);
  }


  /**
   * Returns lists for each mapper passed in as an argument. The values in the list are UNIQUE and
   * in same order as the ResultSet values.
   *
   * @param rs The jdbc result set
   * @param selectMappers An array of sql mappers.
   * @return Map <pre>
   *         key: 'sqlColumnPrefix' of each sqlMapper
   *         value: unique list of objects mapped by the sqlMapper
   */
  @SuppressWarnings("all")
  public Map<String, List> multipleModelMapper(ResultSet rs, SelectMapper... selectMappers) {
    Assert.notNull(selectMappers, "selectMappers must not be null");

    try {
      Map<String, LinkedHashMap<Long, Object>> tempMap = multipleModelMapperRaw(rs, selectMappers);
      Map<String, List> resultMap = new HashMap<>();
      for (String key : tempMap.keySet()) {
        resultMap.put(key, new ArrayList<Object>(tempMap.get(key).values()));
      }
      return resultMap;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a LinkedHashmap for each mapper passed in as an argument. The values in the hashmap are
   * in same order as the ResultSet values.
   *
   * @param rs The jdbc result set
   * @param selectMappers array of sql mappers.
   * @return Map <pre>
   *        key: 'sqlColumnPrefix' of each sqlMapper,
   *        value: LinkedHashMap of objects mapped by sqlMapper.
   *                   LinkedHashMap key - id of object
   *                   LinkedHashMap value - the object
   */
  @SuppressWarnings("all")
  private Map<String, LinkedHashMap<Long, Object>> multipleModelMapperRaw(
      ResultSet rs, SelectMapper... selectMappers) {
    Assert.notNull(selectMappers, "selectMappers must not be null");

    try {
      // LinkedHashMap used to retain the order of insertion of records
      // Map key - SelectMapper's sql column prefix
      // LinkedHashMap key - id of object
      // LinkedHashMap value - the object
      Map<String, LinkedHashMap<Long, Object>> resultMap = new HashMap<>();
      for (SelectMapper selectMapper : selectMappers) {
        resultMap.put(selectMapper.getSqlColumnPrefix(), new LinkedHashMap<>());
      }
      List<String> resultSetColumnNames = dbUtils.getResultSetColumnNames(rs);
      while (rs.next()) {
        for (SelectMapper selectMapper : selectMappers) {
          Number idVal = (Number) rs.getObject(selectMapper.getSqlColumnPrefix() + "id");
          if (idVal != null && idVal.longValue() > 0) {
            Object obj =
                constructInstance(
                    selectMapper.getClazz(),
                    rs,
                    selectMapper.getSqlColumnPrefix(),
                    resultSetColumnNames);
            resultMap.get(selectMapper.getSqlColumnPrefix()).put(idVal.longValue(), obj);
          }
        }
      }

      return resultMap;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Generates a string which can be used in a sql select statement with all the columns of the
   * table.
   *
   * <pre>
   * selectColumns("employee", "emp") where "emp" is the alias will return something like:
   * "emp.id emp_id, emp.last_name emp_last_name, emp.first_name emp_first_name"
   * </pre>
   *
   * @param tableName the Table name
   * @param tableAlias the alias being used in the sql statement for the table.
   * @return comma separated select column string
   */
  public String selectColumns(String tableName, String tableAlias) {
    Assert.hasLength(tableName, "tableName must not be empty");
    Assert.hasLength(tableAlias, "tableAlias must not be empty");

    String str = selectColumnsCache.get(tableName + "-" + tableAlias);
    if (str == null) {
      List<ColumnInfo> tableColumnInfo = dbUtils.getTableColumnInfo(tableName);
      if (CommonUtils.isEmpty(tableColumnInfo)) {
        // try with uppercase table name
        tableColumnInfo = dbUtils.getTableColumnInfo(tableName.toUpperCase());
        if (CommonUtils.isEmpty(tableColumnInfo)) {
          throw new RuntimeException("Could not get column info for table named " + tableName);
        }
      }
      StringBuilder sb = new StringBuilder(" ");
      for (ColumnInfo colInfo : tableColumnInfo) {
        sb.append(tableAlias)
            .append(".")
            .append(colInfo.getColumnName())
            .append(" ")
            .append(tableAlias)
            .append("_")
            .append(colInfo.getColumnName())
            .append(",");
      }
      str = sb.toString();
      // remove the last comma.
      str = str.substring(0, str.length() - 1) + " ";
      selectColumnsCache.put(tableName + "-" + tableAlias, str);
    }
    return str;
  }

  private UpdateSqlAndParams buildUpdateSqlAndParams(
      TableMapping tableMapping, Set<String> propertyNames) {
    Assert.notNull(tableMapping, "tableMapping must not be null");
    Assert.notNull(propertyNames, "propertyNames must not be null");

    String idColumnName = tableMapping.getIdColumnName();
    if (idColumnName == null) {
      throw new RuntimeException(
          "could not find id column for table " + tableMapping.getTableName());
    }
    Set<String> params = new HashSet<>();
    StringBuilder sqlBuilder = new StringBuilder("UPDATE ");
    sqlBuilder.append(dbUtils.fullyQualifiedTableName(tableMapping.getTableName()));
    sqlBuilder.append(" SET ");

    String versionColumnName = tableMapping.getColumnName(versionPropertyName);
    boolean first = true;
    for (String propertyName : propertyNames) {
      String columnName = tableMapping.getColumnName(propertyName);
      if (columnName != null) {
        if (!first) {
          sqlBuilder.append(", ");
        } else {
          first = false;
        }
        sqlBuilder.append(columnName);
        sqlBuilder.append(" = :");

        if (versionPropertyName != null && columnName.equals(versionColumnName)) {
          sqlBuilder.append("incrementedVersion");
          params.add("incrementedVersion");
        } else {
          sqlBuilder.append(propertyName);
          params.add(propertyName);
        }
      }
    }

    // the where clause
    sqlBuilder.append(" WHERE " + idColumnName + " = :id");
    params.add("id");

    if (versionPropertyName != null && versionColumnName != null) {
      sqlBuilder
          .append(" AND ")
          .append(versionColumnName)
          .append(" = :")
          .append(versionPropertyName);
      params.add(versionPropertyName);
    }

    String updateSql = sqlBuilder.toString();
    UpdateSqlAndParams updateSqlAndParams = new UpdateSqlAndParams(updateSql, params);

    return updateSqlAndParams;
  }

  private Integer issueUpdate(
      UpdateSqlAndParams updateSqlAndParams, Object obj, TableMapping tableMapping) {
    Assert.notNull(updateSqlAndParams, "updateSqlAndParams must not be null");
    Assert.notNull(obj, "Object must not be null");

    BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(obj);
    Set<String> parameters = updateSqlAndParams.getParams();
    if (updatedOnPropertyName != null && parameters.contains(updatedOnPropertyName)) {
      bw.setPropertyValue(updatedOnPropertyName, LocalDateTime.now());
    }
    if (updatedByPropertyName != null
        && recordOperatorResolver != null
        && parameters.contains(updatedByPropertyName)) {
      bw.setPropertyValue(updatedByPropertyName, recordOperatorResolver.getRecordOperator());
    }

    MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource();
    for (String paramName : parameters) {
      if (paramName.equals("incrementedVersion")) {
        Integer versionVal = (Integer) bw.getPropertyValue(versionPropertyName);
        if (versionVal == null) {
          throw new RuntimeException(
              versionPropertyName
                  + " cannot be null when updating "
                  + obj.getClass().getSimpleName());
        } else {
          mapSqlParameterSource.addValue(
              "incrementedVersion", versionVal + 1, java.sql.Types.INTEGER);
        }
      } else {
        mapSqlParameterSource.addValue(
            paramName, bw.getPropertyValue(paramName), tableMapping.getPropertySqlType(paramName));
      }
    }

    // if object has property version the version gets incremented on update.
    // throws OptimisticLockingException when update fails.
    if (updateSqlAndParams.getParams().contains("incrementedVersion")) {
      int cnt = npJdbcTemplate.update(updateSqlAndParams.getSql(), mapSqlParameterSource);
      if (cnt == 0) {
        throw new OptimisticLockingException(
            "Update failed for "
                + obj.getClass().getSimpleName()
                + " for id:"
                + bw.getPropertyValue("id")
                + " and "
                + versionPropertyName
                + ":"
                + bw.getPropertyValue(versionPropertyName));
      }
      // update the version in object with new version
      bw.setPropertyValue(
          versionPropertyName, mapSqlParameterSource.getValue("incrementedVersion"));
      return cnt;
    } else {
      return npJdbcTemplate.update(updateSqlAndParams.getSql(), mapSqlParameterSource);
    }
  }

  /**
   * Used by mappers to construct an object from the result set
   *
   * @param clazz Class of object to be instantiated. Object should have a no argument constructor
   * @param rs The sql result set
   * @param prefix The sql column alias prefix in the query
   * @param resultSetColumnNames The column names in the sql statement.
   * @return Object of type T populated from the data in the result set
   */
  private <T> T constructInstance(
      Class<T> clazz, ResultSet rs, String prefix, List<String> resultSetColumnNames) {

    Assert.notNull(clazz, "clazz must not be null");
    Assert.hasLength(prefix, "prefix must not be empty");
    Assert.notNull(resultSetColumnNames, "resultSetColumnNames must not be null");

    try {
      Object obj = clazz.newInstance();
      BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(obj);
      // need below for java.sql.Timestamp to java.time.LocalDateTime conversion etc
      bw.setConversionService(defaultConversionService);
      for (PropertyInfo propertyInfo : CommonUtils.getObjectPropertyInfo(obj)) {
        String columnName = CommonUtils.convertCamelToSnakeCase(propertyInfo.getPropertyName());
        if (CommonUtils.isNotEmpty(prefix)) {
          columnName = prefix + columnName;
        }
        int index = resultSetColumnNames.indexOf(columnName.toLowerCase());
        if (index != -1) {
          // JDBC index starts at 1. using Springs JdbcUtils to get values from resultSet
          bw.setPropertyValue(
              propertyInfo.getPropertyName(),
              JdbcUtils.getResultSetValue(rs, index + 1, propertyInfo.getPropertyType()));
        }
      }
      return clazz.cast(obj);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * checks if an object has an property named 'id'
   *
   * @param obj The object
   * @return true/false depending on if obj has 'id' property
   */
  private boolean hasIdProperty(Object obj) {
    List<PropertyInfo> propertyInfoList = CommonUtils.getObjectPropertyInfo(obj);
    PropertyInfo propertyInfo =
        propertyInfoList
            .stream()
            .filter(pi -> "id".equals(pi.getPropertyName()))
            .findAny()
            .orElse(null);

    return propertyInfo == null ? false : true;
  }
}
