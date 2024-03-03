package io.github.jdbctemplatemapper.model;

import java.util.ArrayList;
import java.util.List;
import io.github.jdbctemplatemapper.annotation.Column;
import io.github.jdbctemplatemapper.annotation.Id;
import io.github.jdbctemplatemapper.annotation.IdType;
import io.github.jdbctemplatemapper.annotation.QuotedIdentifiers;
import io.github.jdbctemplatemapper.annotation.Table;

@Table(name = "quote")
@QuotedIdentifiers
public class Quote {
  @Id(type = IdType.AUTO_INCREMENT)
  private Integer id;

  @Column(name = "Col 1")
  private String col1;
  
  private List<QuoteDetail> details = new ArrayList<>();

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getCol1() {
    return col1;
  }

  public void setCol1(String col1) {
    this.col1 = col1;
  }

  public List<QuoteDetail> getDetails() {
    return details;
  }

  public void setDetails(List<QuoteDetail> details) {
    this.details = details;
  }
  
  

}
