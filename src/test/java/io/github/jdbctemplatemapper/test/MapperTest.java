package io.github.jdbctemplatemapper.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.github.jdbctemplatemapper.core.JdbcTemplateMapper;
import io.github.jdbctemplatemapper.core.SelectMapper;
import io.github.jdbctemplatemapper.exception.AnnotationException;
import io.github.jdbctemplatemapper.exception.MapperException;
import io.github.jdbctemplatemapper.exception.OptimisticLockingException;
import io.github.jdbctemplatemapper.model.Customer;
import io.github.jdbctemplatemapper.model.NoTableAnnotationModel;
import io.github.jdbctemplatemapper.model.Order;
import io.github.jdbctemplatemapper.model.OrderLine;
import io.github.jdbctemplatemapper.model.Person;
import io.github.jdbctemplatemapper.model.Product;

@SpringBootTest
@ExtendWith(SpringExtension.class)
public class MapperTest {

  @Value("${spring.datasource.driver-class-name}")
  private String jdbcDriver;

  @Autowired
  private JdbcTemplateMapper jtm;

  @Test
  public void insert_longAutoIncrementId_Test() {
    Order order = new Order();
    order.setOrderDate(LocalDateTime.now());
    order.setCustomerId(2);

    jtm.insert(order);

    // check if auto assigned properties have been assigned.
    assertNotNull(order.getCreatedOn());
    assertNotNull(order.getUpdatedOn());
    assertEquals(1, order.getVersion());
    assertEquals("tester", order.getCreatedBy());
    assertEquals("tester", order.getUpdatedBy());

    // requery and test.
    order = jtm.findById(Order.class, order.getOrderId());
    assertNotNull(order.getOrderId());
    assertNotNull(order.getOrderDate());
    assertNotNull(order.getCreatedOn());
    assertNotNull(order.getUpdatedOn());
    assertEquals(1, order.getVersion());
    assertEquals("tester", order.getCreatedBy());
    assertEquals("tester", order.getUpdatedBy());
  }

  @Test
  public void insert_integerAutoIncrementId_withNoVersionAndCreatedInfoTest() {

    Customer customer = new Customer();
    customer.setFirstName("aaa");
    customer.setLastName("bbb");

    jtm.insert(customer);

    Customer customer1 = jtm.findById(Customer.class, customer.getCustomerId());

    assertNotNull(customer1.getCustomerId());
    assertEquals("aaa", customer1.getFirstName());
    assertEquals("bbb", customer1.getLastName());
  }

  @Test
  public void insert_withNonNullIdFailure_Test() {
    Order order = new Order();
    order.setOrderId(2002L);
    order.setOrderDate(LocalDateTime.now());
    order.setCustomerId(2);

    Exception exception = Assertions.assertThrows(MapperException.class, () -> {
      jtm.insert(order);
    });
    assertTrue(exception.getMessage()
        .contains("has to be null since this insert is for an object whose id is auto increment"));
  }

  @Test
  public void insert_WithManualIntegerId_Test() {
    Product product = new Product();
    product.setProductId(1001);
    product.setName("hat");
    product.setCost(12.25);

    jtm.insert(product);

    // check if auto assigned properties are assigned.
    assertNotNull(product.getCreatedOn());
    assertNotNull(product.getUpdatedOn());
    assertEquals(1, product.getVersion());
    assertEquals("tester", product.getCreatedBy());
    assertEquals("tester", product.getUpdatedBy());

    // requery and check
    product = jtm.findById(Product.class, 1001);
    assertNotNull(product.getProductId());
    assertEquals("hat", product.getName());
    assertEquals(12.25, product.getCost());
    assertNotNull(product.getCreatedOn());
    assertNotNull(product.getUpdatedOn());
    assertEquals(1, product.getVersion());
    assertEquals("tester", product.getCreatedBy());
    assertEquals("tester", product.getUpdatedBy());
  }

  @Test
  public void insert_withManualStringId_Test() {

    Person person = new Person();
    person.setPersonId("p1");

    person.setFirstName("xxx");
    person.setLastName("yyy");

    jtm.insert(person);

    Person person1 = jtm.findById(Person.class, person.getPersonId());

    assertNotNull(person1);
  }

