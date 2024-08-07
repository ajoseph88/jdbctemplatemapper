# JdbcTemplateMapper #
 
A simple library that makes the usage of Spring JdbcTemplate less verbose for CRUD and relationship queries.
Use it where appropriate and for other features keep using JdbcTemplate as you normally would.
 
Note that this is not an ORM. It's a wrapper around JdbcTemplate so there are no sessions, flushing, level 1 and 2 caches, detached objects, magical inserts/updates/deletes, N+1 queries, unproxy and other challenges of an ORM.
  
[Javadoc](https://jdbctemplatemapper.github.io/jdbctemplatemapper/index.html) | [Website](https://jdbctemplatemapper.github.io/jdbctemplate-crud-spring/)
 
## Features

  1. One liners for CRUD.
  2. Fluent style queries for hasOne, hasMany, hasMany through (many to many) and other ways to query relationships.
  3. Auto assign properties for models:
      * auto assign created on, updated on.
      * auto assign created by, updated by using an implementation of interface IRecordOperatorResolver.
      * optimistic locking feature for updates.
  4. For transaction management use Spring transactions since the library uses JdbcTemplate for all database interactions.
  5. To log the SQL statements use the same logging configurations as JdbcTemplate. See the logging section further below.
  6. Tested against PostgreSQL, MySQL, Oracle, SQLServer (Unit tests are run against these databases). Should work with other relational databases.
  7. Distribution is compiled with java8. Works with java8, java11, java17, java21.
  8. Only dependency is spring-boot-starter-jdbc which is needed for JdbcTemplate.

## Example code
  ```java 
 //@Table annotation is required
 @Table(name="product")
 public class Product {
     // @Id annotation is required.
     // For auto increment database id use @Id(type=IdType.AUTO_INCREMENT). id value will be set on insert
     // For non auto increment id use @Id. In this case the id value will have to be manually set 
     // before invoking insert(). Non auto increment id type can be something other than a Number.
 
     @Id(type=IdType.AUTO_INCREMENT)
     private Integer id; 
             
     @Column(name="product_name")            // Please read documentation of this annotation.
     private String name;                    // will map to product_name column in table.
     
     @Column
     private LocalDateTime availableDate;    // will map to column available_date by default 
     
     @Column
     private Double price;                   // will map to price column by default
     
     private String someNonDatabaseProperty; // No annotations so excluded from inserts/updates/queries
 
     ...
 }
 
 Product product = new Product();
 product.setName("some product name");
 product.setPrice(10.25);
 product.setAvailableDate(LocalDateTime.now());
 
 jdbcTemplateMapper.insert(product); // because id type is auto increment, id value will be set on insert.

 product = jdbcTemplateMapper.findById(Product.class, product.getId());
 product.setPrice(11.50);
 jdbcTemplateMapper.update(product);
 
 // updateProperties() updates only the specified properties passed as arguments.
 product.setPrice(12.50);
 jdbcTemplateMapper.updateProperties(product, "price"); // will issue an SQL update only for price.
 
 List<Product> products = jdbcTemplateMapper.findAll(Product.class);
 
 jdbcTemplateMapper.delete(product);
 
 jdbcTemplateMapper.delete(Product.class, 5); // delete using id
 
 // access JdbcTemplate and NamedParameterJdbcTemplate.
 JdbcTemplate jdbcTemplate = jtm.getJdbcTemplate();
 NamedParameterJdbcTemplate namedParameterJdbcTemplate = jtm.getNamedParameterJdbcTemplate();
 
 ```
 
 See [Querying Relationships](#querying-relationships) for query features of the library.
 
## Maven coordinates
 ``` 
  <dependency>
    <groupId>io.github.jdbctemplatemapper</groupId>
    <artifactId>jdbctemplatemapper</artifactId>
    <version>3.1.0</version>
 </dependency>
 ```
 
## Spring bean configuration for JdbcTemplateMapper

JdbcTemplateMapper should be prepared in a Spring application context and given to services as a bean reference. It caches table meta-data etc.

**Note: An instance of JdbcTemplateMapper is thread safe**

See an example of JdbcTemplateMapper configuration used in an application [here](https://github.com/jdbctemplatemapper/simple-crud-for-spring-jdbctemplate/blob/master/src/test/java/io/github/ajoseph88/jdbctemplatemapper/config/JdbcTemplateMapperConfig.java).

Depending on the versions of springboot/database/driver, changes may be required for the properties.

**PostgreSQL**

```  
# application.properties
spring.datasource.jdbc-url=jdbc:postgresql://HOST:PORT/THE_DATABASE_NAME
spring.datasource.username=username
spring.datasource.password=password
spring.datasource.driver-class-name=org.postgresql.Driver

  // DataSource properties are read from application.properties.
  @Bean
  @ConfigurationProperties(prefix = "spring.datasource")
  public DataSource sqlDataSource() {
    return DataSourceBuilder.create().build();
  }

 // Once Spring identifies that a DataSource bean is configured, it automatically configures a default JdbcTemplate
 // bean using the DataSource. Use the JdbcTemplate bean to configure JdbcTemplateMapper.
 @Bean
  public JdbcTemplateMapper jdbcTemplateMapper(JdbcTemplate jdbcTemplate) {
    return new JdbcTemplateMapper(jdbcTemplate, THE_SCHEMA_NAME);
  }
  
```
 
**MySQL**

```  
# application.properties
spring.datasource.jdbc-url=jdbc:mysql://HOST:PORT/THE_DATABASE_NAME
spring.datasource.username=username
spring.datasource.password=password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

  // DataSource properties are read from application.properties.
  @Bean
  @ConfigurationProperties(prefix = "spring.datasource")
  public DataSource sqlDataSource() {
    return DataSourceBuilder.create().build();
  }

 @Bean
  public JdbcTemplateMapper jdbcTemplateMapper(JdbcTemplate jdbcTemplate) {
    return new JdbcTemplateMapper(jdbcTemplate, null, THE_DATABASE_NAME); // catalog name is synonymous to database name for mysql
  }

```

**Oracle**

```
# application.properties
spring.datasource.jdbc-url=jdbc:oracle:thin:@HOST:PORT/THE_SERVICE_NAME
spring.datasource.username=username
spring.datasource.password=password
spring.datasource.driver-class-name=oracle.jdbc.driver.OracleDriver

  // DataSource properties are read from application.properties.
  @Bean
  @ConfigurationProperties(prefix = "spring.datasource")
  public DataSource sqlDataSource() {
    return DataSourceBuilder.create().build();
  }
  
 // Once Spring identifies that a DataSource bean is configured, it automatically configures a default JdbcTemplate
 // bean using the DataSource. Use the JdbcTemplate bean to configure JdbcTemplateMapper.
 @Bean
  public JdbcTemplateMapper jdbcTemplateMapper(JdbcTemplate jdbcTemplate) {   
    JdbcTemplateMapper jdbcTemplateMapper = new JdbcTemplateMapper(jdbcTemplate, THE_SCHEMA_NAME);
     // This is oracle specific only. Need this for oracle to handle table synonyms 
    jdbcTemplateMapper.includeSynonymsForTableColumnMetaData();
    return jdbcTemplateMapper;
  }

```

**SQLServer**

```
# application.properties
spring.datasource.jdbc-url=jdbc:sqlserver://HOST:PORT;databaseName=THE_DATABASE_NAME;encrypt=true;trustServerCertificate=true;
spring.datasource.username=username
spring.datasource.password=password
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

  // DataSource properties are read from application.properties.
  @Bean
  @ConfigurationProperties(prefix = "spring.datasource")
  public DataSource sqlDataSource() {
    return DataSourceBuilder.create().build();
  }
  
 // Once Spring identifies that a DataSource bean is configured, it automatically configures a default JdbcTemplate
 // bean using the DataSource. Use the JdbcTemplate bean to configure JdbcTemplateMapper.
 @Bean
  public JdbcTemplateMapper jdbcTemplateMapper(JdbcTemplate jdbcTemplate) {
    return new JdbcTemplateMapper(jdbcTemplate, THE_SCHEMA_NAME);
  }
  
```

## Annotations

**@Table**

Required class level annotation. The table or view should exist in database. Examples below:

```java
@Table(name="product")
class Product {
  ...
}

@Table(name="product", schema="someSchemaName") 
class Product {
  ...
}

@Table(name="product", catalog="someDatabaseName")  // for mysql database name is synonymous with catalog name
class Product {
  ...
}

```

**@Id**

There are 2 forms of usage for this.

* **auto incremented id usage**

```java
@Table(name="product")
class Product {
 @Id(type=IdType.AUTO_INCREMENT)
 private Integer productId;
  ...
}

```
After a successful insert() operation the productId property will be populated with the new id.

* **NON auto incremented id usage**

```java
@Table(name="customer")
class Customer {
 @Id
 private Integer id;
  ...
}
```

In this case you will have to manually set the id value before invoking insert()  
Since this is NOT an auto increment id, it does not need to be a Number. It can for example be a String.

**@Column**

Properties that need be persisted to the database will need @Column annotation unless the property is already annotated with one of the other annotations (@Id, @Version, @CreatedOn @CreatedBy @UpdatedOn @UpdatedBy). @Column can be used with the other annotations to map to a different column name.

The two ways to use it:

@Column  
This will map property to a column using the default naming convention of camel case to underscore name.

@Column(name="some_column_name")  
This will map the property to the column specified by name attribute.   
Note that this will impact using "SELECT * " with Spring BeanPropertyRowMapper in custom queries. The mismatch of column and property names will cause BeanPropertyRowMapper to ignore these properties. Use "SELECT " + jdbcTemplateMapper.getBeanColumnsSql(someClass) which will create column aliases to match property names so will work with BeanPropertyRowMapper.

**@Version**

This annotation is used for optimistic locking. It has to be of type Integer.
Will be set to 1 when record is created and will incremented on updates. On updates if the version is stale an OptimisticLockingException will be thrown.  @Column annotation can be used with this to map to a different column name.

**@CreatedOn**

When record is created the property will be set. It has to be of type LocalDateTime. @Column annotation can be used with this to map to a different column name.

**@UpdatedOn**

On updates  the property will be set. It has to be of type LocalDateTime. @Column annotation can be used with this to map to a different column name.

**@CreatedBy**

If IRecordOperatorResolver is implemented and configured with JdbcTemplateMapper the value will be set to value returned by implementation when the record is created. Without configuration no values will be set. The type returned should match the type of the property. @Column annotation can be used with this to map to a different column name.

**@UpdatedBy**

If IRecordOperatorResolver is implemented and configured with JdbcTemplateMapper the value will be set to value returned by implementation when the record is updated. Without configuration no values will be set. The type returned should match the type of the property. @Column annotation can be used with this to map to a different column name.
  

 Annotation examples:

```java
@Table(name="product")
class Product {

 @Id(type=IdType.AUTO_INCREMENT)
 private Integer productId; 
 
 @Column(name="product_name")
 private String name;              // maps to product_name column
 
 @Column
 private String productDescription // defaults to column product_description 
 
 @CreatedOn 
 private LocalDateTime createdTimestamp;  // defaults to column created_timestamp. Property type should be LocalDateTime 
  
 @CreatedBy
 private String createdByUser;     // defaults to column created_by_user. 
                                   // Property type should match return value of implementation of IRecordOperatorResolver.  
  
 @UpdatedOn
 private LocalDateTime updatedAt;  // defaults to column updated_at. Property type should be LocalDateTime  
 
 @Column(name="last_update_user")
 @UpdatedBy
 private String updatedBy;         // maps to column last_update_user. 
                                   // Property type should match return value of implementation of IRecordOperatorResolver.
   
 @Version
 private Integer version;          // defaults to column version, 
                                   // Property type should be Integer. Used for optimistic locking
  
}
```

## Configuration for auto assigning @CreatedBy and @UpdateBy
 
```java
@Bean
public JdbcTemplateMapper jdbcTemplateMapper(JdbcTemplate jdbcTemplate) {
    JdbcTemplateMapper jdbcTemplateMapper = new JdbcTemplateMapper(jdbcTemplate);
    return jdbcTemplateMapper
        .withRecordOperatorResolver(new YourImplementationOfIRecordOperatorResolver());
}
```
 
## Querying relationships
The library provides multiple ways to query relationships.
Fluent style queries allow querying of hasOne, hasMany and hasMany through (many to many using an associated table) relationships.
The query where  and order by clauses are just SQL.
The IDE will provide suggestions to help chain the methods. Turn logging on (see logging section) to see the generated queries.  
The QueryMerge class allows the results of a previous query to be merged with results of a new query. This comes in handy where multiple relationships need to be queried.

### Fluent style queries

```
// Gets all orders. This is equivalent to jdbcTemplateMapper.findAll(Order.class);
List<Order> orders = Query.type(Order.class)
                          .execute(jdbcTemplateMapper); //execute with jdbcTemplateMapper
                          
// with where and orderBy
List<Order> orders = 
  Query.type(Order.class)
       .where("orders.status = ? and orders.customer_id = ?", "COMPLETE", 1) // good practice to parameterize the where clause
       .orderBy("orders.id desc")
       .execute(jdbcTemplateMapper);
       
// with where and orderBy with named parameters
List<Order> orders = 
  Query.type(Order.class)
       .where("orders.status = :status and orders.customer_id = :customerId", 
                                       new MapSqlParameterSource().addValue("status", "COMPLETE").addValue("customerId", 1))
       .orderBy("orders.id desc")
       .execute(jdbcTemplateMapper); 
       
// with where and orderBy with table alias. Aliases can be used in relationship queries too.
List<Order> orders = 
  Query.type(Order.class, "o")  // "o" is the table alias
       .where("o.status = ? and o.customer_id = ?", "COMPLETE", 1)
       .orderBy("o.id desc")
       .execute(jdbcTemplateMapper);       
       

// hasOne relationship         
List<Order> orders = 
  Query.type(Order.class) // type class
       .hasOne(Customer.class) // related Class
       .joinColumnTypeSide("customer_id") // hasOne() join column is on type (Order) side table. No table prefixes.
       .populateProperty("customer") // property on type class to populate. The properties class should match related class
       .where("orders.status = ?", "COMPLETE") 
       .orderBy("orders.id DESC, customer.id")  // type and related table columns can be used
       .execute(jdbcTemplateMapper);
              
// hasMany relationship         
List<Order> orders = 
  Query.type(Order.class) 
       .hasMany(OrderLine.class) // related class
       .joinColumnManySide("order_id") // hasMany() join column is on the many side table. No table prefixes
       .populateProperty("orderLines") // has to be an initialized collection and its generic class should match related class
       .where("orders.status = ?", "COMPLETE")
       .orderBy("orders.id, order_line.id")
       .execute(jdbcTemplateMapper); // execute with jdbcTemplateMapper   
       
 // employees hasMany skills through associated table 'employee_skill' (many to many)
 List<Employee> employees =  
   Query.type(Employee.class) // type class
        .hasMany(Skill.class) // related class
        .throughJoinTable("employee_skill") // the associated table
        .throughJoinColumns("employee_id", "skill_id")  // note order of join columns. type join column is first
        .populateProperty("skills") 
        .execute(jdbcTemplateMapper);                  
         
```

### Merging query results with QueryMerge

The Query api allows one relationship to be queried at a time. If multiple relationships need to be queried, using QueryMerge is an option. It merges the results of a query with results from another query. 
QueryMerge uses an sql 'IN' clause to retrieve records. If the number of records are larger than 100, multiple IN clause queries will be issued with each having up to 100 entries to retrieve all the records.

### Example using Query and QueryMerge together
Relationship:  Order hasOne Customer, Order hasMany OrderLine, OrderLine hasOne Product

```java

 @Table(name = "orders")
  public class Order {
    @Id(type = IdType.AUTO_INCREMENT)
    private Integer id;                             // column id    
    @Column
    private LocalDateTime orderDate;                // column order_date    
    @Column
    private String status;                          // column status  
    @Column
    private Integer customerId;                     // column customer_id. foreign key
    
    private Customer customer;                      // There are no mappings for relationships
    List<OrderLine> orderLines = new ArrayList<>(); // There are no mappings for relationships
                                                    // Important: collections have to be initialized
                                                    // so that the queries can populate them.
  }
  
  @Table(name="order_line")
  public class OrderLine {
    @Id(type = IdType.AUTO_INCREMENT)   
    private Integer id; // column id   
    @Column
    private Integer orderId; // column order_id. foreign key   
    @Column
    private Integer numOfUnits; // column num_of_units   
    @Column
    private Integer productId; // column product_id. foreign key

    private Product product; // There are no mappings for relationships
  }
  
  @Table(name="product")
  public class Product {
    @Id(type = IdType.AUTO_INCREMENT)
    private Integer id;           // column id    
    @Column
    private String name;          // column name    
    @Column
    private Double price;         // column price
  }
  
  @Table(name="customer")
  public class Customer {
    @Id(type = IdType.AUTO_INCREMENT)
    private Integer id;           // column id      
    @Column
    private String name;          // column name
  }
  
```
 
 1) Below query will return a list of orders with their 'orderLines' populated.
 
 ```
 List<Order> orders = 
    Query.type(Order.class) 
         .hasMany(OrderLine.class) 
         .joinColumnManySide("order_id") 
         .populateProperty("orderLines") 
         .where(orders.status = ?, "COMPLETE") 
         .orderBy("orders.order_date DESC, order_line.id") 
         .execute(jdbcTemplateMapper); 
 
 ```
   
 2) Populate the Order hasOne Customer relationship for the above orders.
    For this use QueryMerge. It merges the results of the query with the orders list from previous query. QueryMerge issues an 'IN' sql clause to get the pertinent customer records for orders and merges the results.
    
  ``` 
   QueryMerge.type(Order.class)
             .hasOne(Customer.class)
             .joinColumnTypeSide("customer_id")
             .populateProperty("customer")
             .execute(jdbcTemplateMapper, orders); // issues and IN clause to get customer records and merges into orders list
     
  ```
             
 Now we have Order and its orderLines (1st query) and orders with its Customer (second query merge)   
   
 3) Finally we need to populate the product for each OrderLine.
    Each order could have multiple orderLines so it is a list of lists.
    To get all the orderLines as a consolidated list we need to flatten the list of lists.
 
  ```       
   List<OrderLine> consolidatedOrderLines = orders.stream()
                                                  .map(o -> o.getOrderLines())
                                                  .flatMap(list -> list.stream())
                                                  .collect(Collectors.toList());
                 
   QueryMerge.type(OrderLine.class)
             .hasOne(Product.class)
             .joinColumnTypeSide("product_id")
             .populateProperty("product") 
             .execute(jdbcTemplateMapper, consolidatedOrderLines); // issues an sql 'IN' clause to get product records and merges
             
 ```
 
