package io.github.jdbctemplatemapper.exception;

public class QueryException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public QueryException(String message) {
    super(message);
  }
}