  @Test
  public void insert_nullObjectFailure_Test() {
    Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      jtm.insert(null);
    });
    assertTrue(exception.getMessage().contains("Object must not be null"));
  }

  @Test
  public void insert_nonAutoIncrementId_withNullIdFailure_Test() {
    Product product = new Product();
    product.setName("hat");
    product.setCost(12.25);

    Exception exception = Assertions.assertThrows(MapperException.class, () -> {
      jtm.insert(product);
    });

    assertTrue(
        exception.getMessage().contains("cannot be null since it is not an auto increment id"));
  }

  @Test
  public void insert_nonAutoIncrementId_nullObjectFailure_Test() {
    Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      jtm.insert(null);
    });
    assertTrue(exception.getMessage().contains("Object must not be null"));
  }

  @Test
  public void update_Test() throws Exception {
    Order order = jtm.findById(Order.class, 1);
    LocalDateTime prevUpdatedOn = order.getUpdatedOn();

    Thread.sleep(1000); // avoid timing issue.

    order.setStatus("COMPLETE");
    jtm.update(order);

    // check if auto assigned properties have changed.
    assertEquals(2, order.getVersion());
    assertTrue(order.getUpdatedOn().isAfter(prevUpdatedOn));
    assertEquals("tester", order.getUpdatedBy());

    // requery and check
    order = jtm.findById(Order.class, 1);
    assertEquals("COMPLETE", order.getStatus());
    assertEquals(2, order.getVersion()); // version incremented
    assertTrue(order.getUpdatedOn().isAfter(prevUpdatedOn));
    assertEquals("tester", order.getUpdatedBy());

    // reset status for later tests to work. Refactor
    order.setStatus("IN PROCESS");
    jtm.update(order);
  }

  @Test
  public void update_withIdOfTypeInteger_Test() {
    Product product = jtm.findById(Product.class, 6);
    Product product1 = jtm.findById(Product.class, 6);
    product1.setName("xyz");
    jtm.update(product1);

    Product product2 = jtm.findById(Product.class, 6);

    assertEquals("xyz", product1.getName());
    assertTrue(product2.getVersion() > product.getVersion()); // version incremented
  }

  @Test
  public void update_withIdOfTypeString_Test() {
    Person person = jtm.findById(Person.class, "person101");
    person.setLastName("new name");
    jtm.update(person);

    Person person1 = jtm.findById(Person.class, "person101");

    assertEquals("new name", person1.getLastName());
  }

  @Test
  public void update_withNoVersionAndUpdateInfo_Test() {
    Customer customer = jtm.findById(Customer.class, 4);
    customer.setFirstName("xyz");
    jtm.update(customer);

    Customer customer1 = jtm.findById(Customer.class, 4); // requery
    assertEquals("xyz", customer1.getFirstName());
  }

  @Test
  public void update_throwsOptimisticLockingException_Test() {
    Assertions.assertThrows(OptimisticLockingException.class, () -> {
      Order order = jtm.findById(Order.class, 2);
      order.setVersion(order.getVersion() - 1);
      jtm.update(order);
    });
  }

  @Test
  public void update_nullObjectFailure_Test() {
    Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      jtm.update(null);
    });
    assertTrue(exception.getMessage().contains("Object must not be null"));
  }

  @Test
  public void update_nonDatabaseProperty_Test() {
    Person person = jtm.findById(Person.class, "person101");
    person.setSomeNonDatabaseProperty("xyz");
    jtm.update(person);

    // requery
    Person person2 = jtm.findById(Person.class, "person101");

    assertNotNull(person2);
    assertNull(person2.getSomeNonDatabaseProperty());
  }

  @Test
  public void findById_Test() {
    Order order = jtm.findById(Order.class, 1);

    assertNotNull(order.getOrderId());
    assertNotNull(order.getOrderDate());
    assertNotNull(order.getCreatedBy());
    assertNotNull(order.getCreatedOn());
    assertNotNull(order.getUpdatedBy());
    assertNotNull(order.getUpdatedOn());
    assertNotNull(order.getVersion());
  }

  @Test
  public void findAll_Test() {
    List<Order> orders = jtm.findAll(Order.class);
    assertTrue(orders.size() >= 2);

    for (int idx = 0; idx < orders.size(); idx++) {
      assertNotNull(orders.get(idx).getOrderId());
      assertNotNull(orders.get(idx).getOrderDate());
      assertNotNull(orders.get(idx).getCreatedBy());
      assertNotNull(orders.get(idx).getCreatedOn());
      assertNotNull(orders.get(idx).getUpdatedBy());
      assertNotNull(orders.get(idx).getUpdatedOn());
      assertNotNull(orders.get(idx).getVersion());
    }
  }

  @Test
  public void deleteByObject_Test() {
    Product product = jtm.findById(Product.class, 4);
    int cnt = jtm.delete(product);
    assertTrue(cnt == 1);

    Product product1 = jtm.findById(Product.class, 4);
    assertNull(product1);
  }

  @Test
  public void delete_nullObjectFailure_Test() {
    Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      jtm.delete(null);
    });
    assertTrue(exception.getMessage().contains("Object must not be null"));
  }

  @Test
  public void deleteById_Test() {
    int cnt = jtm.deleteById(Product.class, 5);
    assertTrue(cnt == 1);

    Product product1 = jtm.findById(Product.class, 5);
    assertNull(product1);
  }

  @Test
  public void deleteById_nullIdFailure_Test() {
    Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      jtm.deleteById(Product.class, null);
    });
    assertTrue(exception.getMessage().contains("id must not be null"));
  }

  @Test
  public void findByProperty_Test() {
    List<OrderLine> orderLines = jtm.findByProperty(OrderLine.class, "orderId", 1);
    assertTrue(orderLines.size() == 2);
  }

  @Test
  public void findByProperty_nullPropertyName_Test() {
    Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      jtm.findByProperty(OrderLine.class, null, 1);
    });
    assertTrue(exception.getMessage().contains("propertyName must not be null"));
  }

  @Test
  public void findByProperty_nullClazz_Test() {
    Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      jtm.findByProperty(null, "orderId", 1);
    });
    assertTrue(exception.getMessage().contains("Class must not be null"));
  }

  @Test
  public void findByProperty_nullPropertyValue_Test() {
    Customer customer = new Customer();
    customer.setLastName("doe");
    jtm.insert(customer);
    
    List<Customer> list = jtm.findByProperty(Customer.class, "firstName", null);
    assertTrue(list.size() > 0);
    
    jtm.delete(customer);
  }

  @Test
  public void findByProperty_InvalidProperty_Test() {
    Exception exception = Assertions.assertThrows(MapperException.class, () -> {
      jtm.findByProperty(OrderLine.class, "x", 1);
    });
    assertTrue(exception.getMessage()
        .contains("is either invalid or does not have a corresponding column in database"));
  }

  @Test
  public void findByProperty_OrderBy_Test() {
    List<OrderLine> orderLines = jtm.findByProperty(OrderLine.class, "orderId", 1, "orderLineId");
    assertTrue(orderLines.size() == 2);
    assertTrue(orderLines.get(0).getProductId() == 1);
    assertTrue(orderLines.get(0).getNumOfUnits() == 10);
  }

  @Test
  public void findByProperty_InvalidOrderByProperty_Test() {
    Exception exception = Assertions.assertThrows(MapperException.class, () -> {
      jtm.findByProperty(OrderLine.class, "orderId", 1, "x");
    });
    assertTrue(exception.getMessage()
        .contains("is either invalid or does not have a corresponding column in database"));
  }

  @Test
  public void loadMapping_success_Test() {
    Assertions.assertDoesNotThrow(() -> {
      jtm.loadMapping(Order.class);
    });
  }

  @Test
  public void loadMapping_failure_Test() {
    Assertions.assertThrows(AnnotationException.class, () -> {
      jtm.loadMapping(NoTableAnnotationModel.class);
    });
  }

  @Test
  public void getColumnName_Success_Test() {
    String columnName = jtm.getColumnName(Order.class, "status");
    assertEquals("status", columnName);
  }

  @Test
  public void getColumnName_invalid_Test() {
    String columnName = jtm.getColumnName(Order.class, "x");
    assertNull(columnName);
  }

  @Test
  public void selectMapper_Success_Test() {
    SelectMapper<Order> orderSelectMapper = jtm.getSelectMapper(Order.class, "o");
    SelectMapper<Customer> customerSelectMapper = jtm.getSelectMapper(Customer.class, "c");
    SelectMapper<OrderLine> orderLineSelectMapper = jtm.getSelectMapper(OrderLine.class, "ol");
    SelectMapper<Product> productSelectMapper = jtm.getSelectMapper(Product.class, "p");

    assertTrue(Order.class == orderSelectMapper.getType());
    assertTrue(orderSelectMapper.getTableAlias().equals("o"));

    // @formatter:off
    String sql =
        "select "
            + orderSelectMapper.getColumnsSql()
            + ","
            + customerSelectMapper.getColumnsSql()
            + ","
            + orderLineSelectMapper.getColumnsSql()
            + ","
            + productSelectMapper.getColumnsSql()
            + " from schema1.orders o"
            + " left join " + fullyQualifiedTableName("customer") + " c on o.customer_id = c.customer_id"
            + " left join " + fullyQualifiedTableName("order_line") + " ol on o.order_id = ol.order_id"
            + " left join " + fullyQualifiedTableName("product") + " p on p.product_id = ol.product_id"
            + " where o.status = ?"
            + " order by o.order_id, ol.order_line_id";
    // @formatter:on

    ResultSetExtractor<List<Order>> rsExtractor = new ResultSetExtractor<List<Order>>() {
      @Override
      public List<Order> extractData(ResultSet rs) throws SQLException, DataAccessException {

        Map<Object, Order> orderByIdMap = new LinkedHashMap<>(); // LinkedHashMap to retain result
                                                                 // order
        Map<Object, Customer> customerByIdMap = new HashMap<>();
        Map<Object, Product> productByIdMap = new HashMap<>();

        while (rs.next()) {
          Object orderId = rs.getObject(orderSelectMapper.getResultSetModelIdColumnLabel());
          Order order = orderByIdMap.get(orderId);
          if (order == null) {
            order = orderSelectMapper.buildModel(rs);

            Customer customer = getModel(rs, customerSelectMapper, customerByIdMap);

            order.setCustomer(customer);
            orderByIdMap.put(orderId, order);
          }

          OrderLine orderLine = orderLineSelectMapper.buildModel(rs);
          if (orderLine != null) {
            Product product = getModel(rs, productSelectMapper, productByIdMap);

            orderLine.setProduct(product);
            order.getOrderLines().add(orderLine);
          }

        }
        return new ArrayList<Order>(orderByIdMap.values());
      }
    };

    List<Order> orders = jtm.getJdbcTemplate().query(sql, rsExtractor, "IN PROCESS");

    assertTrue(orders.size() == 2);
    assertTrue(orders.get(0).getOrderLines().size() == 2);
    assertEquals("tony", orders.get(0).getCustomer().getFirstName());
    assertEquals("IN PROCESS", orders.get(1).getStatus());
    assertTrue(orders.get(1).getOrderLines().size() == 1);
    assertTrue(orders.get(0).getOrderLines().get(0).getProductId() == 1);
    assertEquals("shoes", orders.get(0).getOrderLines().get(0).getProduct().getName());
    assertEquals("socks", orders.get(0).getOrderLines().get(1).getProduct().getName());
    assertEquals("laces", orders.get(1).getOrderLines().get(0).getProduct().getName());
  }

  // This method is not part of the distribution. You will need to copy it for use
  // in your code.
  private <U> U getModel(ResultSet rs, SelectMapper<U> selectMapper, Map<Object, U> idToModelMap)
      throws SQLException {
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

  private String fullyQualifiedTableName(String tableName) {
    return jtm.getSchemaName() == null ? tableName : jtm.getSchemaName() + "." + tableName;
  }

}