### hasMany through (many to many)
This allows querying of hasMany relationship through an associated table (many to many)

```
@Table(name = "employee")
public class Employee {
    @Id(type = IdType.AUTO_INCREMENT)
    private Integer id;   
    @Column
    private String lastName;
    @Column
    private String firstName;
        
    private List<Skill> skills = new ArrayList<>();
    ...
}

@Table(name = "skill")
public class Skill {
    @Id(type = IdType.AUTO_INCREMENT)
    private Integer id;
    @Column
    private String name;
    
    private List<Employee> employees = new ArrayList<>();
    ...
}    

@Table(name = "employee_skill")
public class EmployeeSkill {
    @Id(type = IdType.AUTO_INCREMENT)
    private Integer id;
    @Column
    private Integer employeeId;
    @Column
    private Integer skillId;
    ...
 }   
    
 // employees with skills populated.
 List<Employee> employees =  
   Query.type(Employee.class) //type class
        .hasMany(Skill.class)
        .throughJoinTable("employee_skill") // the associated table
        .throughJoinColumns("employee_id", "skill_id") // note order of join columns. type join column is first
        .populateProperty("skills") 
        .execute(jdbcTemplateMapper);
 
 // Skills with employees populated
 List<Skill> skills = 
   Query.type(Skill.class) // type class
        .hasMany(Employee.class) 
        .throughJoinTable("employee_skill") // the associated table
        .throughJoinColumns("skill_id", "employee_id") // note order of join columns. type join column is first
        .populateProperty("employees")
        .execute(jdbcTemplateMapper);
 
```

### Paginated queries
Paginated queries are supported with some limitations.

#### limitOffsetClause
limitOffsetClause is supported for non relationship queries and hasOne relationship queries. There is no support for hasMany/hasManythrough relationships.  

To achieve pagination functionality for hasMany/hasManythrough relationships one option is to issue a query for the main class with the limitOffsetClause and then use QueryMerge to populate the hasMany/hasManythrough side of the relationship. Example below:

```
 // a paginated query for orders. Issuing a hasOne relationship query here. It could have been a non relationship query
 List<Order> orders = 
    Query.type(Order.class)
         .hasOne(Customer.class)
         .joinColumnTypeSide("customer_id") 
         .populateProperty("customer") 
         .where("orders.status = ?", "COMPLETE")
         .orderBy("orders.id") // always order paginated queries. Otherwise databases will return random records per query.
         .limitOffsetClause("OFFSET 0 ROWS FETCH FIRST 10 ROWS ONLY")  // postgres syntax. Would be different for other databases.
         .execute(jdbcTemplateMapper);
    
 // merge the the orderLines into the orders list from the above paginated query
 QueryMerge.type(Order.class)
           .hasMany(OrderLine.class)
           .joinColumnManySide("order_id")
           .populateProperty("orderLines")
           .orderBy("order_line.id") // hasMany side can be ordered
           .execute(jdbcTemplateMapper, orders); // issues an sql 'IN' clause to get OrderLine records and merges results into orders list.
         
```

#### QueryCount
To get corresponding record counts for paginated queries use the QueryCount class. It has no support hasMany()/hasManythrough relationships.

Example to get count for the paginated order query above:

```
 Integer count = QueryCount.type(Order.class)
                           .hasOne(Customer.class)
                           .joinColumnTypeSide("customer_id") 
                           .where("orders.status = ?", "COMPLETE")
                           .execute(jdbcTemplateMapper);
                       
```
Note that the following methods are missing in QueryCount when compared to its corresponding paginated Query:

populateProperty() - Not needed since we are just getting a count.  
orderBy()          - Not needed because orderBy does not change the count of records returned.  
limitOffsetClause  - Not needed since we want the total count not a subset.  

### Dynamic Queries
A simple dynamic query example given below:

```
IQueryFluent<Person> qry = (IQueryFluent<Person>) Query.type(Person.class);

qry.where(yourDynamicWhereClause());
qry.orderBy(yourDynamicOrderByClause());
qry.limitOffsetClause(yourDynamicLimitOffsetClause());

List<Person> persons = qry.execute(jtm); // get the query results.
```

### Querying multiple relationships with a single query
To query multiple relationships with a single query use SelectMapper with Spring ResultSetExtractor.  
SelectMapper allows generating the select columns string for the models and population of the models from a ResultSet. 
   
An example for querying the following relationship: Order hasOne Customer, Order hasMany OrderLine, OrderLine hasOne Product  

```
 // The second argument to getSelectMapper() below is the table alias in the query and should match what is exactly used in query
 // For the query below the 'orders' table alias is 'o', 'customer' table alias is 'c', 'order_line' table alias 
 // is 'ol' and the product table alias is 'p'
 // SelectMapper.getColumnsSql() aliases all columns with the prefix: table alias + "_" so that there are no conflicts in sql
 // when different models have same property names like for example 'id'.
 // SelectMapper.buildModel(rs) uses the column alias prefix to populate the pertinent model from the ResultSet.
 
 SelectMapper<Order> orderSelectMapper = jdbcTemplateMapper.getSelectMapper(Order.class, "o");
 SelectMapper<Customer> customerSelectMapper = jdbcTemplateMapper.getSelectMapper(Customer.class, "c");
 SelectMapper<OrderLine> orderLineSelectMapper = jdbcTemplateMapper.getSelectMapper(OrderLine.class, "ol");
 SelectMapper<Product> productSelectMapper = jdbcTemplateMapper.getSelectMapper(Product.class, "p");

 // no need to type in column names and aliases so we can concentrate on join and where clauses
 String sql = "select" 
               + orderSelectMapper.getColumnsSql() 
               + ","
               + customerSelectMapper.getColumnsSql() 
               + ","
               + orderLineSelectMapper.getColumnsSql() 
               + "," 
               + productSelectMapper.getColumnsSql()
               + " from orders o" 
               + " left join customer c on o.customer_id = c.id
               + " left join order_line ol on o.id = ol.order_id"
               + " left join product p on ol.product_id = p.id"
               + " where o.status = ?"
               + " order by o.id, ol.id";
  
 // using Spring ResultSetExtractor                                      	
 ResultSetExtractor<List<Order>> rsExtractor = new ResultSetExtractor<List<Order>>() {
     @Override
     public List<Order> extractData(ResultSet rs) throws SQLException, DataAccessException {
       // below logic is specific to this use case. Your logic will be different.
       // The thing to note is that each model gets built by selectMapper.buildModel(rs).
       
       // using maps below to prevent multiple instantiation of same Order, Customer and Product
       // It is a common pattern when using ResultSetExtractor
       // key - id
       Map<Object, Order> idOrderMap = new LinkedHashMap<>(); // LinkedHashMap to retain result order
       Map<Object, Customer> idCustomerMap = new HashMap<>();
       Map<Object, Project> idProductMap = new HashMap<>();
       
       while (rs.next()) {		
         // orderSelectMapper.getResultSetModelIdColumnLabel() returns the id column alias 
         // which is 'o_id' for the sql above. 
         Object orderId = rs.getObject(orderSelectMapper.getResultSetModelIdColumnLabel());
         Order order = idOrderMap.get(orderId);
         if (order == null) {
           order = orderSelectMapper.buildModel(rs); 
           Customer customer = getModel(rs, customerSelectMapper, idCustomerMap); // see definition of getModel() further below         
           order.setCustomer(customer);
           idOrderMap.put(orderId, order);
         }

         OrderLine orderLine = orderLineSelectMapper.buildModel(rs);
         if(orderLine != null) {
           Product product = getModel(rs, productSelectMapper, idProductMap); // see definition of getModel() further below
           orderLine.setProduct(product); 
           order.getOrderLines().add(orderLine);
         }			
      }
      return new ArrayList<Order>(idOrderMap.values());
    }
  };
 
  // execute the JdbcTemplate query	
  List<Order> orders = jdbcTemplateMapper.getJdbcTemplate().query(sql, rsExtractor, "COMPLETE");
  
  ...
  
  // This method is not part of the distribution. Copy it for use in your code.
  public <U> U getModel(ResultSet rs, SelectMapper<U> selectMapper, Map<Object, U> idToModelMap) throws SQLException {
    U model = null;
    Object id = rs.getObject(selectMapper.getResultSetModelIdColumnLabel());
    if (id != null) {
      model = idToModelMap.get(id);
      if (model == null) {
        model = selectMapper.buildModel(rs); // builds the model from resultSet
        idToModelMap.put(id, model);
      }
    }
    return model;
  }
  
...

```

## Accessing JdbcTemplate

```
  jdbcTemplateMapper.getJdbcTemplate(); // gets you the JdbcTemplate of the JdbcTemplateMapper
  jdbcTemplateMapper.getNamedParameterJdbcTemplate(); // gets you the NamedParameterJdbcTemplate of the JdbcTemplateMapper
```

## Logging
 
Uses the same logging configurations as Spring's JdbcTemplate to log the SQL. In application.properties:
 
 ```
 # log the SQL
 logging.level.org.springframework.jdbc.core.JdbcTemplate=TRACE

 # need this to log the INSERT statements
 logging.level.org.springframework.jdbc.core.simple.SimpleJdbcInsert=TRACE

 # log the parameters of SQL statement
 logging.level.org.springframework.jdbc.core.StatementCreatorUtils=TRACE
 
 ```

## Notes
 1. Models should have a no argument constructor so they can be instantiated.
 2. Database changes will require a restart of the application since JdbcTemplateMapper caches table metadata.
 3. When using @Column(name="some_column_name") to map a property to a non default column, it will impact using "SELECT * " with Spring BeanPropertyRowMapper in custom queries. The mismatch of column and property names will cause Spring BeanPropertyRowMapper to ignore these properties. Use "SELECT " + jdbcTemplateMapper.getBeanColumnsSql(Class) which will create column aliases to match property names so will work with BeanPropertyRowMapper.
 4. If insert/update fails do not reuse the object since it could be in an inconsistent state.
 5. For Oracle/SqlServer no support for blob/clob. Use JdbcTemplate directly for this with custom code
 6. Does not support quoted table and column names (needed when using reserved table and column names).
 
## TroubleShooting
Make sure you can connect to your database and issue a simple query using Spring JdbcTemplate without the JdbcTemplateMapper.

## Discussions/bugs
Discussions [here](https://github.com/jdbctemplatemapper/jdbctemplatemapper/discussions)
  
[Issues ticket](https://github.com/jdbctemplatemapper/jdbctemplatemapper/issues). 
 
When opening a ticket please provide the following:
jdbcTemplateMapper version, Database name/version, jdbc driver version, version of java, version of Spring Boot

## Upgrading to 3.x from 2.x
Version 3.x removes all the deprecated methods in 2.x versions.

1. Query.joinColumnOwningSide() removed - Use Query.joinColumnTypeSide()  
   QueryMerge.joinColumnOwningSide() removed - Use QueryMerge.joinColumnTypeSide()  
   QueryCount.joinColumnOwningSide() removed - Use QueryCount.joinColumnTypeSide()   
2. JdbcTemplateMapper.getColumnsSql() removed - Use JdbcTemplateMapper.getBeanColumnsSql()
3. JdbcTemplateMapper.findByProperty() removed - Use Query with where()
4. JdbcTemplateMapper.useColumnLabelForResultSetMetaData() removed - No replacement method. Use jdbc 4.x drivers.

